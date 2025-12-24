// Shared types for the backend API

export interface DatasetIngredient {
  quantity: number | null;
  unit: string | null;
  name: string;
  rawName: string;
  preparation: string | null;
}

export interface DatasetRecipe {
  id?: number;
  sourceId: number;
  name: string;
  description: string;
  servings: number;
  servingSizeGrams: number | null;
  totalTimeMinutes: number | null;
  ingredients: DatasetIngredient[];
  steps: string[];
  tags: string[];
  category: string | null;
  cuisines: string[];
  dietaryFlags: string[];
  rating: number | null;
}

export interface RecipeSearchOptions {
  category?: string;
  cuisines?: string[];
  dietaryFlags?: string[];
  maxTotalTime?: number;
  servings?: { min?: number; max?: number };
  includeIngredients?: string[];
  excludeIngredients?: string[];
  searchText?: string;
  limit?: number;
  offset?: number;
  random?: boolean;
}

export interface RecipeMatch {
  recipe: DatasetRecipe;
  score: number;
  matchedIngredients: string[];
}

// Meal generation types
export interface GeneratedRecipe {
  name: string;
  description: string;
  servings: number;
  prepTimeMinutes: number;
  cookTimeMinutes: number;
  ingredients: RecipeIngredient[];
  steps: CookingStep[];
  tags: string[];
}

export interface RecipeIngredient {
  ingredientName: string;
  quantity: number | null; // null for "to taste" ingredients
  unit: string;
  preparation: string | null;
}

export interface CookingStep {
  title: string;
  substeps: string[];
}

export interface MealPlanRequest {
  pantryItems: PantryItem[];
  preferences: UserPreferences | null;
  recentRecipeHashes: string[];
  numDays?: number;
}

export interface PantryItem {
  name: string;
  quantity: number;
  unit: string;
  availability?: string; // "plenty", "some", "low", "out" - stock level from Android
}

export interface UserPreferences {
  likes: string[];
  dislikes: string[];
  summary?: string;
  targetServings?: number;
}

export interface MealPlanResponse {
  recipes: GeneratedRecipe[];
  defaultSelections: number[];
}

// Progress event for SSE streaming
export interface ProgressEvent {
  phase: 'planning' | 'building' | 'generating_images' | 'complete';
  current: number;
  total: number;
  message?: string;
  day?: number;
}

// Grocery list types
export interface GroceryPolishRequest {
  ingredients: GroceryIngredient[];
  pantryItems?: PantryItem[];
}

export interface GroceryIngredient {
  id: number;
  name: string;
  quantity: number;
  unit: string;
}

export interface PolishedGroceryItem {
  name: string;
  displayQuantity: string;
  category: string;
}

export interface GroceryPolishResponse {
  items: PolishedGroceryItem[];
}

// Async job management types
export type JobStatus = 'pending' | 'running' | 'completed' | 'failed';

export interface MealPlanJob {
  id: string;
  status: JobStatus;
  progress: ProgressEvent | null;
  result: MealPlanResponse | null;
  error: string | null;
  createdAt: Date;
  updatedAt: Date;
}

// Grocery polish job types
export interface GroceryPolishJob {
  id: string;
  status: JobStatus;
  progress: GroceryPolishProgress | null;
  result: GroceryPolishResponse | null;
  error: string | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface GroceryPolishProgress {
  phase: 'polishing' | 'merging' | 'complete';
  currentBatch: number;
  totalBatches: number;
  message?: string;
}
