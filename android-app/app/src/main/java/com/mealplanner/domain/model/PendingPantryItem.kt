package com.mealplanner.domain.model

/**
 * Represents a shopping item pending confirmation before being added to pantry.
 * Used in the confirmation screen after completing shopping.
 */
data class PendingPantryItem(
    val shoppingItemId: Long,
    val name: String,
    val displayQuantity: String,
    val isModified: Boolean = false,
    val originalName: String,
    val sources: List<IngredientSource>
) {
    /**
     * Whether this item has a name substitution (not just quantity change).
     * Name substitutions need to be propagated back to recipes.
     */
    val hasSubstitution: Boolean get() = name != originalName
}

/**
 * Tracks where a shopping item ingredient came from.
 * Used to propagate substitutions back to the source recipe(s).
 */
data class IngredientSource(
    val plannedRecipeId: Long,
    val recipeName: String,
    val ingredientIndex: Int,
    val originalQuantity: Double = 0.0,
    val originalUnit: String = ""
)
