package com.mealplanner.domain.repository

import com.mealplanner.domain.model.IngredientSource
import com.mealplanner.domain.model.ModifiedIngredient
import com.mealplanner.domain.model.RecipeIngredient
import com.mealplanner.domain.model.ShoppingItem
import com.mealplanner.domain.model.ShoppingList
import kotlinx.coroutines.flow.Flow

/**
 * Repository for shopping list management
 */
interface ShoppingRepository {

    /**
     * Observe the shopping list for a meal plan
     */
    fun observeShoppingList(mealPlanId: Long): Flow<ShoppingList?>

    /**
     * Observe the shopping list for the current/latest meal plan
     */
    fun observeCurrentShoppingList(): Flow<ShoppingList?>

    /**
     * Generate a shopping list from a meal plan's recipes
     */
    suspend fun generateShoppingList(mealPlanId: Long): Result<ShoppingList>

    /**
     * Add a custom item to the shopping list
     */
    suspend fun addItem(
        mealPlanId: Long,
        name: String,
        quantity: Double,
        unit: String,
        category: String
    ): Long

    /**
     * Toggle an item's checked state
     */
    suspend fun toggleItemChecked(itemId: Long)

    /**
     * Toggle an item's in-cart state
     */
    suspend fun toggleItemInCart(itemId: Long)

    /**
     * Reset all items to unchecked
     */
    suspend fun resetAllItems(mealPlanId: Long)

    /**
     * Delete the shopping list for a meal plan
     */
    suspend fun deleteShoppingList(mealPlanId: Long)

    /**
     * Delete a single item
     */
    suspend fun deleteItem(itemId: Long)

    /**
     * Get count of unchecked items
     */
    fun observeUncheckedCount(mealPlanId: Long): Flow<Int>

    /**
     * Get all checked items for a meal plan
     */
    suspend fun getCheckedItems(mealPlanId: Long): List<ShoppingItem>

    /**
     * Polish the shopping list using Gemini to optimize quantities
     * and categorize items for easier shopping
     */
    suspend fun polishShoppingList(mealPlanId: Long): Result<ShoppingList>

    /**
     * Clear all shopping list data
     */
    suspend fun clearAll()

    /**
     * Get source recipe information for shopping items in a meal plan.
     * Returns a map of shopping item ID to list of recipe sources.
     * Used to propagate ingredient substitutions back to recipes.
     */
    suspend fun getItemsWithSources(mealPlanId: Long): Map<Long, List<IngredientSource>>

    /**
     * Update a shopping item's name and display quantity.
     * Used when user edits items in the confirmation screen.
     */
    suspend fun updateItem(itemId: Long, name: String, displayQuantity: String)

    /**
     * Check if there's a pending polish job and resume polling if so.
     * Call this when app resumes from background.
     * @return Result with polished shopping list, or null if no pending job
     */
    suspend fun checkAndResumePendingPolish(): Result<ShoppingList>?

    /**
     * Clean up stale pending jobs older than 1 hour.
     * Should be called on app startup.
     */
    suspend fun cleanupStaleJobs()

    /**
     * Update the shopping list after recipe customization using Gemini AI.
     * Intelligently handles adding, removing, and modifying ingredients with proper
     * polishing (metric units, correct categories, proper capitalization).
     *
     * @param mealPlanId The meal plan to update
     * @param ingredientsToAdd Ingredients to add (from customization result)
     * @param ingredientsToRemove Ingredients to remove (with quantities)
     * @param ingredientsToModify Ingredients to modify
     * @param recipeName The recipe being customized (for context)
     * @return Result with updated shopping list
     */
    suspend fun updateShoppingListAfterCustomization(
        mealPlanId: Long,
        ingredientsToAdd: List<RecipeIngredient>,
        ingredientsToRemove: List<RecipeIngredient>,
        ingredientsToModify: List<ModifiedIngredient>,
        recipeName: String
    ): Result<Unit>
}
