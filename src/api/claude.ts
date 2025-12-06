/**
 * Claude API Integration
 *
 * This module handles communication with the Anthropic Claude API
 * for recipe generation, image intake processing, and shopping assistance.
 */

import type { Ingredient, Recipe, UserPreferences } from '../db/types';
import { getPreferenceSummary } from '../db';
import { isNative } from '../native';

// Use proxy in web development to avoid CORS, call API directly in native app
const API_BASE = isNative ? 'https://api.anthropic.com' : '/api/anthropic';

// API configuration - key will be set by user
let apiKey: string | null = null;

export function setApiKey(key: string) {
  apiKey = key;
  localStorage.setItem('claude_api_key', key);
}

export function getApiKey(): string | null {
  if (!apiKey) {
    apiKey = localStorage.getItem('claude_api_key');
  }
  return apiKey;
}

export function hasApiKey(): boolean {
  return !!getApiKey();
}

// Types for API responses
export interface CookingStep {
  title: string; // e.g., "Prepare the Rice", "Set up Mise en Place"
  substeps: string[];
}

// Lightweight recipe outline (Phase 1 - just names and metadata)
export interface RecipeOutline {
  name: string;
  description: string;
  servings: number;
  prepTimeMinutes: number;
  cookTimeMinutes: number;
  tags: string[];
  mainProtein: string; // chicken, beef, pork, fish, tofu, none
  mainStarch: string; // rice, pasta, potato, bread, tortilla, none
  mealFormat: string; // bowl, sandwich, taco, soup, salad, stir-fry, etc.
}

export interface RecipeOutlinesResponse {
  recipes: RecipeOutline[];
  defaultSelections: number[]; // indices of the 6 pre-selected recipes
}

export interface GeneratedRecipe {
  name: string;
  description: string;
  servings: number;
  prepTimeMinutes: number;
  cookTimeMinutes: number;
  ingredients: {
    ingredientName: string;
    quantity: number;
    unit: string;
    preparation: string | null;
  }[];
  steps: CookingStep[]; // Structured cooking steps (4-6 main steps with substeps)
  tags: string[];
}

export interface RecipePoolResponse {
  recipes: GeneratedRecipe[];
  defaultSelections: number[]; // indices of the 6 pre-selected recipes
}

// Progress callback for phased generation
export interface GenerationProgress {
  phase: 'outlines' | 'details' | 'normalizing' | 'images';
  current: number;
  total: number;
  recipeName?: string;
}

export interface ShoppingListResponse {
  items: {
    ingredientName: string;
    quantity: number;
    unit: string;
    category: string;
    notes: string | null;
  }[];
}

export interface ImageIntakeResponse {
  productName: string;
  brand: string | null;
  quantity: number;
  unit: string;
  expiryDate: string | null;
  category: string;
  confidence: number;
}

// API call functions

export async function generateRecipePool(
  pantryState: Ingredient[],
  recentRecipes: string[], // recipe hashes to avoid
  preferences: UserPreferences | null,
  currentDate: Date = new Date()
): Promise<RecipePoolResponse> {
  const key = getApiKey();
  if (!key) {
    throw new Error('API key not configured');
  }

  const month = currentDate.toLocaleDateString('en-CA', { month: 'long' });
  const season = getSeason(currentDate);

  // Get learned preferences from recipe history
  const learnedPrefs = await getPreferenceSummary();

  // Build preference hints for the prompt
  let prefHints = '';
  if (learnedPrefs.likedTags.length > 0) {
    prefHints += `\n- USER FAVORITES: Include more recipes with these styles/cuisines: ${learnedPrefs.likedTags.join(', ')}`;
  }
  if (learnedPrefs.dislikedTags.length > 0) {
    prefHints += `\n- USER DISLIKES: Avoid or limit recipes with: ${learnedPrefs.dislikedTags.join(', ')}`;
  }
  if (learnedPrefs.likedIngredients.length > 0) {
    prefHints += `\n- LIKED INGREDIENTS: ${learnedPrefs.likedIngredients.slice(0, 8).join(', ')}`;
  }
  if (learnedPrefs.avoidIngredients.length > 0) {
    prefHints += `\n- AVOID INGREDIENTS: ${learnedPrefs.avoidIngredients.slice(0, 5).join(', ')}`;
  }

  const systemPrompt = `You are a meal planning assistant. Generate recipes that:
- Use a MIX of pantry ingredients and fresh ingredients that need to be purchased
- About 60-70% of recipes should use mostly pantry items, 30-40% should require some fresh shopping (proteins, produce, dairy)
- Consider seasonal availability (current: ${month}, ${season} in Alberta, Canada)
- Respect user preferences and dietary constraints
- Avoid recently made recipes
- Balance variety across cuisines and cooking methods
- IMPORTANT: Include diverse meal FORMATS - not just bowls and stir-fries! Include:
  * Sandwiches (banh mi, clubs, melts, subs)
  * Burgers (beef, chicken, veggie)
  * Tacos, burritos, quesadillas
  * Wraps (lettuce wraps, tortilla wraps)
  * Salads with protein
  * Pasta dishes
  * Sheet pan meals
  * Soups and stews
- Embrace fusion cuisine (Korean-Mexican, Thai-Italian, etc.)${prefHints}

Respond with valid JSON only.`;

  // Compact pantry state to reduce token usage
  const compactPantry = pantryState.map(i => `${i.name}:${i.quantityRemaining}${i.unit}`).join(', ');

  const userPrompt = `Generate 12 recipes for meal planning.

PANTRY: ${compactPantry}

AVOID HASHES: ${recentRecipes.slice(0, 5).join(',')}

PREFS: ${preferences ? `servings=${preferences.servings}, spice=${preferences.spiceTolerance}` : 'servings=2'}

REQUIREMENTS:
- 8 quick (≤30 min), 4 longer (45-60 min)
- Pre-select 6 defaults in defaultSelections array
- Mix of pantry-only and recipes needing fresh items (proteins, veggies)
- Each recipe: 3-4 steps, each step has 2-3 short substeps

Return compact JSON (no extra whitespace):
{"recipes":[{"name":"...","description":"short desc","servings":2,"prepTimeMinutes":15,"cookTimeMinutes":20,"ingredients":[{"ingredientName":"...","quantity":1,"unit":"cup","preparation":"diced"}],"steps":[{"title":"Step","substeps":["do x","do y"]}],"tags":["quick"]}],"defaultSelections":[0,1,2,3,4,5]}`;

  const response = await callClaudeAPI(key, systemPrompt, userPrompt);
  console.log('Raw API response length:', response.length);
  const extracted = extractJSON(response);
  console.log('Extracted JSON length:', extracted.length);
  console.log('Extracted JSON preview:', extracted.slice(0, 500));
  console.log('Extracted JSON end:', extracted.slice(-200));
  try {
    return JSON.parse(extracted) as RecipePoolResponse;
  } catch (e) {
    console.error('JSON parse error:', e);
    console.error('Full extracted text:', extracted);
    throw e;
  }
}

// ============================================
// PHASED RECIPE GENERATION (New Architecture)
// ============================================

// Phase 1: Generate 24 balanced recipe outlines using extended thinking
export async function generateRecipeOutlines(
  pantryState: Ingredient[],
  recentRecipes: string[],
  preferences: UserPreferences | null,
  currentDate: Date = new Date()
): Promise<RecipeOutlinesResponse> {
  const key = getApiKey();
  if (!key) {
    throw new Error('API key not configured');
  }

  const month = currentDate.toLocaleDateString('en-CA', { month: 'long' });
  const season = getSeason(currentDate);

  // Get learned preferences from recipe history
  const learnedPrefs = await getPreferenceSummary();

  let prefHints = '';
  if (learnedPrefs.likedTags.length > 0) {
    prefHints += `\nUser favorites: ${learnedPrefs.likedTags.join(', ')}`;
  }
  if (learnedPrefs.dislikedTags.length > 0) {
    prefHints += `\nUser dislikes: ${learnedPrefs.dislikedTags.join(', ')}`;
  }

  const compactPantry = pantryState.map(i => `${i.name}:${i.quantityRemaining}${i.unit}`).join(', ');

  const systemPrompt = `You are a meal planning assistant creating a balanced weekly menu. Your task is to plan 24 diverse recipe ideas.

Think carefully about balance across these dimensions:
- PROTEIN: Mix of chicken (6-7), beef (4-5), pork (3-4), fish/seafood (2-3), vegetarian (4-5)
- STARCH: Mix of rice (6-7), pasta (4-5), bread/sandwich (4-5), potato (2-3), tortilla (3-4), other/none (3-4)
- FORMAT: Bowls (4-5), sandwiches/subs (3-4), tacos/burritos (3-4), stir-fries (3-4), pasta dishes (3-4), soups/stews (2-3), salads (2-3), sheet pan (2-3)
- CUISINE: Mix of Asian, Italian, Mexican, American, Mediterranean, fusion
- TIME: 16-18 quick (≤30 min), 6-8 longer (45-60 min)
- COMPLEXITY: Mix of weeknight-easy and weekend-worthy

Season: ${month}, ${season} in Alberta, Canada${prefHints}

Respond with valid JSON only.`;

  const userPrompt = `Create 24 diverse recipe outlines for meal planning.

PANTRY AVAILABLE: ${compactPantry}

RECENT RECIPES TO AVOID: ${recentRecipes.slice(0, 10).join(', ')}

SERVINGS: ${preferences?.servings ?? 2}

Return JSON with recipe outlines (NO ingredients or steps yet - just the plan):
{"recipes":[{"name":"Recipe Name","description":"Brief appetizing description","servings":2,"prepTimeMinutes":15,"cookTimeMinutes":20,"tags":["quick","asian"],"mainProtein":"chicken","mainStarch":"rice","mealFormat":"stir-fry"}],"defaultSelections":[0,1,2,3,4,5]}

Think through the balance carefully before outputting. Aim for variety - no two recipes should feel too similar.`;

  const response = await callClaudeAPIWithThinking(key, systemPrompt, userPrompt);
  const extracted = extractJSON(response);

  try {
    return JSON.parse(extracted) as RecipeOutlinesResponse;
  } catch (e) {
    console.error('JSON parse error in outlines:', e);
    throw e;
  }
}

// Phase 2: Generate full details for a single recipe
export async function generateRecipeDetails(
  outline: RecipeOutline,
  pantryState: Ingredient[]
): Promise<GeneratedRecipe> {
  const key = getApiKey();
  if (!key) {
    throw new Error('API key not configured');
  }

  const compactPantry = pantryState.map(i => i.name).join(', ');

  const systemPrompt = `You are a recipe writer. Given a recipe outline, provide the complete ingredient list and cooking steps. Be practical and precise with quantities. Respond with valid JSON only.`;

  const userPrompt = `Complete this recipe with ingredients and steps:

RECIPE: ${outline.name}
DESCRIPTION: ${outline.description}
SERVINGS: ${outline.servings}
PREP TIME: ${outline.prepTimeMinutes} min
COOK TIME: ${outline.cookTimeMinutes} min
TAGS: ${outline.tags.join(', ')}
MAIN PROTEIN: ${outline.mainProtein}
MAIN STARCH: ${outline.mainStarch}
FORMAT: ${outline.mealFormat}

PANTRY AVAILABLE: ${compactPantry}

Return compact JSON:
{"ingredients":[{"ingredientName":"...","quantity":1,"unit":"cup","preparation":"diced"}],"steps":[{"title":"Step Title","substeps":["substep 1","substep 2"]}]}

Rules:
- 6-12 ingredients, practical quantities
- 3-4 main steps, each with 2-3 substeps
- Use pantry items where sensible, add fresh items as needed`;

  const response = await callClaudeAPI(key, systemPrompt, userPrompt);
  const extracted = extractJSON(response);

  try {
    const details = JSON.parse(extracted) as { ingredients: GeneratedRecipe['ingredients']; steps: CookingStep[] };

    // Merge outline with details
    return {
      name: outline.name,
      description: outline.description,
      servings: outline.servings,
      prepTimeMinutes: outline.prepTimeMinutes,
      cookTimeMinutes: outline.cookTimeMinutes,
      tags: outline.tags,
      ingredients: details.ingredients,
      steps: details.steps,
    };
  } catch (e) {
    console.error(`JSON parse error for ${outline.name}:`, e);
    throw e;
  }
}

// Phase 3: Normalize ingredients across all recipes for shopping list consistency
export async function normalizeRecipeIngredients(
  recipes: GeneratedRecipe[]
): Promise<GeneratedRecipe[]> {
  const key = getApiKey();
  if (!key) {
    throw new Error('API key not configured');
  }

  // Extract all ingredients grouped by recipe for context
  const ingredientsByRecipe = recipes.map(r => ({
    recipeName: r.name,
    ingredients: r.ingredients.map(i => ({
      ingredientName: i.ingredientName,
      quantity: i.quantity,
      unit: i.unit,
    })),
  }));

  const systemPrompt = `You are a shopping list optimizer. Your job is to normalize ingredient names across multiple recipes so they can be properly aggregated for a shopping list.

NORMALIZATION RULES:

1. KEEP IMPORTANT DETAILS - DO NOT SIMPLIFY:
   - Keep cut details: "boneless skinless chicken breast" stays as-is (different from bone-in)
   - Keep preparation differences: "chicken thighs" vs "chicken breast" are different
   - Keep size/type when relevant: "jumbo shrimp" vs "small shrimp"

2. ADD SPECIFICITY where missing:
   - Bell peppers: specify color - "red bell pepper", "green bell pepper", "yellow bell pepper"
   - Lettuce: specify type - "romaine lettuce", "iceberg lettuce", "butter lettuce"
   - Onions: specify if it matters - "yellow onion", "red onion", "white onion"
   - Fresh vs dried: "fresh ginger" vs "ground ginger", "fresh thyme" vs "dried thyme"

3. EXPAND GENERIC ITEMS:
   - "fresh herbs" → list the specific herbs: "fresh cilantro", "fresh basil", etc.
   - "mixed greens" → specify what greens or keep as "mixed salad greens"

4. PREPARED INGREDIENTS → RAW:
   - "coleslaw mix" → separate "green cabbage" and "shredded carrots"
   - "stir-fry vegetables" → list individual vegetables
   - Keep reasonable prepared items: tomato paste, coconut milk, canned beans, etc.

5. UNIT NORMALIZATION:
   - Use standard units: tbsp, tsp, cup, g, ml, pcs
   - Proteins by weight (g or lb) or piece count, not vague amounts
   - "tablespoon" → "tbsp", "teaspoon" → "tsp"

Return the SAME structure but with normalized ingredient names and any prepared items broken down.
Respond with valid JSON only.`;

  const userPrompt = `Normalize these recipe ingredients for shopping list aggregation:

${JSON.stringify(ingredientsByRecipe, null, 2)}

Return JSON with the same structure, but normalized:
{"recipes":[{"recipeName":"...","ingredients":[{"ingredientName":"normalized name","quantity":1,"unit":"cup"}]}]}

Important:
- Keep same recipe order
- Keep same ingredient order within each recipe
- Only change ingredientName, quantity, unit as needed
- If breaking down a prepared item, add multiple ingredients in its place`;

  try {
    const response = await callClaudeAPI(key, systemPrompt, userPrompt);
    const extracted = extractJSON(response);
    const normalized = JSON.parse(extracted) as {
      recipes: Array<{
        recipeName: string;
        ingredients: Array<{
          ingredientName: string;
          quantity: number;
          unit: string;
        }>;
      }>;
    };

    // Apply normalized ingredients back to recipes
    const normalizedRecipes = recipes.map((recipe, idx) => {
      const normalizedData = normalized.recipes[idx];
      if (!normalizedData || normalizedData.recipeName !== recipe.name) {
        // Fallback if something went wrong
        console.warn(`Normalization mismatch for ${recipe.name}, keeping original`);
        return recipe;
      }

      return {
        ...recipe,
        ingredients: normalizedData.ingredients.map((ni, ingIdx) => ({
          ingredientName: ni.ingredientName,
          quantity: ni.quantity,
          unit: ni.unit,
          preparation: recipe.ingredients[ingIdx]?.preparation ?? null,
        })),
      };
    });

    return normalizedRecipes;
  } catch (err) {
    console.error('Ingredient normalization failed, using original:', err);
    return recipes; // Return original if normalization fails
  }
}

// Orchestrate multi-phase generation with progress callbacks
export async function generateRecipePoolPhased(
  pantryState: Ingredient[],
  recentRecipes: string[],
  preferences: UserPreferences | null,
  onProgress?: (progress: GenerationProgress) => void,
  currentDate: Date = new Date()
): Promise<RecipePoolResponse> {
  // Phase 1: Generate all outlines
  onProgress?.({ phase: 'outlines', current: 0, total: 1 });

  const outlines = await generateRecipeOutlines(pantryState, recentRecipes, preferences, currentDate);

  onProgress?.({ phase: 'outlines', current: 1, total: 1 });

  // Phase 2: Generate details in parallel batches
  const recipes: GeneratedRecipe[] = [];
  const batchSize = 4; // Process 4 recipes at a time
  const totalRecipes = outlines.recipes.length;

  for (let i = 0; i < totalRecipes; i += batchSize) {
    const batch = outlines.recipes.slice(i, i + batchSize);

    onProgress?.({
      phase: 'details',
      current: i,
      total: totalRecipes,
      recipeName: batch[0]?.name,
    });

    // Process batch in parallel
    const batchResults = await Promise.all(
      batch.map(outline => generateRecipeDetails(outline, pantryState).catch(err => {
        console.error(`Failed to generate details for ${outline.name}:`, err);
        return null;
      }))
    );

    // Add successful results
    for (const result of batchResults) {
      if (result) {
        recipes.push(result);
      }
    }
  }

  onProgress?.({
    phase: 'details',
    current: totalRecipes,
    total: totalRecipes,
  });

  // Phase 3: Normalize ingredients across all recipes
  // This ensures consistent naming for shopping list aggregation
  onProgress?.({
    phase: 'normalizing',
    current: 0,
    total: 1,
  });

  const normalizedRecipes = await normalizeRecipeIngredients(recipes);

  onProgress?.({
    phase: 'normalizing',
    current: 1,
    total: 1,
  });

  return {
    recipes: normalizedRecipes,
    defaultSelections: outlines.defaultSelections.filter(i => i < normalizedRecipes.length),
  };
}

export async function generateShoppingList(
  selectedRecipes: Recipe[],
  pantryState: Ingredient[]
): Promise<ShoppingListResponse> {
  const key = getApiKey();
  if (!key) {
    throw new Error('API key not configured');
  }

  const systemPrompt = `You are a shopping list generator. Create an aggregated shopping list that:
- Combines quantities for duplicate ingredients across recipes
- Subtracts what's already in the pantry
- Organizes by store section
- Uses sensible retail quantities (round up to typical package sizes)

Respond with valid JSON only.`;

  const userPrompt = `Generate a shopping list for these recipes:

RECIPES:
${JSON.stringify(selectedRecipes.map(r => ({
  name: r.name,
  ingredients: r.ingredients,
})), null, 2)}

CURRENT PANTRY:
${JSON.stringify(pantryState.map(i => ({
  name: i.name,
  quantity: i.quantityRemaining,
  unit: i.unit,
})), null, 2)}

Return JSON with items needed (excluding what's in pantry), organized by category:
{
  "items": [
    { "ingredientName": "...", "quantity": ..., "unit": "...", "category": "produce|dairy|protein|dry goods|condiment|spice|frozen|other", "notes": "..." }
  ]
}`;

  const response = await callClaudeAPI(key, systemPrompt, userPrompt);
  return JSON.parse(extractJSON(response)) as ShoppingListResponse;
}

export async function processImageIntake(
  imageBase64: string
): Promise<ImageIntakeResponse> {
  const key = getApiKey();
  if (!key) {
    throw new Error('API key not configured');
  }

  // Note: This requires the vision-capable model
  const response = await fetch(`${API_BASE}/v1/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-api-key': key,
      'anthropic-version': '2023-06-01',
      'anthropic-dangerous-direct-browser-access': 'true',
    },
    body: JSON.stringify({
      model: 'claude-sonnet-4-5', // Alias for latest Sonnet 4.5 snapshot
      max_tokens: 1024,
      messages: [
        {
          role: 'user',
          content: [
            {
              type: 'image',
              source: {
                type: 'base64',
                media_type: 'image/jpeg',
                data: imageBase64,
              },
            },
            {
              type: 'text',
              text: `Extract product details from this grocery item image. Return JSON:
{
  "productName": "normalized ingredient name",
  "brand": "brand name or null",
  "quantity": numeric_value,
  "unit": "g|ml|units|bunch",
  "expiryDate": "YYYY-MM-DD or null",
  "category": "produce|dairy|protein|dry goods|condiment|spice|frozen|other",
  "confidence": 0.0-1.0
}`,
            },
          ],
        },
      ],
    }),
  });

  if (!response.ok) {
    throw new Error(`API error: ${response.status}`);
  }

  const data = await response.json();
  const content = data.content[0].text;
  return JSON.parse(extractJSON(content)) as ImageIntakeResponse;
}

// Helper function for text-only API calls
async function callClaudeAPI(
  key: string,
  systemPrompt: string,
  userPrompt: string
): Promise<string> {
  const response = await fetch(`${API_BASE}/v1/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-api-key': key,
      'anthropic-version': '2023-06-01',
      'anthropic-dangerous-direct-browser-access': 'true',
    },
    body: JSON.stringify({
      model: 'claude-sonnet-4-5', // Alias for latest Sonnet 4.5 snapshot
      max_tokens: 16384,
      system: systemPrompt,
      messages: [
        {
          role: 'user',
          content: userPrompt,
        },
      ],
    }),
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`API error: ${response.status} - ${error}`);
  }

  const data = await response.json();
  return data.content[0].text;
}

// Helper function with extended thinking enabled for complex reasoning
async function callClaudeAPIWithThinking(
  key: string,
  systemPrompt: string,
  userPrompt: string
): Promise<string> {
  const response = await fetch(`${API_BASE}/v1/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-api-key': key,
      'anthropic-version': '2023-06-01',
      'anthropic-dangerous-direct-browser-access': 'true',
    },
    body: JSON.stringify({
      model: 'claude-sonnet-4-5', // Sonnet 4.5 supports extended thinking
      max_tokens: 16000,
      thinking: {
        type: 'enabled',
        budget_tokens: 8000, // Allow up to 8k tokens for thinking through balance
      },
      system: systemPrompt,
      messages: [
        {
          role: 'user',
          content: userPrompt,
        },
      ],
    }),
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`API error: ${response.status} - ${error}`);
  }

  const data = await response.json();

  // Extended thinking returns multiple content blocks - find the text one
  for (const block of data.content) {
    if (block.type === 'text') {
      return block.text;
    }
  }

  throw new Error('No text response found in extended thinking output');
}

function getSeason(date: Date): string {
  const month = date.getMonth();
  if (month >= 2 && month <= 4) return 'spring';
  if (month >= 5 && month <= 7) return 'summer';
  if (month >= 8 && month <= 10) return 'fall';
  return 'winter';
}

// Extract JSON from markdown code blocks if present
function extractJSON(text: string): string {
  const trimmed = text.trim();

  // Try to find JSON object or array directly
  const jsonStart = trimmed.indexOf('{');
  const arrayStart = trimmed.indexOf('[');

  // If text starts with a code block, strip it
  if (trimmed.startsWith('```')) {
    // Find the end of the opening line (```json or ```)
    const firstNewline = trimmed.indexOf('\n');
    const closingBackticks = trimmed.lastIndexOf('```');

    if (firstNewline !== -1 && closingBackticks > firstNewline) {
      return trimmed.slice(firstNewline + 1, closingBackticks).trim();
    }
  }

  // If we can find a JSON object/array, extract from there
  if (jsonStart !== -1 || arrayStart !== -1) {
    const start = jsonStart === -1 ? arrayStart :
                  arrayStart === -1 ? jsonStart :
                  Math.min(jsonStart, arrayStart);
    return trimmed.slice(start);
  }

  return trimmed;
}
