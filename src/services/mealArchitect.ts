/**
 * Meal Architect Service
 *
 * Uses Gemini with function calling to compose complete meals from the recipe database.
 * Two-stage process:
 * 1. Planning: Gemini decides on 24 balanced meal outlines using tools to search DB
 * 2. Construction: Batched calls to build full recipe cards with unified steps
 */

import { GoogleGenAI, Type, type FunctionDeclaration } from '@google/genai';
import { db, getRecentRecipes, getStoredPreferenceSummary } from '../db';
import type { Ingredient } from '../db/types';
import { getGeminiKey } from '../api/gemini';
import type { GeneratedRecipe, GenerationProgress } from '../api/claude';
import {
  getTargetServings,
  getCurrentSeason,
  SEASONAL_INGREDIENTS,
  getUnitSystem
} from '../utils/settings';
import { ensureRecipeDataLoaded } from './recipeData';

// ============================================================================
// Types
// ============================================================================

// Meal outline from Stage 1 planning
export interface MealOutline {
  name: string;
  description: string;
  components: {
    main: string;        // e.g., "Thai Basil Chicken"
    carb?: string;       // e.g., "Coconut Rice"
    veggie?: string;     // e.g., "Thai Cucumber Salad"
  };
  sourceRecipeIds: number[];  // DB recipe IDs used as inspiration
  estimatedTime: number;
  tags: string[];
  usesExpiringIngredients: boolean;
  expiringIngredientsUsed: string[];
}

// Full composed meal from Stage 2
export interface ComposedMeal extends GeneratedRecipe {
  usesExpiringIngredients: boolean;
  expiringIngredientsUsed: string[];
  sourceRecipeIds: number[];
}

// ============================================================================
// Tool Definitions for Gemini
// ============================================================================

const FUNCTION_DECLARATIONS: FunctionDeclaration[] = [
  {
    name: 'search_recipes',
    description: 'Search the recipe database by various criteria. Returns up to 20 matching recipes with their IDs, names, ingredients, and metadata.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        query: {
          type: Type.STRING,
          description: 'Text search query for recipe names or descriptions'
        },
        ingredients: {
          type: Type.ARRAY,
          items: { type: Type.STRING },
          description: 'Ingredients that should be in the recipe'
        },
        cuisine: {
          type: Type.STRING,
          description: 'Cuisine type (e.g., italian, mexican, thai, chinese)'
        },
        maxTime: {
          type: Type.NUMBER,
          description: 'Maximum total cooking time in minutes'
        },
        tags: {
          type: Type.ARRAY,
          items: { type: Type.STRING },
          description: 'Tags to filter by (e.g., easy, quick, vegetarian)'
        },
        protein: {
          type: Type.STRING,
          description: 'Main protein type (chicken, beef, pork, seafood, vegetarian)'
        }
      }
    }
  },
  {
    name: 'get_expiring_ingredients',
    description: 'Get pantry ingredients that need to be used up soon. Returns items with explicit expiry dates within 7 days, plus any produce/protein/dairy items that have been in the pantry for 3+ days. These should be prioritized in meal planning.',
    parameters: {
      type: Type.OBJECT,
      properties: {}
    }
  },
  {
    name: 'get_recent_meals',
    description: 'Get recently cooked meals to avoid repetition.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        weeks: {
          type: Type.NUMBER,
          description: 'Number of weeks to look back (default 3)'
        }
      }
    }
  },
  {
    name: 'get_recipe_details',
    description: 'Get full details for specific recipes by their IDs.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        recipeIds: {
          type: Type.ARRAY,
          items: { type: Type.NUMBER },
          description: 'Array of recipe source IDs to fetch'
        }
      },
      required: ['recipeIds']
    }
  }
];

// ============================================================================
// Tool Handlers
// ============================================================================

async function handleSearchRecipes(input: {
  query?: string;
  ingredients?: string[];
  cuisine?: string;
  maxTime?: number;
  tags?: string[];
  protein?: string;
}): Promise<string> {
  await ensureRecipeDataLoaded();

  let recipes = await db.recipeDataset.toArray();

  // Apply filters
  if (input.query) {
    const q = input.query.toLowerCase();
    recipes = recipes.filter(r =>
      r.name.toLowerCase().includes(q) ||
      r.description.toLowerCase().includes(q)
    );
  }

  if (input.ingredients && input.ingredients.length > 0) {
    const ingLower = input.ingredients.map(i => i.toLowerCase());
    recipes = recipes.filter(r =>
      ingLower.some(ing =>
        r.ingredients.some(ri => ri.name.toLowerCase().includes(ing))
      )
    );
  }

  if (input.cuisine) {
    const c = input.cuisine.toLowerCase();
    recipes = recipes.filter(r =>
      r.cuisines.some(rc => rc.toLowerCase().includes(c)) ||
      r.tags.some(t => t.toLowerCase().includes(c))
    );
  }

  if (input.maxTime) {
    recipes = recipes.filter(r => (r.totalTimeMinutes || 60) <= input.maxTime!);
  }

  if (input.tags && input.tags.length > 0) {
    const tagsLower = input.tags.map(t => t.toLowerCase());
    recipes = recipes.filter(r =>
      tagsLower.some(tag => r.tags.some(rt => rt.toLowerCase().includes(tag)))
    );
  }

  if (input.protein) {
    const p = input.protein.toLowerCase();
    recipes = recipes.filter(r => {
      const ingText = r.ingredients.map(i => i.name.toLowerCase()).join(' ');
      if (p === 'chicken') return ingText.includes('chicken');
      if (p === 'beef') return ingText.includes('beef') || ingText.includes('steak');
      if (p === 'pork') return ingText.includes('pork') || ingText.includes('bacon') || ingText.includes('ham');
      if (p === 'seafood') return ingText.includes('fish') || ingText.includes('shrimp') || ingText.includes('salmon');
      if (p === 'vegetarian') return !ingText.includes('chicken') && !ingText.includes('beef') && !ingText.includes('pork') && !ingText.includes('fish');
      return true;
    });
  }

  // Limit and format results
  const results = recipes.slice(0, 20).map(r => ({
    id: r.sourceId,
    name: r.name,
    description: r.description.slice(0, 100),
    time: r.totalTimeMinutes,
    servings: r.servings,
    cuisines: r.cuisines,
    tags: r.tags.slice(0, 4),
    mainIngredients: r.ingredients.slice(0, 5).map(i => i.name)
  }));

  return JSON.stringify(results);
}

async function handleGetExpiringIngredients(): Promise<string> {
  const now = new Date();
  const weekFromNow = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
  const threeDaysAgo = new Date(now.getTime() - 3 * 24 * 60 * 60 * 1000);

  // Perishable categories that should be prioritized for use
  const perishableCategories = ['produce', 'protein', 'dairy'];

  const allIngredients = await db.ingredients
    .filter(i => i.quantityRemaining > 0)
    .toArray();

  const results: Array<{
    name: string;
    quantity: number;
    unit: string;
    category: string;
    daysUntilExpiry: number | null;
    reason: string;
  }> = [];

  for (const i of allIngredients) {
    // Case 1: Has explicit expiry date within a week
    if (i.expiryDate && i.expiryDate <= weekFromNow) {
      results.push({
        name: i.name,
        quantity: i.quantityRemaining,
        unit: i.unit,
        category: i.category,
        daysUntilExpiry: Math.ceil((i.expiryDate.getTime() - now.getTime()) / (24 * 60 * 60 * 1000)),
        reason: 'expiring_soon'
      });
    }
    // Case 2: Perishable category, added 3+ days ago (likely needs to be used)
    else if (perishableCategories.includes(i.category) && i.dateAdded <= threeDaysAgo) {
      results.push({
        name: i.name,
        quantity: i.quantityRemaining,
        unit: i.unit,
        category: i.category,
        daysUntilExpiry: null,
        reason: 'perishable_category'
      });
    }
  }

  // Sort by urgency: expiring items first, then by days until expiry
  results.sort((a, b) => {
    if (a.daysUntilExpiry !== null && b.daysUntilExpiry !== null) {
      return a.daysUntilExpiry - b.daysUntilExpiry;
    }
    if (a.daysUntilExpiry !== null) return -1;
    if (b.daysUntilExpiry !== null) return 1;
    return 0;
  });

  return JSON.stringify(results);
}

async function handleGetRecentMeals(input: { weeks?: number }): Promise<string> {
  const weeks = input.weeks || 3;
  const recent = await getRecentRecipes(weeks);

  const results = recent.map(r => ({
    name: r.recipeName,
    cookedAt: r.dateCooked,
    rating: r.rating
  }));

  return JSON.stringify(results);
}

async function handleGetRecipeDetails(input: { recipeIds: number[] }): Promise<string> {
  await ensureRecipeDataLoaded();

  const recipes = await db.recipeDataset
    .where('sourceId')
    .anyOf(input.recipeIds)
    .toArray();

  const results = recipes.map(r => ({
    id: r.sourceId,
    name: r.name,
    description: r.description,
    servings: r.servings,
    time: r.totalTimeMinutes,
    ingredients: r.ingredients,
    steps: r.steps,
    tags: r.tags,
    cuisines: r.cuisines
  }));

  return JSON.stringify(results);
}

async function processToolCall(toolName: string, args: Record<string, unknown>): Promise<string> {
  switch (toolName) {
    case 'search_recipes':
      return handleSearchRecipes(args as Parameters<typeof handleSearchRecipes>[0]);
    case 'get_expiring_ingredients':
      return handleGetExpiringIngredients();
    case 'get_recent_meals':
      return handleGetRecentMeals(args as { weeks?: number });
    case 'get_recipe_details':
      return handleGetRecipeDetails(args as { recipeIds: number[] });
    default:
      return JSON.stringify({ error: `Unknown tool: ${toolName}` });
  }
}

// ============================================================================
// Gemini API Calls with Function Calling
// ============================================================================

async function callGeminiWithTools(
  systemPrompt: string,
  userPrompt: string,
  maxIterations: number = 10
): Promise<string> {
  const key = getGeminiKey();
  if (!key) throw new Error('Gemini API key not configured');

  const ai = new GoogleGenAI({ apiKey: key });

  // Use any[] to avoid complex SDK type conflicts - the SDK is flexible
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const contents: any[] = [
    { role: 'user', parts: [{ text: userPrompt }] }
  ];

  for (let i = 0; i < maxIterations; i++) {
    let response;
    try {
      response = await ai.models.generateContent({
        model: 'gemini-3-pro-preview',
        config: {
          systemInstruction: systemPrompt,
          tools: [{ functionDeclarations: FUNCTION_DECLARATIONS }],
        },
        contents: contents
      });
    } catch (err) {
      // Retry once on network error
      console.warn(`Gemini API call failed (attempt ${i + 1}), retrying...`, err);
      // Wait 1s before retry
      await new Promise(resolve => setTimeout(resolve, 1000));
      response = await ai.models.generateContent({
        model: 'gemini-3-pro-preview',
        config: {
          systemInstruction: systemPrompt,
          tools: [{ functionDeclarations: FUNCTION_DECLARATIONS }],
        },
        contents: contents
      });
    }

    const candidate = response.candidates?.[0];
    if (!candidate?.content?.parts) {
      throw new Error('No response from Gemini');
    }

    const parts = candidate.content.parts;

    // Check for function calls
    const functionCalls = parts.filter(p => p.functionCall);

    if (functionCalls.length === 0) {
      // No function calls - extract text response
      const textPart = parts.find(p => p.text);
      return textPart?.text || '';
    }

    // Process function calls
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const functionResponses: any[] = [];
    for (const part of functionCalls) {
      if (part.functionCall && part.functionCall.name) {
        const result = await processToolCall(
          part.functionCall.name,
          (part.functionCall.args || {}) as Record<string, unknown>
        );
        // Parse result and ensure it's wrapped in an object (Gemini requires object, not array)
        const parsedResult = JSON.parse(result);
        const wrappedResult = Array.isArray(parsedResult)
          ? { results: parsedResult }
          : parsedResult;
        functionResponses.push({
          functionResponse: {
            name: part.functionCall.name,
            response: wrappedResult
          }
        });
      }
    }

    // Add model's response and function results to conversation
    contents.push({
      role: 'model',
      parts: parts
    });
    contents.push({
      role: 'user',
      parts: functionResponses
    });
  }

  throw new Error('Max tool iterations exceeded');
}

// ============================================================================
// Stage 1: Meal Planning
// ============================================================================

async function planMeals(
  pantryState: Ingredient[],
  onProgress?: (progress: GenerationProgress) => void
): Promise<MealOutline[]> {
  const season = getCurrentSeason();
  const seasonalIngs = SEASONAL_INGREDIENTS[season];
  const targetServings = getTargetServings();
  const unitSystem = getUnitSystem();

  // Load user's taste preferences
  const preferenceSummary = await getStoredPreferenceSummary();

  onProgress?.({ phase: 'outlines', current: 0, total: 1 });

  // Build preference section for prompt
  let preferenceSection = '';
  if (preferenceSummary) {
    const parts: string[] = [];
    if (preferenceSummary.summary) {
      parts.push(`Taste profile: ${preferenceSummary.summary}`);
    }
    if (preferenceSummary.likes && preferenceSummary.likes.length > 0) {
      parts.push(`Favorite ingredients: ${preferenceSummary.likes.join(', ')}`);
    }
    if (preferenceSummary.dislikes && preferenceSummary.dislikes.length > 0) {
      parts.push(`Ingredients to avoid (DO NOT USE): ${preferenceSummary.dislikes.join(', ')}`);
    }
    if (parts.length > 0) {
      preferenceSection = `
USER TASTE PREFERENCES:
${parts.join('\n')}
- Respect the user's ingredients to avoid - do NOT include meals featuring those ingredients
- Favor the user's favorite ingredients
- Consider their taste profile when selecting recipes
`;
    }
  }

  const systemPrompt = `You are a meal planning architect. Your job is to compose 24 complete, balanced dinner meals for the week.

IMPORTANT CONCEPTS:
- A "meal" is a complete dinner: main protein + carb side + vegetable side (if not already included)
- You have access to a recipe database of 10,000 dinner recipes
- Use the tools to search for recipes, then compose complete meals
- Scale recipes to ${targetServings} servings

BALANCE REQUIREMENTS (across all 24 meals):
- Proteins: ~7 chicken, ~5 beef, ~4 pork, ~3 seafood, ~5 vegetarian
- Formats: ~4 bowls, ~4 sandwiches/burgers, ~4 tacos/wraps, ~4 pasta, ~3 soups/stews, ~3 sheet pan, ~2 salads
- Time: ~16 quick (≤30 min), ~8 longer (45-60 min)
- Seasonal: 4-6 meals should feature seasonal ingredients for ${season}: ${seasonalIngs.join(', ')}
${preferenceSection}
UNIT SYSTEM: ${unitSystem.toUpperCase()}
${unitSystem === 'metric'
      ? '- Use grams (g) for proteins and produce weights, milliliters (ml) for liquids, Celsius for temperatures'
      : '- Use pounds (lb) for proteins and produce weights, cups for liquids, Fahrenheit for temperatures'}

CRITICAL VARIETY RULES:
- NEVER repeat the same cuisine+protein combo more than twice (e.g., only 2 Thai chicken dishes max)
- Each meal must have a DISTINCT main flavor profile - no two meals should taste similar
- Spread cuisines evenly: Italian, Mexican, Asian (varied: Thai/Chinese/Japanese/Korean), Indian, Mediterranean, American, etc.
- Carb sides must vary: rice only 4-5 times, then rotate pasta, bread, potatoes, quinoa, noodles, tortillas
- If you find yourself gravitating toward one type, STOP and deliberately choose something different

SIDE DISH QUALITY:
- CARB SIDES (rice, noodles, pasta): Plain is fine - "steamed jasmine rice", "egg noodles", "spaghetti" are all acceptable
- VEGETABLE SIDES: Need a cooking method - roasted, sautéed, grilled, glazed, etc. NOT just raw/plain
- SALAD SIDES: Need a specific composed dressing with components, not just "salad dressing"
- BAD: "mixed salad greens with dressing", "raw vegetables"
- GOOD: "arugula salad with shaved parmesan and lemon-olive oil dressing", "roasted broccoli with garlic"

HOMEMADE SAUCES & DRESSINGS (15-minute rule):
- If a sauce/dressing can be made in under 15 minutes from basic ingredients, make it from scratch
- DO make from scratch: vinaigrettes (oil + vinegar + herbs), teriyaki (soy + mirin + sugar), pan sauces, alfredo (cream + parmesan), chimichurri, tahini dressing, honey mustard
- OK to use premade: gochujang, hoisin, fish sauce, tomato paste/sauce, curry paste, miso, sriracha
- Never just say "dressing" - specify components: "lemon-olive oil dressing" or "balsamic vinaigrette"

PERISHABLE PRIORITY:
- Call get_expiring_ingredients first to check what needs to be used up
- IF the tool returns expiring ingredients: prioritize them in ~8 meals, mark with usesExpiringIngredients: true
- IF the tool returns EMPTY or no results: set usesExpiringIngredients: false for ALL meals, leave expiringIngredientsUsed as empty array
- NEVER invent or assume expiring ingredients - only use what the tool returns
- Only mark usesExpiringIngredients: true if the ingredient ACTUALLY came from the get_expiring_ingredients results

WORKFLOW:
1. Call get_expiring_ingredients to check for items needing use (may be empty - that's OK!)
2. Call get_recent_meals to avoid repetition
3. Use search_recipes to find main dishes, then sides as needed
4. Compose complete meals (main + carb + veggie)
5. Return the final 24 meal outlines

Respond with JSON at the end:
{
  "meals": [
    {
      "name": "Honey Garlic Pork Chops with Roasted Potatoes",
      "description": "Pan-seared pork with crispy herb potatoes and green beans",
      "components": {
        "main": "Honey Garlic Pork Chops",
        "carb": "Rosemary Roasted Potatoes",
        "veggie": "Sautéed Green Beans"
      },
      "sourceRecipeIds": [12345, 67890],
      "estimatedTime": 40,
      "tags": ["american", "comfort", "one-pan"],
      "usesExpiringIngredients": false,
      "expiringIngredientsUsed": []
    }
  ]
}`;

  const userPrompt = `Plan 24 complete dinner meals for this week.

Current pantry (non-expiring staples):
${pantryState.filter(i => !i.perishable).map(i => `${i.name}: ${i.quantityRemaining} ${i.unit}`).join('\n')}

Start by checking for expiring ingredients and recent meals, then search for recipes to compose your meal plan.`;

  const result = await callGeminiWithTools(systemPrompt, userPrompt);

  onProgress?.({ phase: 'outlines', current: 1, total: 1 });

  // Extract JSON from response
  const jsonMatch = result.match(/\{[\s\S]*"meals"[\s\S]*\}/);
  if (!jsonMatch) {
    throw new Error('Failed to parse meal plan response');
  }

  const parsed = JSON.parse(jsonMatch[0]);
  return parsed.meals as MealOutline[];
}

// ============================================================================
// Stage 2: Recipe Construction (Batched)
// ============================================================================

async function constructMealBatch(
  mealOutlines: MealOutline[],
  batchIndex: number,
  onProgress?: (progress: GenerationProgress) => void
): Promise<ComposedMeal[]> {
  const targetServings = getTargetServings();
  const unitSystem = getUnitSystem();

  const systemPrompt = `You are constructing detailed recipe cards for composed meals. Each meal may combine multiple source recipes into a unified cooking flow.

HOMEMADE SAUCES & DRESSINGS:
- Make sauces/dressings from scratch using basic components - never list "salad dressing" as an ingredient
- Break down into components: oil, vinegar, mustard, honey, garlic, herbs, etc.
- Examples: "2 tbsp olive oil, 1 tbsp red wine vinegar, 1 tsp dijon mustard" NOT "3 tbsp vinaigrette"
- Teriyaki = soy sauce + mirin + sugar + ginger. Alfredo = cream + parmesan + butter + garlic.
- Only use premade for complex items: gochujang, hoisin, curry paste, miso, fish sauce

UNIT SYSTEM: ${unitSystem.toUpperCase()}
${unitSystem === 'metric'
      ? `- Proteins: use grams (g) for weight (e.g., "500 g chicken breast")
- Liquids: use milliliters (ml) for volume
- Produce by weight: use grams (g)
- Temperatures: use Celsius`
      : `- Proteins: use pounds (lb) or ounces (oz) for weight (e.g., "1.5 lb chicken breast")
- Liquids: use cups or fluid ounces
- Produce by weight: use pounds (lb) or ounces (oz)
- Temperatures: use Fahrenheit`}

OUTPUT FORMAT for each meal:
- name: The composed meal name
- description: 1-2 sentence description
- servings: ${targetServings}
- prepTimeMinutes / cookTimeMinutes: Based on combined workflow
- ingredients: ALL ingredients from all components, scaled to ${targetServings} servings
  - Use standard units (cup, tbsp, tsp, lb, oz, g)
  - Combine duplicates (e.g., if main and side both use garlic, combine)
  - List sauce/dressing components separately, NOT as premade items
- steps: 5-7 main procedure cards, each with 2-4 substeps
  - Organize by component initially (Prep Protein, Prep Carb, Prep Veggie)
  - Then combine (Cook Protein, Cook Sides, Assembly/Plating)
  - Interleave steps for efficient cooking (e.g., "While chicken rests, finish rice")
  - Include a step for making any dressings/sauces from scratch
- tags: 3-4 relevant tags

Respond with JSON:
{
  "meals": [
    {
      "name": "...",
      "description": "...",
      "servings": ${targetServings},
      "prepTimeMinutes": 15,
      "cookTimeMinutes": 25,
      "ingredients": [{"ingredientName": "...", "quantity": 1, "unit": "lb", "preparation": "diced"}],
      "steps": [{"title": "Prep the Protein", "substeps": ["...", "..."]}],
      "tags": ["quick", "thai"],
      "usesExpiringIngredients": true,
      "expiringIngredientsUsed": ["chicken"],
      "sourceRecipeIds": [123]
    }
  ]
}`;

  // Fetch source recipe details for this batch
  const allSourceIds = mealOutlines.flatMap(m => m.sourceRecipeIds);
  const sourceRecipes = await db.recipeDataset
    .where('sourceId')
    .anyOf(allSourceIds)
    .toArray();

  const sourceRecipeMap = new Map(sourceRecipes.map(r => [r.sourceId, r]));

  const userPrompt = `Construct detailed recipe cards for these ${mealOutlines.length} meals:

${mealOutlines.map((meal, i) => {
    const sources = meal.sourceRecipeIds
      .map(id => sourceRecipeMap.get(id))
      .filter(Boolean)
      .map(r => `  - ${r!.name}: ${r!.ingredients.length} ingredients, ${r!.steps.length} steps`);

    return `
MEAL ${i + 1}: ${meal.name}
Description: ${meal.description}
Components: Main=${meal.components.main}, Carb=${meal.components.carb || 'none'}, Veggie=${meal.components.veggie || 'none'}
Estimated time: ${meal.estimatedTime} min
Uses expiring: ${meal.usesExpiringIngredients ? meal.expiringIngredientsUsed.join(', ') : 'no'}
Source recipes:
${sources.join('\n')}`;
  }).join('\n\n')}

SOURCE RECIPE DETAILS:
${sourceRecipes.map(r => `
[${r.sourceId}] ${r.name} (${r.servings} servings, ${r.totalTimeMinutes} min)
Ingredients: ${r.ingredients.map(i => `${i.quantity || ''} ${i.unit || ''} ${i.name}`).join(', ')}
Steps: ${r.steps.join(' | ')}
`).join('\n')}

Construct the full recipe cards, scaling all ingredients to ${targetServings} servings and creating unified cooking steps.`;

  const result = await callGeminiWithTools(systemPrompt, userPrompt, 3); // Fewer iterations for construction

  // Update progress
  onProgress?.({
    phase: 'details',
    current: (batchIndex + 1) * mealOutlines.length,
    total: 24
  });

  // Extract JSON
  const jsonMatch = result.match(/\{[\s\S]*"meals"[\s\S]*\}/);
  if (!jsonMatch) {
    throw new Error('Failed to parse meal construction response');
  }

  const parsed = JSON.parse(jsonMatch[0]);
  return parsed.meals as ComposedMeal[];
}

// ============================================================================
// Main Entry Point
// ============================================================================

export interface MealArchitectResponse {
  recipes: ComposedMeal[];
  defaultSelections: number[];
}

export async function generateMealsWithArchitect(
  pantryState: Ingredient[],
  onProgress?: (progress: GenerationProgress) => void
): Promise<MealArchitectResponse> {
  // Ensure recipe data is loaded
  await ensureRecipeDataLoaded();

  // Stage 1: Plan 24 meals
  onProgress?.({ phase: 'outlines', current: 0, total: 1 });
  const mealOutlines = await planMeals(pantryState, onProgress);

  if (mealOutlines.length < 24) {
    console.warn(`Only got ${mealOutlines.length} meal outlines, expected 24`);
  }

  // Stage 2: Construct recipes in batches of 4
  const BATCH_SIZE = 4;
  const allMeals: ComposedMeal[] = [];

  for (let i = 0; i < mealOutlines.length; i += BATCH_SIZE) {
    const batch = mealOutlines.slice(i, i + BATCH_SIZE);
    const batchIndex = Math.floor(i / BATCH_SIZE);

    onProgress?.({
      phase: 'details',
      current: i,
      total: mealOutlines.length,
      recipeName: batch[0]?.name
    });

    const constructedMeals = await constructMealBatch(batch, batchIndex, onProgress);
    allMeals.push(...constructedMeals);
  }

  onProgress?.({ phase: 'normalizing', current: 0, total: 1 });

  // Select default 6 with good variety
  const defaultSelections = selectDefaultSix(allMeals);

  onProgress?.({ phase: 'normalizing', current: 1, total: 1 });

  return {
    recipes: allMeals,
    defaultSelections
  };
}

// Select 6 defaults with variety
function selectDefaultSix(meals: ComposedMeal[]): number[] {
  const selected: number[] = [];
  const usedProteins = new Set<string>();
  const usedFormats = new Set<string>();

  // Prioritize meals using expiring ingredients first
  const expiringFirst = [...meals.entries()]
    .sort((a, b) => {
      const aExp = a[1].usesExpiringIngredients ? 0 : 1;
      const bExp = b[1].usesExpiringIngredients ? 0 : 1;
      return aExp - bExp;
    });

  for (const [index, meal] of expiringFirst) {
    if (selected.length >= 6) break;

    // Determine protein and format from tags
    const tags = meal.tags.map(t => t.toLowerCase());
    const protein = tags.find(t =>
      ['chicken', 'beef', 'pork', 'seafood', 'fish', 'vegetarian', 'tofu'].includes(t)
    ) || 'other';
    const format = tags.find(t =>
      ['bowl', 'sandwich', 'burger', 'taco', 'wrap', 'pasta', 'soup', 'stew', 'salad'].includes(t)
    ) || 'other';

    // For first 4, ensure variety
    if (selected.length < 4) {
      if (usedProteins.has(protein) || usedFormats.has(format)) {
        continue;
      }
    }

    selected.push(index);
    usedProteins.add(protein);
    usedFormats.add(format);
  }

  // Fill remaining slots
  for (let i = 0; i < meals.length && selected.length < 6; i++) {
    if (!selected.includes(i)) {
      selected.push(i);
    }
  }

  return selected.slice(0, 6);
}

// ============================================================================
// Utility: Check if meal architect is available
// ============================================================================

export function isMealArchitectAvailable(): boolean {
  return !!getGeminiKey();
}
