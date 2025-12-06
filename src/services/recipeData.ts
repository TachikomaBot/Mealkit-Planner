/**
 * Recipe Data Service
 *
 * Handles loading, caching, and querying the Food.com recipe dataset.
 * Provides functions to find recipes by category, cuisine, ingredients, etc.
 */

import { db, DatasetRecipe } from '../db';
import type { Ingredient, UserPreferences } from '../db/types';
import type { GeneratedRecipe, RecipePoolResponse, GenerationProgress } from '../api/claude';
import { getPreferenceSummary } from '../db';

// ============================================================================
// Types
// ============================================================================

export interface RecipeSearchOptions {
  category?: string;               // "dinner", "breakfast", "dessert"
  cuisines?: string[];             // ["italian", "mexican"]
  dietaryFlags?: string[];         // ["vegetarian", "gluten-free"]
  maxTotalTime?: number;           // Maximum total time in minutes
  servings?: { min?: number; max?: number };
  includeIngredients?: string[];   // Must include these ingredients
  excludeIngredients?: string[];   // Must not include these ingredients
  searchText?: string;             // Free text search in name/description
  limit?: number;                  // Maximum results
  random?: boolean;                // Randomize results
}

export interface RecipeMatch {
  recipe: DatasetRecipe;
  score: number;                   // Relevance score
  matchedIngredients: string[];    // Which requested ingredients matched
}

// ============================================================================
// Data Loading
// ============================================================================

let loadingPromise: Promise<void> | null = null;

/**
 * Load recipe data from JSON into IndexedDB
 * Only loads if not already loaded
 */
export async function ensureRecipeDataLoaded(): Promise<void> {
  // Check if already loaded
  const count = await db.recipeDataset.count();
  if (count > 0) {
    return;
  }

  // If loading in progress, wait for it
  if (loadingPromise) {
    return loadingPromise;
  }

  // Start loading
  loadingPromise = loadRecipeData();
  await loadingPromise;
  loadingPromise = null;
}

async function loadRecipeData(): Promise<void> {
  console.log('Loading recipe dataset...');

  try {
    // Try to load from public data folder
    const response = await fetch('/data/recipes.json');
    if (!response.ok) {
      console.warn('Recipe data not found. Run "npm run import:recipes:meals" to generate.');
      return;
    }

    const recipes: DatasetRecipe[] = await response.json();
    console.log(`Loaded ${recipes.length} recipes from JSON`);

    // Clear existing data and bulk insert
    await db.recipeDataset.clear();
    await db.recipeDataset.bulkAdd(recipes);
    console.log('Recipe data imported to IndexedDB');

  } catch (error) {
    console.error('Failed to load recipe data:', error);
  }
}

/**
 * Get the total number of recipes in the dataset
 */
export async function getRecipeCount(): Promise<number> {
  await ensureRecipeDataLoaded();
  return db.recipeDataset.count();
}

// ============================================================================
// Recipe Queries
// ============================================================================

/**
 * Search for recipes matching the given criteria
 */
export async function searchRecipes(options: RecipeSearchOptions = {}): Promise<RecipeMatch[]> {
  await ensureRecipeDataLoaded();

  const {
    category,
    cuisines,
    dietaryFlags,
    maxTotalTime,
    servings,
    includeIngredients,
    excludeIngredients,
    searchText,
    limit = 20,
    random = false
  } = options;

  // Start with all recipes or filter by category
  let query = db.recipeDataset.toCollection();

  // Category filter
  if (category) {
    query = db.recipeDataset.where('category').equals(category);
  }

  // Get all matching recipes
  let recipes = await query.toArray();

  // Apply additional filters
  recipes = recipes.filter(recipe => {
    // Cuisine filter
    if (cuisines && cuisines.length > 0) {
      const hasCuisine = cuisines.some(c =>
        recipe.cuisines.some(rc => rc.toLowerCase().includes(c.toLowerCase()))
      );
      if (!hasCuisine) return false;
    }

    // Dietary flags filter
    if (dietaryFlags && dietaryFlags.length > 0) {
      const hasFlags = dietaryFlags.every(flag =>
        recipe.dietaryFlags.some(rf => rf.toLowerCase().includes(flag.toLowerCase()))
      );
      if (!hasFlags) return false;
    }

    // Time filter
    if (maxTotalTime && recipe.totalTimeMinutes && recipe.totalTimeMinutes > maxTotalTime) {
      return false;
    }

    // Servings filter
    if (servings) {
      if (servings.min && recipe.servings < servings.min) return false;
      if (servings.max && recipe.servings > servings.max) return false;
    }

    // Exclude ingredients filter
    if (excludeIngredients && excludeIngredients.length > 0) {
      const hasExcluded = recipe.ingredients.some(ing =>
        excludeIngredients.some(ex =>
          ing.name.toLowerCase().includes(ex.toLowerCase())
        )
      );
      if (hasExcluded) return false;
    }

    // Text search filter
    if (searchText) {
      const text = searchText.toLowerCase();
      const inName = recipe.name.toLowerCase().includes(text);
      const inDesc = recipe.description.toLowerCase().includes(text);
      const inIngredients = recipe.ingredients.some(i =>
        i.name.toLowerCase().includes(text)
      );
      if (!inName && !inDesc && !inIngredients) return false;
    }

    return true;
  });

  // Calculate scores and find matched ingredients
  const results: RecipeMatch[] = recipes.map(recipe => {
    let score = 1;
    const matchedIngredients: string[] = [];

    // Boost score for matching requested ingredients
    if (includeIngredients && includeIngredients.length > 0) {
      for (const reqIng of includeIngredients) {
        const match = recipe.ingredients.find(i =>
          i.name.toLowerCase().includes(reqIng.toLowerCase())
        );
        if (match) {
          matchedIngredients.push(match.name);
          score += 0.5;
        }
      }
      // Filter out recipes with no matching ingredients if includeIngredients specified
      if (matchedIngredients.length === 0) {
        score = 0;
      }
    }

    return { recipe, score, matchedIngredients };
  });

  // Filter out zero-score results
  let filtered = results.filter(r => r.score > 0);

  // Sort by score (descending) or randomize
  if (random) {
    filtered = shuffleArray(filtered);
  } else {
    filtered.sort((a, b) => b.score - a.score);
  }

  // Apply limit
  return filtered.slice(0, limit);
}

/**
 * Get recipes that use specific pantry ingredients
 */
export async function findRecipesWithIngredients(
  ingredientNames: string[],
  options: Omit<RecipeSearchOptions, 'includeIngredients'> = {}
): Promise<RecipeMatch[]> {
  return searchRecipes({
    ...options,
    includeIngredients: ingredientNames
  });
}

/**
 * Get a random selection of recipes for a meal plan
 */
export async function getRandomRecipesForMealPlan(
  count: number = 7,
  options: RecipeSearchOptions = {}
): Promise<DatasetRecipe[]> {
  const results = await searchRecipes({
    ...options,
    limit: count * 3, // Get more than needed for variety
    random: true
  });

  // Take the requested count
  return results.slice(0, count).map(r => r.recipe);
}

/**
 * Get a single recipe by ID
 */
export async function getRecipeById(id: number): Promise<DatasetRecipe | undefined> {
  await ensureRecipeDataLoaded();
  return db.recipeDataset.get(id);
}

/**
 * Get a single recipe by source ID (Food.com ID)
 */
export async function getRecipeBySourceId(sourceId: number): Promise<DatasetRecipe | undefined> {
  await ensureRecipeDataLoaded();
  return db.recipeDataset.where('sourceId').equals(sourceId).first();
}

/**
 * Get recipes by name (partial match)
 */
export async function searchRecipesByName(name: string, limit: number = 10): Promise<DatasetRecipe[]> {
  await ensureRecipeDataLoaded();

  const allRecipes = await db.recipeDataset.toArray();
  const lower = name.toLowerCase();

  return allRecipes
    .filter(r => r.name.toLowerCase().includes(lower))
    .slice(0, limit);
}

// ============================================================================
// Statistics & Metadata
// ============================================================================

/**
 * Get available categories and their counts
 */
export async function getCategoryStats(): Promise<Map<string, number>> {
  await ensureRecipeDataLoaded();

  const stats = new Map<string, number>();
  const recipes = await db.recipeDataset.toArray();

  for (const recipe of recipes) {
    if (recipe.category) {
      stats.set(recipe.category, (stats.get(recipe.category) || 0) + 1);
    }
  }

  return stats;
}

/**
 * Get available cuisines and their counts
 */
export async function getCuisineStats(): Promise<Map<string, number>> {
  await ensureRecipeDataLoaded();

  const stats = new Map<string, number>();
  const recipes = await db.recipeDataset.toArray();

  for (const recipe of recipes) {
    for (const cuisine of recipe.cuisines) {
      stats.set(cuisine, (stats.get(cuisine) || 0) + 1);
    }
  }

  return stats;
}

/**
 * Get most common ingredients across all recipes
 */
export async function getTopIngredients(limit: number = 50): Promise<{ name: string; count: number }[]> {
  await ensureRecipeDataLoaded();

  const counts = new Map<string, number>();
  const recipes = await db.recipeDataset.toArray();

  for (const recipe of recipes) {
    for (const ing of recipe.ingredients) {
      counts.set(ing.name, (counts.get(ing.name) || 0) + 1);
    }
  }

  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit)
    .map(([name, count]) => ({ name, count }));
}

// ============================================================================
// Recipe Pool Generation (Dataset-based)
// ============================================================================

/**
 * Generate a pool of recipes from the dataset for meal planning
 * This replaces the LLM-based generation with database queries
 */
export async function generateRecipePoolFromDataset(
  pantryState: Ingredient[],
  recentRecipes: string[], // recipe hashes to avoid
  _preferences: UserPreferences | null, // placeholder for future use
  onProgress?: (progress: GenerationProgress) => void
): Promise<RecipePoolResponse> {
  await ensureRecipeDataLoaded();

  onProgress?.({ phase: 'outlines', current: 0, total: 1 });

  // Get learned preferences from recipe history
  const learnedPrefs = await getPreferenceSummary();

  // Get all dinner recipes
  const allRecipes = await db.recipeDataset.toArray();

  if (allRecipes.length === 0) {
    throw new Error('No recipes found in dataset. Run "npm run import:recipes:dinner" first.');
  }

  onProgress?.({ phase: 'outlines', current: 1, total: 1 });
  onProgress?.({ phase: 'details', current: 0, total: 24 });

  // Score and filter recipes
  const scoredRecipes = allRecipes.map(recipe => ({
    recipe,
    score: scoreRecipe(recipe, pantryState, learnedPrefs, recentRecipes)
  })).filter(r => r.score > 0);

  // Sort by score (descending)
  scoredRecipes.sort((a, b) => b.score - a.score);

  // Select diverse recipes with variety constraints
  const selectedRecipes = selectDiverseRecipes(scoredRecipes, 24);

  onProgress?.({ phase: 'details', current: 24, total: 24 });
  onProgress?.({ phase: 'normalizing', current: 0, total: 1 });

  // Convert to GeneratedRecipe format
  const recipes: GeneratedRecipe[] = selectedRecipes.map(r => datasetToGeneratedRecipe(r.recipe));

  onProgress?.({ phase: 'normalizing', current: 1, total: 1 });

  // Select default 6 recipes with good variety
  const defaultSelections = selectDefaultSix(selectedRecipes);

  return {
    recipes,
    defaultSelections
  };
}

/**
 * Score a recipe based on various factors
 */
function scoreRecipe(
  recipe: DatasetRecipe,
  pantryState: Ingredient[],
  learnedPrefs: { likedTags: string[]; dislikedTags: string[]; likedIngredients: string[]; avoidIngredients: string[] },
  recentRecipes: string[]
): number {
  let score = 1.0;

  // Check for avoided ingredients
  for (const avoid of learnedPrefs.avoidIngredients) {
    if (recipe.ingredients.some(i => i.name.toLowerCase().includes(avoid.toLowerCase()))) {
      return 0; // Completely exclude
    }
  }

  // Check if this is a recent recipe (by name hash)
  const nameHash = simpleHash(recipe.name);
  if (recentRecipes.includes(nameHash)) {
    return 0; // Exclude recent recipes
  }

  // Boost for matching pantry ingredients
  const pantryNames = new Set(pantryState.map(i => i.name.toLowerCase()));
  let pantryMatches = 0;
  for (const ing of recipe.ingredients) {
    if (pantryNames.has(ing.name.toLowerCase())) {
      pantryMatches++;
    }
  }
  // Boost recipes that use 2-4 pantry items (sweet spot)
  if (pantryMatches >= 2 && pantryMatches <= 4) {
    score += 0.3;
  } else if (pantryMatches >= 1) {
    score += 0.1;
  }

  // Boost for liked tags/cuisines
  for (const liked of learnedPrefs.likedTags) {
    if (recipe.tags.some(t => t.toLowerCase().includes(liked.toLowerCase())) ||
        recipe.cuisines.some(c => c.toLowerCase().includes(liked.toLowerCase()))) {
      score += 0.2;
    }
  }

  // Penalty for disliked tags/cuisines
  for (const disliked of learnedPrefs.dislikedTags) {
    if (recipe.tags.some(t => t.toLowerCase().includes(disliked.toLowerCase())) ||
        recipe.cuisines.some(c => c.toLowerCase().includes(disliked.toLowerCase()))) {
      score -= 0.3;
    }
  }

  // Boost for liked ingredients
  for (const liked of learnedPrefs.likedIngredients) {
    if (recipe.ingredients.some(i => i.name.toLowerCase().includes(liked.toLowerCase()))) {
      score += 0.1;
    }
  }

  // Slight randomness to prevent always getting same recipes
  score += Math.random() * 0.2;

  return Math.max(score, 0.1);
}

/**
 * Select diverse recipes ensuring variety across cuisines, proteins, and formats
 */
function selectDiverseRecipes(
  scoredRecipes: Array<{ recipe: DatasetRecipe; score: number }>,
  count: number
): Array<{ recipe: DatasetRecipe; score: number }> {
  const selected: Array<{ recipe: DatasetRecipe; score: number }> = [];

  // Track what we've selected for variety
  const cuisineCounts = new Map<string, number>();
  const proteinCounts = new Map<string, number>();

  // Cuisine limits (soft - we'll still pick if nothing else available)
  const maxPerCuisine = Math.ceil(count / 4); // ~6 max per cuisine
  const maxPerProtein = Math.ceil(count / 4); // ~6 max per protein type

  for (const candidate of scoredRecipes) {
    if (selected.length >= count) break;

    const recipe = candidate.recipe;

    // Check cuisine diversity
    let cuisineOk = true;
    for (const cuisine of recipe.cuisines) {
      const current = cuisineCounts.get(cuisine) || 0;
      if (current >= maxPerCuisine) {
        cuisineOk = false;
        break;
      }
    }

    // Check protein diversity
    const mainProtein = detectMainProtein(recipe);
    const proteinCount = proteinCounts.get(mainProtein) || 0;
    const proteinOk = proteinCount < maxPerProtein;

    // Prefer diverse recipes, but accept if we're running low
    if (cuisineOk && proteinOk) {
      selected.push(candidate);

      // Update counts
      for (const cuisine of recipe.cuisines) {
        cuisineCounts.set(cuisine, (cuisineCounts.get(cuisine) || 0) + 1);
      }
      proteinCounts.set(mainProtein, proteinCount + 1);
    } else if (selected.length < count * 0.7) {
      // Early in selection, be picky about variety
      continue;
    } else {
      // Later, accept what we can get
      selected.push(candidate);
      for (const cuisine of recipe.cuisines) {
        cuisineCounts.set(cuisine, (cuisineCounts.get(cuisine) || 0) + 1);
      }
      proteinCounts.set(mainProtein, proteinCount + 1);
    }
  }

  return selected;
}

/**
 * Detect the main protein in a recipe
 */
function detectMainProtein(recipe: DatasetRecipe): string {
  const ingredientText = recipe.ingredients.map(i => i.name.toLowerCase()).join(' ');

  if (ingredientText.includes('chicken') || ingredientText.includes('poultry')) return 'chicken';
  if (ingredientText.includes('beef') || ingredientText.includes('steak') || ingredientText.includes('ground meat')) return 'beef';
  if (ingredientText.includes('pork') || ingredientText.includes('bacon') || ingredientText.includes('ham')) return 'pork';
  if (ingredientText.includes('fish') || ingredientText.includes('salmon') || ingredientText.includes('tuna') ||
      ingredientText.includes('shrimp') || ingredientText.includes('seafood')) return 'seafood';
  if (ingredientText.includes('tofu') || ingredientText.includes('tempeh') || ingredientText.includes('seitan')) return 'tofu';
  if (ingredientText.includes('turkey')) return 'turkey';
  if (ingredientText.includes('lamb')) return 'lamb';

  // Check dietary flags for vegetarian
  if (recipe.dietaryFlags.some(f => f.toLowerCase().includes('vegetarian') || f.toLowerCase().includes('vegan'))) {
    return 'vegetarian';
  }

  return 'other';
}

/**
 * Select the default 6 recipes ensuring variety
 */
function selectDefaultSix(
  recipes: Array<{ recipe: DatasetRecipe; score: number }>
): number[] {
  const selected: number[] = [];
  const usedProteins = new Set<string>();
  const usedCuisines = new Set<string>();

  // First pass: get variety
  for (let i = 0; i < recipes.length && selected.length < 6; i++) {
    const recipe = recipes[i].recipe;
    const protein = detectMainProtein(recipe);
    const cuisine = recipe.cuisines[0] || 'other';

    // Skip if we already have this protein or cuisine (first 4 picks)
    if (selected.length < 4) {
      if (usedProteins.has(protein) || usedCuisines.has(cuisine)) {
        continue;
      }
    }

    selected.push(i);
    usedProteins.add(protein);
    usedCuisines.add(cuisine);
  }

  // Fill remaining slots with highest scored
  for (let i = 0; i < recipes.length && selected.length < 6; i++) {
    if (!selected.includes(i)) {
      selected.push(i);
    }
  }

  return selected;
}

/**
 * Convert a DatasetRecipe to GeneratedRecipe format
 */
function datasetToGeneratedRecipe(dataset: DatasetRecipe): GeneratedRecipe {
  // Estimate prep/cook time from total time
  const totalTime = dataset.totalTimeMinutes || 45;
  const prepTime = Math.round(totalTime * 0.3);
  const cookTime = totalTime - prepTime;

  // Convert ingredients
  const ingredients = dataset.ingredients
    .filter(i => i.name && i.name.length > 0)
    .map(i => ({
      ingredientName: i.name,
      quantity: i.quantity || 1,
      unit: i.unit || 'pcs',
      preparation: i.preparation
    }));

  // Convert steps - group into logical sections
  const steps = groupStepsIntoSections(dataset.steps);

  return {
    name: dataset.name,
    description: dataset.description,
    servings: dataset.servings || 4,
    prepTimeMinutes: prepTime,
    cookTimeMinutes: cookTime,
    ingredients,
    steps,
    // dataset.tags is already curated by selectRelevantTags() which includes cuisines/dietary
    tags: dataset.tags
  };
}

/**
 * Simple string hash for recipe deduplication
 */
function simpleHash(str: string): string {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash;
  }
  return hash.toString(16);
}

// ============================================================================
// Utilities
// ============================================================================

function shuffleArray<T>(array: T[]): T[] {
  const result = [...array];
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [result[i], result[j]] = [result[j], result[i]];
  }
  return result;
}

/**
 * Convert a DatasetRecipe to the app's Recipe format
 */
export function datasetRecipeToAppRecipe(dataset: DatasetRecipe): {
  name: string;
  description: string;
  servings: number;
  prepTimeMinutes: number;
  cookTimeMinutes: number;
  ingredients: { ingredientName: string; quantity: number; unit: string; preparation: string | null }[];
  steps: { title: string; substeps: string[] }[];
  tags: string[];
} {
  // Estimate prep/cook time from total time
  const totalTime = dataset.totalTimeMinutes || 45;
  const prepTime = Math.round(totalTime * 0.3);
  const cookTime = totalTime - prepTime;

  // Convert ingredients
  const ingredients = dataset.ingredients
    .filter(i => i.name && i.name.length > 0)
    .map(i => ({
      ingredientName: i.name,
      quantity: i.quantity || 1,
      unit: i.unit || 'pcs',
      preparation: i.preparation
    }));

  // Convert steps - group into logical sections
  const steps = groupStepsIntoSections(dataset.steps);

  return {
    name: dataset.name,
    description: dataset.description,
    servings: dataset.servings || 4,
    prepTimeMinutes: prepTime,
    cookTimeMinutes: cookTime,
    ingredients,
    steps,
    // dataset.tags is already curated by selectRelevantTags() which includes cuisines/dietary
    tags: dataset.tags
  };
}

function groupStepsIntoSections(steps: string[]): { title: string; substeps: string[] }[] {
  // Simple grouping: create sections of 3-4 steps each
  const sections: { title: string; substeps: string[] }[] = [];

  if (steps.length <= 4) {
    return [{ title: 'Instructions', substeps: steps }];
  }

  const sectionSize = Math.ceil(steps.length / Math.ceil(steps.length / 4));
  const titles = ['Preparation', 'Cooking', 'Assembly', 'Finishing'];

  for (let i = 0; i < steps.length; i += sectionSize) {
    const sectionIndex = Math.floor(i / sectionSize);
    sections.push({
      title: titles[sectionIndex] || `Step ${sectionIndex + 1}`,
      substeps: steps.slice(i, i + sectionSize)
    });
  }

  return sections;
}
