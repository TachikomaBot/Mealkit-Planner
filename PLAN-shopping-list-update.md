# Plan: Gemini-Powered Shopping List Update for Recipe Customization

## Problem Statement

When a recipe is customized (e.g., "make this vegetarian"), the shopping list needs to be updated intelligently:
- **Current approach**: Local string matching and regex-based quantity manipulation
- **Issues**:
  1. Fuzzy matching fails (e.g., "shrimp (peeled and deveined)" doesn't match "Shrimp")
  2. Added items aren't polished (lowercase, wrong category, imperial units)
  3. Quantity parsing is fragile (produces values like "224.5g")

## Proposed Solution

Create a new Gemini-powered endpoint that intelligently updates an existing polished shopping list based on recipe customization changes.

## Current Implementation Reference

### Grocery Polish Flow (existing)
```
POST /api/meal-plan/polish-grocery-list-async
  → Creates job, returns jobId
  → Background: polishGroceryList() in geminiService.ts
    → Batches ingredients (8 per batch)
    → Each batch: polishBatch() with Gemini
    → Final: finalMergePass() to deduplicate
  → Result: PolishedGroceryItem[] with name, displayQuantity, category
```

### Recipe Customization Response (from Gemini)
```typescript
{
  ingredientsToAdd: RecipeIngredient[],     // { ingredientName, quantity, unit, preparation }
  ingredientsToRemove: string[],            // e.g., ["shrimp (peeled and deveined)"]
  ingredientsToModify: ModifiedIngredient[] // { originalName, newName, newQuantity, ... }
}
```

## Implementation Plan

### 1. Backend: New Types (types.ts)

```typescript
export interface ShoppingListUpdateRequest {
  currentItems: PolishedGroceryItem[];      // Current polished shopping list
  ingredientsToAdd: RecipeIngredient[];     // From customization result
  ingredientsToRemove: RecipeIngredient[];  // With quantities for smart subtraction
  ingredientsToModify: ModifiedIngredient[];
  recipeName: string;                       // For context in prompt
}

export interface ShoppingListUpdateResponse {
  items: PolishedGroceryItem[];             // Updated polished list
}
```

### 2. Backend: New Service Function (geminiService.ts)

**Function**: `updateShoppingListForCustomization()`

**Prompt Structure** (similar to polishBatch but for updates):
```
You are updating an existing shopping list after a recipe was customized.

RECIPE MODIFIED: "Pan-Seared Mediterranean Salmon" → "Pan-Seared Mediterranean Chicken"

CURRENT SHOPPING LIST:
[
  { "name": "Salmon Fillets", "displayQuantity": "350g (2 fillets)", "category": "Protein" },
  { "name": "Chicken Thighs", "displayQuantity": "450g (4 pieces)", "category": "Protein" },
  ...
]

CHANGES TO APPLY:
- REMOVE: 2 pieces salmon fillets (350g) from "Pan-Seared Mediterranean Salmon"
- ADD: 2 pieces chicken thighs (boneless, skinless) from the same recipe

YOUR TASK:
1. Find the matching shopping item for REMOVED ingredients:
   - "salmon fillets" matches "Salmon Fillets" → reduce quantity or remove
   - If item quantity goes to 0, remove it entirely
   - If item was from multiple recipes, reduce quantity appropriately

2. For ADDED ingredients:
   - Check if similar item exists (e.g., "Chicken Thighs")
   - If exists: ADD the quantities together
   - If new: Create a polished entry with proper category

3. Keep ALL unchanged items exactly as they are

4. Apply the same polish rules:
   - Metric units (grams for protein, not pounds)
   - Proper capitalization
   - Appropriate categories
   - No cups/tbsp in displayQuantity

Return the COMPLETE updated shopping list.
```

**Key differences from polishBatch**:
- Works on already-polished items (not raw ingredients)
- Understands "subtract" and "add to existing" semantics
- Preserves unchanged items exactly
- Uses recipe context for smarter matching

### 3. Backend: New Route (mealplan.ts)

```typescript
// POST /api/meal-plan/update-shopping-list
router.post('/update-shopping-list', async (req, res) => {
  const apiKey = req.headers['x-gemini-api-key'];
  const { currentItems, ingredientsToAdd, ingredientsToRemove, ingredientsToModify, recipeName } = req.body;

  const result = await updateShoppingListForCustomization(
    apiKey,
    currentItems,
    ingredientsToAdd,
    ingredientsToRemove,
    ingredientsToModify,
    recipeName
  );

  res.json(result);
});
```

### 4. Android: Update ShoppingRepository

**New method in interface**:
```kotlin
suspend fun updateShoppingListAfterCustomization(
    mealPlanId: Long,
    ingredientsToAdd: List<RecipeIngredient>,
    ingredientsToRemove: List<RecipeIngredient>,
    ingredientsToModify: List<ModifiedIngredient>,
    recipeName: String
): Result<ShoppingList>
```

**Implementation**:
1. Fetch current shopping items for mealPlanId
2. Convert to DTO format for API
3. Call new backend endpoint
4. Replace shopping list items with response
5. Return updated ShoppingList

### 5. Android: Update ViewModel

Replace local `shoppingRepository.applyRecipeCustomization()` with the new Gemini-powered method:

```kotlin
// In applyCustomization() success handler:
shoppingRepository.updateShoppingListAfterCustomization(
    mealPlanId = mealPlanId,
    ingredientsToAdd = current.customization.ingredientsToAdd,
    ingredientsToRemove = removedWithQuantities,
    ingredientsToModify = current.customization.ingredientsToModify,
    recipeName = current.recipe.name
)
```

### 6. Android: New API Endpoint (MealPlanApi.kt)

```kotlin
@POST("api/meal-plan/update-shopping-list")
suspend fun updateShoppingList(
    @Body request: ShoppingListUpdateRequest
): ShoppingListUpdateResponse
```

### 7. Android: New DTOs (MealPlanDto.kt)

```kotlin
@Serializable
data class ShoppingListUpdateRequest(
    val currentItems: List<PolishedGroceryItemDto>,
    val ingredientsToAdd: List<RecipeIngredientDto>,
    val ingredientsToRemove: List<RecipeIngredientDto>,
    val ingredientsToModify: List<ModifiedIngredientDto>,
    val recipeName: String
)

@Serializable
data class PolishedGroceryItemDto(
    val name: String,
    val displayQuantity: String,
    val category: String
)

@Serializable
data class ShoppingListUpdateResponse(
    val items: List<PolishedGroceryItemDto>
)
```

## Data Flow Summary

```
User taps "Apply" on customization
    ↓
ViewModel.applyCustomization()
    ↓
1. mealPlanRepository.applyRecipeCustomization() → Updates recipe in DB
    ↓
2. refreshPlannedRecipe() → UI shows updated recipe
    ↓
3. shoppingRepository.updateShoppingListAfterCustomization()
    ↓
   a. Fetch current shopping items from DB
   b. POST to /api/meal-plan/update-shopping-list
   c. Gemini intelligently updates the list
   d. Replace shopping items in DB with response
    ↓
4. Shopping list UI auto-updates (observes Flow)
```

## Benefits

1. **Semantic matching**: Gemini understands "shrimp" = "Shrimp (peeled and deveined)"
2. **Consistent polish**: New items use same formatting as existing (metric, proper case)
3. **Smart quantity math**: Gemini handles "350g (2 fillets)" + "350g (2 fillets)" = "700g (4 fillets)"
4. **Proper categorization**: New items get correct category
5. **Preserves unchanged items**: Exact preservation of items not affected by customization

## Considerations

- **Latency**: Adds ~2-3s Gemini call after customization
- **Cost**: One additional Gemini API call per customization
- **Fallback**: If Gemini fails, could fall back to local logic (current implementation)

## Files to Modify

### Backend
1. `backend/src/types.ts` - Add new request/response types
2. `backend/src/services/geminiService.ts` - Add `updateShoppingListForCustomization()`
3. `backend/src/routes/mealplan.ts` - Add new route

### Android
4. `MealPlanApi.kt` - Add new endpoint
5. `MealPlanDto.kt` - Add new DTOs
6. `ShoppingRepository.kt` - Add new method to interface
7. `ShoppingRepositoryImpl.kt` - Implement new method, remove old local logic
8. `RecipeDetailViewModel.kt` - Use new method instead of local update
