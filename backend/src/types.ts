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
  leftoversInput?: string;
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

// Pantry categorization types
export interface ShoppingItemForPantry {
  id: number;
  name: string;
  polishedDisplayQuantity: string;  // "4 pieces", "500g"
  shoppingCategory: string;
}

export interface CategorizedPantryItem {
  id: number;
  name: string;
  quantity: number;
  unit: string;       // GRAMS, MILLILITERS, UNITS, BUNCH, PIECES
  category: string;   // PRODUCE, PROTEIN, DAIRY, DRY_GOODS, SPICE, OILS, CONDIMENT, FROZEN, OTHER
  trackingStyle: string;  // STOCK_LEVEL, COUNT, PRECISE
  stockLevel: string | null;  // FULL, HIGH, MEDIUM, LOW (for STOCK_LEVEL tracking)
  expiryDays: number | null;
  perishable: boolean;
}

export interface PantryCategorizeRequest {
  items: ShoppingItemForPantry[];
}

export interface PantryCategorizeResponse {
  items: CategorizedPantryItem[];
}

export interface PantryCategorizeJob {
  id: string;
  status: JobStatus;
  progress: PantryCategorizeProgress | null;
  result: PantryCategorizeResponse | null;
  error: string | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface PantryCategorizeProgress {
  phase: 'categorizing' | 'complete';
  current: number;
  total: number;
  message?: string;
}

// Ingredient substitution types
export interface SubstitutionRequest {
  recipeName: string;
  originalIngredient: {
    name: string;
    quantity: number;
    unit: string;
    preparation: string | null;  // e.g., "torn", "minced"
  };
  newIngredientName: string;
  steps: RecipeStep[];  // Recipe steps to potentially update
}

export interface RecipeStep {
  title: string;
  substeps: string[];
}

export interface SubstitutionResponse {
  updatedRecipeName: string;
  updatedIngredient: {
    name: string;
    quantity: number;
    unit: string;
    preparation: string | null;  // e.g., "torn", "minced" - null to remove
  };
  updatedSteps: RecipeStep[];  // Updated recipe steps
  notes: string | null;  // e.g., "Dried herbs are more concentrated than fresh"
}

export interface SubstitutionJob {
  id: string;
  status: JobStatus;
  result: SubstitutionResponse | null;
  error: string | null;
  createdAt: Date;
  updatedAt: Date;
}

// Recipe customization types
export interface RecipeCustomizationRequest {
  recipeName: string;
  description: string;           // Original recipe description
  ingredients: RecipeIngredient[];
  steps: RecipeStep[];
  customizationRequest: string;  // Free-form text from user
  previousRequests?: string[];   // For refine loop context
}

export interface ModifiedIngredient {
  originalName: string;
  newName: string | null;        // null if name unchanged
  newQuantity: number | null;
  newUnit: string | null;
  newPreparation: string | null;
}

export interface RecipeCustomizationResponse {
  updatedRecipeName: string;
  updatedDescription: string;              // Updated recipe description
  ingredientsToAdd: RecipeIngredient[];
  ingredientsToRemove: string[];           // Names of ingredients to remove
  ingredientsToModify: ModifiedIngredient[];
  updatedSteps: RecipeStep[];
  changesSummary: string;                  // Human-readable summary of changes
  notes: string | null;
}

// Shopping list update types (for recipe customization)
export interface ShoppingListUpdateRequest {
  currentItems: PolishedGroceryItem[];      // Current polished shopping list
  ingredientsToAdd: RecipeIngredient[];     // Ingredients to add from customization
  ingredientsToRemove: RecipeIngredient[];  // Ingredients to remove (with quantities)
  ingredientsToModify: ModifiedIngredient[];
  recipeName: string;                       // For context in prompt
}

export interface ShoppingListUpdateResponse {
  items: PolishedGroceryItem[];             // Updated polished list
}
