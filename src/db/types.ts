// Data models matching the planning document

export interface Ingredient {
  id?: number;
  name: string;
  brand: string | null;
  quantityInitial: number;
  quantityRemaining: number;
  unit: 'g' | 'ml' | 'units' | 'bunch';
  category: 'dry goods' | 'dairy' | 'produce' | 'protein' | 'condiment' | 'spice' | 'oils' | 'frozen' | 'other';
  perishable: boolean;
  expiryDate: Date | null;
  dateAdded: Date;
  lastUpdated: Date;
  lastStockCheck: Date | null;
  imageUrl?: string | null;
}

export interface RecipeIngredient {
  ingredientName: string;
  quantity: number;
  unit: string;
  preparation: string | null;
}

export interface CookingStep {
  title: string;
  substeps: string[];
}

export interface Recipe {
  id?: number;
  name: string;
  description: string;
  servings: number;
  prepTimeMinutes: number;
  cookTimeMinutes: number;
  ingredients: RecipeIngredient[];
  steps: CookingStep[]; // Structured cooking steps
  tags: string[];
  source: 'generated' | 'imported';
  dateCreated: Date;
  timesCooked: number;
  lastCooked: Date | null;
}

export interface RecipeHistory {
  id?: number;
  recipeId: number;
  recipeName: string;
  recipeHash: string;
  dateCooked: Date;
  rating: number | null;
  wouldMakeAgain: boolean | null;
  notes: string | null;
}

export interface PlannedRecipe {
  recipeId: number;
  plannedDate: Date | null;
  cooked: boolean;
  cookedDate: Date | null;
}

export interface MealPlan {
  id?: number;
  weekOf: Date;
  recipes: PlannedRecipe[];
  shoppingListGenerated: Date | null;
}

export interface ShoppingListItem {
  id?: number;
  mealPlanId: number;
  ingredientName: string;
  quantity: number;
  unit: string;
  category: string;
  checked: boolean;
  inCart: boolean;
  notes: string | null;
}

// Preference system types
export interface RecipeRating {
  recipeId: string;
  rating: 1 | 2 | 3 | 4 | 5 | null;
  wouldMakeAgain: boolean | null;
  date: Date;
  notes: string | null;
}

export interface IngredientPreferences {
  loved: string[];
  disliked: string[];
  allergies: string[];
}

export interface CuisineAffinity {
  [cuisine: string]: number; // 0 = avoid, 0.5 = neutral, 1 = favor
}

export interface UserPreferences {
  id?: number;
  ingredientPreferences: IngredientPreferences;
  cuisineAffinity: CuisineAffinity;
  methodAffinity: { [method: string]: number };
  spiceTolerance: 'mild' | 'medium' | 'hot' | 'very_hot';
  weekdayMaxTime: number;
  weekendMaxTime: number;
  servings: number;
}

export interface PreferenceSummary {
  id?: number;
  summary: string;           // ~1k word description of taste preferences
  dislikes: string[];        // Explicit list of disliked ingredients/cuisines
  likes: string[];           // Explicit list of liked ingredients/cuisines
  lastUpdated: Date;
  entriesProcessed: number;  // Count of history entries summarized
}
