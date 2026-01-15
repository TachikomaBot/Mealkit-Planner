package com.mealplanner.domain.repository

import com.mealplanner.data.remote.dto.CategorizedPantryItemDto
import com.mealplanner.domain.model.IngredientSource
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
     * Use AI to categorize checked shopping items for pantry storage.
     * Returns categorized items with proper pantry categories, tracking styles, and expiry dates.
     */
    suspend fun categorizeForPantry(items: List<ShoppingItem>): Result<List<CategorizedPantryItemDto>>

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
}
