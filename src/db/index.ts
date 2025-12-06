import Dexie, { type EntityTable } from 'dexie';
import type {
  Ingredient,
  Recipe,
  RecipeHistory,
  MealPlan,
  ShoppingListItem,
  UserPreferences,
  PreferenceSummary,
} from './types';
import { getNextWeekStart } from '../utils/settings';
import { consolidateShoppingList, type RawIngredient } from '../services/shoppingConsolidation';
import { generatePreferenceSummary, type HistoryEntryForSummary, hasGeminiKey } from '../api/gemini';

// Image cache entry type
export interface ImageCacheEntry {
  id?: number;
  recipeName: string;
  imageDataUrl: string;
  generatedAt: Date;
}

// Clean up old cached images (older than specified days)
export async function cleanupExpiredImages(maxAgeDays: number = 21): Promise<number> {
  try {
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - maxAgeDays);

    // Find and delete old entries
    const oldEntries = await db.imageCache
      .where('generatedAt')
      .below(cutoffDate)
      .toArray();

    if (oldEntries.length > 0) {
      const idsToDelete = oldEntries.map(e => e.id!).filter(id => id !== undefined);
      await db.imageCache.bulkDelete(idsToDelete);
      console.log(`Cleaned up ${oldEntries.length} expired cached images`);
    }

    return oldEntries.length;
  } catch (error) {
    console.error('Failed to cleanup expired images:', error);
    return 0;
  }
}

// Plan session state for persistence between tab navigations
export interface PlanSessionState {
  id?: number;
  recipePool: string; // JSON stringified GeneratedRecipe[]
  selectedIndices: number[];
  generationStartedAt: Date | null; // Track if generation was interrupted
  savedAt: Date;
}

// Recipe dataset entry (imported from Food.com)
export interface DatasetRecipe {
  id?: number;
  sourceId: number;                // Original Food.com recipe ID
  name: string;
  description: string;
  servings: number;
  servingSizeGrams: number | null;
  totalTimeMinutes: number | null;
  ingredients: DatasetIngredient[];
  steps: string[];
  tags: string[];
  category: string | null;         // "dinner", "breakfast", "dessert", etc.
  cuisines: string[];              // "italian", "mexican", "asian", etc.
  dietaryFlags: string[];          // "vegetarian", "vegan", "gluten-free", etc.
  rating: number | null;           // Average rating (not in current dataset but could add)
}

export interface DatasetIngredient {
  quantity: number | null;
  unit: string | null;
  name: string;                    // Normalized name
  rawName: string;                 // Original name from dataset
  preparation: string | null;
}

// Database class extending Dexie
class MealPlannerDB extends Dexie {
  ingredients!: EntityTable<Ingredient, 'id'>;
  recipes!: EntityTable<Recipe, 'id'>;
  recipeHistory!: EntityTable<RecipeHistory, 'id'>;
  mealPlans!: EntityTable<MealPlan, 'id'>;
  shoppingList!: EntityTable<ShoppingListItem, 'id'>;
  userPreferences!: EntityTable<UserPreferences, 'id'>;
  imageCache!: EntityTable<ImageCacheEntry, 'id'>;
  planSessionState!: EntityTable<PlanSessionState, 'id'>;
  recipeDataset!: EntityTable<DatasetRecipe, 'id'>;
  preferenceSummary!: EntityTable<PreferenceSummary, 'id'>;

  constructor() {
    super('MealPlannerDB');

    this.version(1).stores({
      // Primary key is 'id', indexed fields listed after
      ingredients: '++id, name, category, expiryDate, lastUpdated',
      recipes: '++id, name, *tags, dateCreated, lastCooked',
      recipeHistory: '++id, recipeId, recipeHash, dateCooked',
      mealPlans: '++id, weekOf',
      shoppingList: '++id, mealPlanId, category, checked',
      userPreferences: '++id',
    });

    // Version 2: Add image cache
    this.version(2).stores({
      ingredients: '++id, name, category, expiryDate, lastUpdated',
      recipes: '++id, name, *tags, dateCreated, lastCooked',
      recipeHistory: '++id, recipeId, recipeHash, dateCooked',
      mealPlans: '++id, weekOf',
      shoppingList: '++id, mealPlanId, category, checked',
      userPreferences: '++id',
      imageCache: '++id, recipeName, generatedAt',
    });

    // Version 3: Add plan session state for persistence
    this.version(3).stores({
      ingredients: '++id, name, category, expiryDate, lastUpdated',
      recipes: '++id, name, *tags, dateCreated, lastCooked',
      recipeHistory: '++id, recipeId, recipeHash, dateCooked',
      mealPlans: '++id, weekOf',
      shoppingList: '++id, mealPlanId, category, checked',
      userPreferences: '++id',
      imageCache: '++id, recipeName, generatedAt',
      planSessionState: '++id, savedAt',
    });

    // Version 4: Add recipe dataset for Food.com imports
    this.version(4).stores({
      ingredients: '++id, name, category, expiryDate, lastUpdated',
      recipes: '++id, name, *tags, dateCreated, lastCooked',
      recipeHistory: '++id, recipeId, recipeHash, dateCooked',
      mealPlans: '++id, weekOf',
      shoppingList: '++id, mealPlanId, category, checked',
      userPreferences: '++id',
      imageCache: '++id, recipeName, generatedAt',
      planSessionState: '++id, savedAt',
      recipeDataset: '++id, sourceId, name, category, *cuisines, *dietaryFlags, *tags',
    });

    // Version 5: Add preference summary for compacted history
    this.version(5).stores({
      ingredients: '++id, name, category, expiryDate, lastUpdated',
      recipes: '++id, name, *tags, dateCreated, lastCooked',
      recipeHistory: '++id, recipeId, recipeHash, dateCooked, recipeName',
      mealPlans: '++id, weekOf',
      shoppingList: '++id, mealPlanId, category, checked',
      userPreferences: '++id',
      imageCache: '++id, recipeName, generatedAt',
      planSessionState: '++id, savedAt',
      recipeDataset: '++id, sourceId, name, category, *cuisines, *dietaryFlags, *tags',
      preferenceSummary: '++id, lastUpdated',
    });
  }
}

// Single database instance
export const db = new MealPlannerDB();

// Helper functions for common operations

export async function addIngredient(ingredient: Omit<Ingredient, 'id' | 'dateAdded' | 'lastUpdated' | 'lastStockCheck'>): Promise<number> {
  const now = new Date();
  const id = await db.ingredients.add({
    ...ingredient,
    dateAdded: now,
    lastUpdated: now,
    lastStockCheck: null,
  } as Ingredient);
  return id as number;
}

export async function updateIngredientQuantity(
  id: number,
  newQuantity: number,
  markAsChecked: boolean = false
): Promise<void> {
  const updates: Partial<Ingredient> = {
    quantityRemaining: Math.max(0, newQuantity),
    lastUpdated: new Date(),
  };
  if (markAsChecked) {
    updates.lastStockCheck = new Date();
  }
  await db.ingredients.update(id, updates);
}

export async function getExpiringIngredients(withinDays: number = 5): Promise<Ingredient[]> {
  const cutoffDate = new Date();
  cutoffDate.setDate(cutoffDate.getDate() + withinDays);

  return db.ingredients
    .where('expiryDate')
    .belowOrEqual(cutoffDate)
    .and(item => item.expiryDate !== null && item.quantityRemaining > 0)
    .toArray();
}

export async function getLowStockIngredients(thresholdPercent: number = 0.2): Promise<Ingredient[]> {
  const allIngredients = await db.ingredients.toArray();
  return allIngredients.filter(
    item => item.quantityRemaining / item.quantityInitial < thresholdPercent
  );
}

export async function addRecipe(recipe: Omit<Recipe, 'id' | 'dateCreated' | 'timesCooked' | 'lastCooked'>): Promise<number> {
  const id = await db.recipes.add({
    ...recipe,
    dateCreated: new Date(),
    timesCooked: 0,
    lastCooked: null,
  } as Recipe);
  return id as number;
}

export interface DeductionWarning {
  ingredientName: string;
  needed: number;
  unit: string;
  available: number;
  type: 'missing' | 'insufficient';
}

export interface MarkCookedResult {
  success: boolean;
  warnings: DeductionWarning[];
}

export async function markRecipeCooked(recipeId: number): Promise<MarkCookedResult> {
  const recipe = await db.recipes.get(recipeId);
  if (!recipe) return { success: false, warnings: [] };

  const now = new Date();
  const warnings: DeductionWarning[] = [];

  // Update recipe stats
  await db.recipes.update(recipeId, {
    timesCooked: recipe.timesCooked + 1,
    lastCooked: now,
  });

  // Add to history
  await db.recipeHistory.add({
    recipeId,
    recipeName: recipe.name,
    recipeHash: generateRecipeHash(recipe),
    dateCooked: now,
    rating: null,
    wouldMakeAgain: null,
    notes: null,
  });

  // Deduct ingredients from pantry, tracking warnings
  for (const recipeIngredient of recipe.ingredients) {
    const pantryItems = await db.ingredients
      .where('name')
      .equalsIgnoreCase(recipeIngredient.ingredientName)
      .toArray();

    if (pantryItems.length === 0) {
      // Ingredient not in pantry at all
      warnings.push({
        ingredientName: recipeIngredient.ingredientName,
        needed: recipeIngredient.quantity,
        unit: recipeIngredient.unit,
        available: 0,
        type: 'missing',
      });
    } else {
      const item = pantryItems[0];
      if (item.quantityRemaining < recipeIngredient.quantity) {
        // Insufficient quantity - still deduct what's available
        warnings.push({
          ingredientName: recipeIngredient.ingredientName,
          needed: recipeIngredient.quantity,
          unit: recipeIngredient.unit,
          available: item.quantityRemaining,
          type: 'insufficient',
        });
      }
      await updateIngredientQuantity(
        item.id!,
        item.quantityRemaining - recipeIngredient.quantity
      );
    }
  }

  return { success: true, warnings };
}

export async function getRecentRecipes(monthsBack: number = 3): Promise<RecipeHistory[]> {
  const cutoffDate = new Date();
  cutoffDate.setMonth(cutoffDate.getMonth() - monthsBack);

  return db.recipeHistory
    .where('dateCooked')
    .aboveOrEqual(cutoffDate)
    .toArray();
}

// Update the rating for a recipe in history
export async function rateRecipe(
  recipeName: string,
  rating: number | null,
  wouldMakeAgain: boolean | null,
  notes?: string
): Promise<void> {
  // Find the most recent history entry for this recipe
  const history = await db.recipeHistory
    .where('recipeName')
    .equals(recipeName)
    .reverse()
    .sortBy('dateCooked');

  if (history.length > 0) {
    // Update the most recent entry
    await db.recipeHistory.update(history[0].id!, {
      rating,
      wouldMakeAgain,
      notes: notes ?? history[0].notes,
    });
  } else {
    // No history entry exists - create one (recipe was viewed but not cooked yet)
    await db.recipeHistory.add({
      recipeId: 0, // Unknown - recipe might not be saved yet
      recipeName,
      recipeHash: '', // Will be populated when cooked
      dateCooked: new Date(),
      rating,
      wouldMakeAgain,
      notes: notes ?? null,
    });
  }

  // Trigger compaction check in background (don't await - let it run async)
  compactPreferenceHistoryIfNeeded().catch(err => {
    console.error('Background preference compaction failed:', err);
  });
}

// Get rating for a recipe by name
export async function getRecipeRating(recipeName: string): Promise<{ rating: number | null; wouldMakeAgain: boolean | null } | null> {
  const history = await db.recipeHistory
    .where('recipeName')
    .equals(recipeName)
    .reverse()
    .sortBy('dateCooked');

  if (history.length > 0 && (history[0].rating !== null || history[0].wouldMakeAgain !== null)) {
    return {
      rating: history[0].rating,
      wouldMakeAgain: history[0].wouldMakeAgain,
    };
  }
  return null;
}

// Get preference summary based on recipe history for smarter generation
// Combines stored summary (from compacted old history) with recent history analysis
export async function getPreferenceSummary(): Promise<{
  likedTags: string[];
  dislikedTags: string[];
  likedIngredients: string[];
  avoidIngredients: string[];
  avgRatingByTag: Record<string, number>;
}> {
  // Get stored preference summary (from compacted history)
  const storedSummary = await db.preferenceSummary.toCollection().first();

  // Get all history with ratings (these are the recent, non-compacted entries)
  const allHistory = await db.recipeHistory.toArray();
  const ratedHistory = allHistory.filter(h => h.rating !== null);

  // Get recipes to match with history
  const recipes = await db.recipes.toArray();
  const recipeByName = new Map(recipes.map(r => [r.name, r]));

  const tagRatings: Record<string, number[]> = {};
  const ingredientRatings: Record<string, number[]> = {};

  for (const h of ratedHistory) {
    const recipe = recipeByName.get(h.recipeName);
    if (!recipe || h.rating === null) continue;

    // Track tag ratings
    for (const tag of recipe.tags) {
      if (!tagRatings[tag]) tagRatings[tag] = [];
      tagRatings[tag].push(h.rating);
    }

    // Track ingredient ratings (main ingredients only)
    for (const ing of recipe.ingredients.slice(0, 5)) {
      const name = ing.ingredientName.toLowerCase();
      if (!ingredientRatings[name]) ingredientRatings[name] = [];
      ingredientRatings[name].push(h.rating);
    }
  }

  // Calculate averages and categorize from recent history
  const avgRatingByTag: Record<string, number> = {};
  const likedTags: string[] = [];
  const dislikedTags: string[] = [];

  for (const [tag, ratings] of Object.entries(tagRatings)) {
    const avg = ratings.reduce((a, b) => a + b, 0) / ratings.length;
    avgRatingByTag[tag] = avg;
    if (avg >= 4 && ratings.length >= 2) likedTags.push(tag);
    if (avg <= 2 && ratings.length >= 2) dislikedTags.push(tag);
  }

  const likedIngredients: string[] = [];
  const avoidIngredients: string[] = [];

  for (const [ing, ratings] of Object.entries(ingredientRatings)) {
    const avg = ratings.reduce((a, b) => a + b, 0) / ratings.length;
    if (avg >= 4 && ratings.length >= 2) likedIngredients.push(ing);
    if (avg <= 2 && ratings.length >= 2) avoidIngredients.push(ing);
  }

  // Merge with stored summary if available
  // Stored summary preferences are added but can be overridden by recent history
  if (storedSummary) {
    // Add stored likes that aren't in dislikes from recent history
    for (const like of storedSummary.likes) {
      const normalized = like.toLowerCase();
      if (!dislikedTags.includes(normalized) && !avoidIngredients.includes(normalized)) {
        // Could be a tag/cuisine or ingredient - add to both for matching
        if (!likedTags.includes(normalized)) likedTags.push(normalized);
        if (!likedIngredients.includes(normalized)) likedIngredients.push(normalized);
      }
    }

    // Add stored dislikes that aren't in likes from recent history
    for (const dislike of storedSummary.dislikes) {
      const normalized = dislike.toLowerCase();
      if (!likedTags.includes(normalized) && !likedIngredients.includes(normalized)) {
        if (!dislikedTags.includes(normalized)) dislikedTags.push(normalized);
        if (!avoidIngredients.includes(normalized)) avoidIngredients.push(normalized);
      }
    }
  }

  return {
    likedTags,
    dislikedTags,
    likedIngredients,
    avoidIngredients,
    avgRatingByTag,
  };
}

// Constants for history compaction
const HISTORY_COMPACTION_THRESHOLD = 50; // Trigger compaction when history exceeds this
const HISTORY_KEEP_RECENT = 20; // Keep this many recent entries for granular scoring

/**
 * Check if preference history needs compaction and perform it if needed.
 * Compacts old history entries into a summary using Gemini.
 */
export async function compactPreferenceHistoryIfNeeded(): Promise<boolean> {
  // Check if Gemini is available for summarization
  if (!hasGeminiKey()) {
    return false;
  }

  // Get history count
  const historyCount = await db.recipeHistory.count();
  if (historyCount <= HISTORY_COMPACTION_THRESHOLD) {
    return false; // No compaction needed
  }

  // Get all history sorted by date (oldest first)
  const allHistory = await db.recipeHistory
    .orderBy('dateCooked')
    .toArray();

  // Only compact entries that have ratings
  const ratedHistory = allHistory.filter(h => h.rating !== null);
  if (ratedHistory.length <= HISTORY_KEEP_RECENT) {
    return false; // Not enough rated entries to compact
  }

  // Split into entries to compact and entries to keep
  const entriesToCompact = ratedHistory.slice(0, ratedHistory.length - HISTORY_KEEP_RECENT);

  if (entriesToCompact.length === 0) {
    return false;
  }

  // Get saved recipes to match with history
  const recipes = await db.recipes.toArray();
  const recipeByName = new Map(recipes.map(r => [r.name, r]));

  // Also try to get data from the recipe dataset for more complete info
  const datasetRecipes = await db.recipeDataset.toArray();
  const datasetByName = new Map(datasetRecipes.map(r => [r.name.toLowerCase(), r]));

  // Build detailed entries for Gemini
  const entriesForSummary: HistoryEntryForSummary[] = entriesToCompact
    .filter(h => h.rating !== null)
    .map(h => {
      const savedRecipe = recipeByName.get(h.recipeName);
      const datasetRecipe = datasetByName.get(h.recipeName.toLowerCase());

      return {
        recipeName: h.recipeName,
        rating: h.rating!,
        wouldMakeAgain: h.wouldMakeAgain,
        dateCooked: h.dateCooked,
        tags: savedRecipe?.tags || datasetRecipe?.tags || [],
        cuisines: datasetRecipe?.cuisines || [],
        ingredients: savedRecipe?.ingredients.map(i => i.ingredientName) ||
                     datasetRecipe?.ingredients.map(i => i.name) || [],
      };
    });

  if (entriesForSummary.length === 0) {
    return false;
  }

  // Get existing summary if any
  const existingSummary = await db.preferenceSummary.toCollection().first();

  // Generate new summary with Gemini
  const result = await generatePreferenceSummary(
    entriesForSummary,
    existingSummary?.summary || null
  );

  if (!result) {
    console.error('Failed to generate preference summary');
    return false;
  }

  // Save or update the summary
  const newSummary: Omit<PreferenceSummary, 'id'> = {
    summary: result.summary,
    likes: result.likes,
    dislikes: result.dislikes,
    lastUpdated: new Date(),
    entriesProcessed: (existingSummary?.entriesProcessed || 0) + entriesForSummary.length,
  };

  if (existingSummary?.id) {
    await db.preferenceSummary.update(existingSummary.id, newSummary);
  } else {
    await db.preferenceSummary.add(newSummary as PreferenceSummary);
  }

  // Delete the compacted entries
  const idsToDelete = entriesToCompact.map(h => h.id!).filter(id => id !== undefined);
  await db.recipeHistory.bulkDelete(idsToDelete);

  console.log(`Compacted ${entriesToCompact.length} history entries into preference summary`);
  return true;
}

/**
 * Get the stored preference summary (if any)
 */
export async function getStoredPreferenceSummary(): Promise<PreferenceSummary | undefined> {
  return db.preferenceSummary.toCollection().first();
}

export async function getCurrentMealPlan(): Promise<MealPlan | undefined> {
  const today = new Date();
  const monday = getMonday(today);

  return db.mealPlans
    .where('weekOf')
    .equals(monday)
    .first();
}

export async function createMealPlan(recipes: { recipeId: number; plannedDate: Date | null }[]): Promise<number> {
  const weekStart = getNextWeekStart(new Date());

  const id = await db.mealPlans.add({
    weekOf: weekStart,
    recipes: recipes.map(r => ({
      ...r,
      cooked: false,
      cookedDate: null,
    })),
    shoppingListGenerated: null,
  });

  return id as number;
}

// Generate shopping list from a meal plan using Gemini for intelligent consolidation
export async function generateShoppingList(mealPlanId: number): Promise<void> {
  const mealPlan = await db.mealPlans.get(mealPlanId);
  if (!mealPlan) throw new Error('Meal plan not found');

  // Get all recipes in the plan
  const recipes: Recipe[] = [];
  for (const planned of mealPlan.recipes) {
    const recipe = await db.recipes.get(planned.recipeId);
    if (recipe) recipes.push(recipe);
  }

  // Collect raw ingredients from all recipes
  const rawIngredients: RawIngredient[] = [];
  for (const recipe of recipes) {
    for (const ing of recipe.ingredients) {
      rawIngredients.push({
        name: ing.ingredientName,
        quantity: ing.quantity,
        unit: ing.unit,
        recipeName: recipe.name,
      });
    }
  }

  // Get current pantry for Gemini to consider
  const pantry = await db.ingredients.toArray();
  const pantryForGemini = pantry.map(p => ({
    name: p.name,
    quantity: p.quantityRemaining,
    unit: p.unit,
  }));

  // Clear existing shopping list for this meal plan
  await db.shoppingList.where('mealPlanId').equals(mealPlanId).delete();

  // Use Gemini to intelligently consolidate ingredients into shopping quantities
  const consolidatedItems = await consolidateShoppingList(rawIngredients, pantryForGemini);

  // Create shopping list items from Gemini's consolidated list
  const shoppingItems: Omit<ShoppingListItem, 'id'>[] = consolidatedItems.map(item => ({
    mealPlanId,
    ingredientName: item.name,
    quantity: item.quantity,
    unit: item.unit,
    category: item.category,
    checked: false,
    inCart: false,
    notes: item.notes,
  }));

  // Bulk add all shopping items
  await db.shoppingList.bulkAdd(shoppingItems as ShoppingListItem[]);

  // Update meal plan to mark shopping list as generated
  await db.mealPlans.update(mealPlanId, {
    shoppingListGenerated: new Date(),
  });
}

// Utility functions

function getMonday(date: Date): Date {
  const d = new Date(date);
  const day = d.getDay();
  const diff = d.getDate() - day + (day === 0 ? -6 : 1);
  d.setDate(diff);
  d.setHours(0, 0, 0, 0);
  return d;
}

function generateRecipeHash(recipe: Recipe): string {
  // Simple hash based on core ingredients and technique
  const ingredientNames = recipe.ingredients
    .map(i => i.ingredientName.toLowerCase())
    .sort()
    .join(',');
  const tags = recipe.tags.sort().join(',');

  // Basic string hash
  const str = `${ingredientNames}|${tags}`;
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash;
  }
  return Math.abs(hash).toString(16);
}

// Seed pantry with common kitchen staples
export async function seedPantryStaples(): Promise<void> {
  const count = await db.ingredients.count();
  if (count > 0) return; // Already has items

  const staples: Omit<Ingredient, 'id' | 'dateAdded' | 'lastUpdated' | 'lastStockCheck'>[] = [
    // Grains & Pasta
    { name: 'Jasmine Rice', brand: null, quantityInitial: 2000, quantityRemaining: 1500, unit: 'g', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'Basmati Rice', brand: null, quantityInitial: 1000, quantityRemaining: 800, unit: 'g', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'Spaghetti', brand: null, quantityInitial: 500, quantityRemaining: 500, unit: 'g', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'Penne', brand: null, quantityInitial: 500, quantityRemaining: 400, unit: 'g', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'Egg Noodles', brand: null, quantityInitial: 400, quantityRemaining: 400, unit: 'g', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'Panko Breadcrumbs', brand: null, quantityInitial: 300, quantityRemaining: 200, unit: 'g', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'All-Purpose Flour', brand: null, quantityInitial: 2000, quantityRemaining: 1500, unit: 'g', category: 'dry goods', perishable: false, expiryDate: null },

    // Oils & Vinegars
    { name: 'Olive Oil', brand: null, quantityInitial: 750, quantityRemaining: 500, unit: 'ml', category: 'condiment', perishable: false, expiryDate: null },
    { name: 'Vegetable Oil', brand: null, quantityInitial: 1000, quantityRemaining: 700, unit: 'ml', category: 'condiment', perishable: false, expiryDate: null },
    { name: 'Sesame Oil', brand: null, quantityInitial: 250, quantityRemaining: 200, unit: 'ml', category: 'condiment', perishable: false, expiryDate: null },
    { name: 'Rice Vinegar', brand: null, quantityInitial: 500, quantityRemaining: 400, unit: 'ml', category: 'condiment', perishable: false, expiryDate: null },
    { name: 'Balsamic Vinegar', brand: null, quantityInitial: 250, quantityRemaining: 200, unit: 'ml', category: 'condiment', perishable: false, expiryDate: null },

    // Asian Sauces
    { name: 'Soy Sauce', brand: null, quantityInitial: 500, quantityRemaining: 350, unit: 'ml', category: 'condiment', perishable: false, expiryDate: null },
    { name: 'Fish Sauce', brand: null, quantityInitial: 250, quantityRemaining: 200, unit: 'ml', category: 'condiment', perishable: false, expiryDate: null },
    { name: 'Oyster Sauce', brand: null, quantityInitial: 300, quantityRemaining: 250, unit: 'ml', category: 'condiment', perishable: false, expiryDate: null },
    { name: 'Hoisin Sauce', brand: null, quantityInitial: 300, quantityRemaining: 200, unit: 'ml', category: 'condiment', perishable: false, expiryDate: null },
    { name: 'Sriracha', brand: null, quantityInitial: 500, quantityRemaining: 300, unit: 'ml', category: 'condiment', perishable: false, expiryDate: null },
    { name: 'Sambal Oelek', brand: null, quantityInitial: 250, quantityRemaining: 150, unit: 'g', category: 'condiment', perishable: false, expiryDate: null },

    // Western Sauces & Condiments
    { name: 'Tomato Paste', brand: null, quantityInitial: 200, quantityRemaining: 150, unit: 'g', category: 'condiment', perishable: false, expiryDate: null },
    { name: 'Dijon Mustard', brand: null, quantityInitial: 250, quantityRemaining: 200, unit: 'g', category: 'condiment', perishable: false, expiryDate: null },
    { name: 'Mayonnaise', brand: null, quantityInitial: 500, quantityRemaining: 400, unit: 'ml', category: 'condiment', perishable: true, expiryDate: new Date(Date.now() + 90 * 24 * 60 * 60 * 1000) },
    { name: 'Worcestershire Sauce', brand: null, quantityInitial: 300, quantityRemaining: 250, unit: 'ml', category: 'condiment', perishable: false, expiryDate: null },

    // Canned Goods
    { name: 'Canned Diced Tomatoes', brand: null, quantityInitial: 3, quantityRemaining: 3, unit: 'units', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'Coconut Milk', brand: null, quantityInitial: 2, quantityRemaining: 2, unit: 'units', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'Chicken Broth', brand: null, quantityInitial: 3, quantityRemaining: 2, unit: 'units', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'Canned Black Beans', brand: null, quantityInitial: 2, quantityRemaining: 2, unit: 'units', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'Canned Chickpeas', brand: null, quantityInitial: 2, quantityRemaining: 2, unit: 'units', category: 'dry goods', perishable: false, expiryDate: null },

    // Spices & Seasonings
    { name: 'Salt', brand: null, quantityInitial: 500, quantityRemaining: 400, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Black Pepper', brand: null, quantityInitial: 100, quantityRemaining: 80, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Garlic Powder', brand: null, quantityInitial: 100, quantityRemaining: 70, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Onion Powder', brand: null, quantityInitial: 100, quantityRemaining: 80, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Paprika', brand: null, quantityInitial: 100, quantityRemaining: 60, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Smoked Paprika', brand: null, quantityInitial: 50, quantityRemaining: 40, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Cumin', brand: null, quantityInitial: 100, quantityRemaining: 70, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Coriander', brand: null, quantityInitial: 50, quantityRemaining: 40, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Chili Powder', brand: null, quantityInitial: 100, quantityRemaining: 60, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Red Pepper Flakes', brand: null, quantityInitial: 50, quantityRemaining: 40, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Italian Seasoning', brand: null, quantityInitial: 50, quantityRemaining: 40, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Oregano', brand: null, quantityInitial: 30, quantityRemaining: 25, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Thyme', brand: null, quantityInitial: 30, quantityRemaining: 20, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Bay Leaves', brand: null, quantityInitial: 20, quantityRemaining: 15, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Cinnamon', brand: null, quantityInitial: 50, quantityRemaining: 40, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Curry Powder', brand: null, quantityInitial: 100, quantityRemaining: 70, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Garam Masala', brand: null, quantityInitial: 50, quantityRemaining: 40, unit: 'g', category: 'spice', perishable: false, expiryDate: null },
    { name: 'Chinese Five Spice', brand: null, quantityInitial: 50, quantityRemaining: 40, unit: 'g', category: 'spice', perishable: false, expiryDate: null },

    // Baking
    { name: 'Sugar', brand: null, quantityInitial: 1000, quantityRemaining: 800, unit: 'g', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'Brown Sugar', brand: null, quantityInitial: 500, quantityRemaining: 400, unit: 'g', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'Honey', brand: null, quantityInitial: 500, quantityRemaining: 350, unit: 'g', category: 'condiment', perishable: false, expiryDate: null },
    { name: 'Maple Syrup', brand: null, quantityInitial: 250, quantityRemaining: 200, unit: 'ml', category: 'condiment', perishable: false, expiryDate: null },
    { name: 'Cornstarch', brand: null, quantityInitial: 400, quantityRemaining: 300, unit: 'g', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'Baking Powder', brand: null, quantityInitial: 200, quantityRemaining: 150, unit: 'g', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'Baking Soda', brand: null, quantityInitial: 250, quantityRemaining: 200, unit: 'g', category: 'dry goods', perishable: false, expiryDate: null },

    // Nuts & Seeds
    { name: 'Sesame Seeds', brand: null, quantityInitial: 200, quantityRemaining: 150, unit: 'g', category: 'dry goods', perishable: false, expiryDate: null },
    { name: 'Peanuts', brand: null, quantityInitial: 300, quantityRemaining: 200, unit: 'g', category: 'dry goods', perishable: false, expiryDate: null },

    // Misc
    { name: 'Garlic', brand: null, quantityInitial: 3, quantityRemaining: 2, unit: 'units', category: 'produce', perishable: true, expiryDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000) },
    { name: 'Onions', brand: null, quantityInitial: 5, quantityRemaining: 3, unit: 'units', category: 'produce', perishable: true, expiryDate: new Date(Date.now() + 21 * 24 * 60 * 60 * 1000) },
  ];

  const now = new Date();
  for (const staple of staples) {
    await db.ingredients.add({
      ...staple,
      dateAdded: now,
      lastUpdated: now,
    } as Ingredient);
  }

  console.log(`Seeded pantry with ${staples.length} staple items`);
}

export type { Ingredient, Recipe, RecipeHistory, MealPlan, ShoppingListItem, UserPreferences };
