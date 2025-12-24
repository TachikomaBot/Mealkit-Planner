import { readFileSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import type { DatasetRecipe, RecipeSearchOptions, RecipeMatch } from '../types.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// In-memory recipe store
let recipes: DatasetRecipe[] = [];
let isLoaded = false;

/**
 * Load recipes from the JSON file into memory
 */
export function loadRecipes(): void {
  if (isLoaded) return;

  // Try multiple paths for flexibility
  const possiblePaths = [
    join(__dirname, '../../data/recipes.json'),
    join(__dirname, '../../../public/data/recipes.json'),
  ];

  for (const filePath of possiblePaths) {
    if (existsSync(filePath)) {
      console.log(`Loading recipes from ${filePath}...`);
      const data = readFileSync(filePath, 'utf-8');
      recipes = JSON.parse(data);
      isLoaded = true;
      console.log(`Loaded ${recipes.length} recipes`);
      return;
    }
  }

  console.warn('No recipe data file found. API will return empty results.');
  recipes = [];
  isLoaded = true;
}

/**
 * Get total recipe count
 */
export function getRecipeCount(): number {
  return recipes.length;
}

/**
 * Search recipes with various filters
 */
export function searchRecipes(options: RecipeSearchOptions = {}): RecipeMatch[] {
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
    offset = 0,
    random = false,
  } = options;

  // Start with all recipes
  let filtered = recipes;

  // Category filter
  if (category) {
    filtered = filtered.filter(r => r.category === category);
  }

  // Apply additional filters
  filtered = filtered.filter(recipe => {
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
        excludeIngredients.some(ex => ing.name.toLowerCase().includes(ex.toLowerCase()))
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
  const results: RecipeMatch[] = filtered.map(recipe => {
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
  let finalResults = results.filter(r => r.score > 0);

  // Sort by score (descending) or randomize
  if (random) {
    finalResults = shuffleArray(finalResults);
  } else {
    finalResults.sort((a, b) => b.score - a.score);
  }

  // Apply pagination
  return finalResults.slice(offset, offset + limit);
}

/**
 * Get a recipe by its ID
 */
export function getRecipeById(id: number): DatasetRecipe | undefined {
  return recipes.find(r => r.id === id);
}

/**
 * Get a recipe by source ID (Food.com ID)
 */
export function getRecipeBySourceId(sourceId: number): DatasetRecipe | undefined {
  return recipes.find(r => r.sourceId === sourceId);
}

/**
 * Get category statistics
 */
export function getCategoryStats(): Record<string, number> {
  const stats: Record<string, number> = {};
  for (const recipe of recipes) {
    if (recipe.category) {
      stats[recipe.category] = (stats[recipe.category] || 0) + 1;
    }
  }
  return stats;
}

/**
 * Get cuisine statistics
 */
export function getCuisineStats(): Record<string, number> {
  const stats: Record<string, number> = {};
  for (const recipe of recipes) {
    for (const cuisine of recipe.cuisines) {
      stats[cuisine] = (stats[cuisine] || 0) + 1;
    }
  }
  return stats;
}

// Utility function
function shuffleArray<T>(array: T[]): T[] {
  const result = [...array];
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [result[i], result[j]] = [result[j], result[i]];
  }
  return result;
}
