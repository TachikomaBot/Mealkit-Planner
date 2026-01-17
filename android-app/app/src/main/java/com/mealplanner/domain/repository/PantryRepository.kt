package com.mealplanner.domain.repository

import com.mealplanner.domain.model.PantryCategory
import com.mealplanner.domain.model.PantryItem
import com.mealplanner.domain.model.StockLevel
import kotlinx.coroutines.flow.Flow

/**
 * Repository for pantry ingredient management
 */
interface PantryRepository {

    /**
     * Observe all pantry items
     */
    fun observeAllItems(): Flow<List<PantryItem>>

    /**
     * Observe items by category
     */
    fun observeByCategory(category: PantryCategory): Flow<List<PantryItem>>

    /**
     * Observe the total count of pantry items
     */
    fun observeItemCount(): Flow<Int>

    /**
     * Get all pantry items
     */
    suspend fun getAllItems(): List<PantryItem>

    /**
     * Get a specific item by ID
     */
    suspend fun getItemById(id: Long): PantryItem?

    /**
     * Search items by name
     */
    suspend fun searchItems(query: String): List<PantryItem>

    /**
     * Get items by category
     */
    suspend fun getByCategory(category: PantryCategory): List<PantryItem>

    /**
     * Get items that need stock verification
     */
    suspend fun getItemsNeedingStockCheck(): List<PantryItem>

    /**
     * Get total item count
     */
    suspend fun getItemCount(): Int

    /**
     * Add a new item to the pantry
     */
    suspend fun addItem(item: PantryItem): Long

    /**
     * Update an existing item
     */
    suspend fun updateItem(item: PantryItem)

    /**
     * Update just the quantity of an item
     */
    suspend fun updateQuantity(id: Long, newQuantity: Double, markAsChecked: Boolean = false)

    /**
     * Deduct quantity from an item by name (for recipe cooking)
     */
    suspend fun deductByName(ingredientName: String, amount: Double): Boolean

    /**
     * Reduce stock level by one notch for STOCK_LEVEL tracked items.
     * PLENTY → SOME → LOW → OUT_OF_STOCK
     * @return true if the level was reduced, false if already at minimum or item not found
     */
    suspend fun reduceStockLevel(itemId: Long): Boolean

    /**
     * Set stock level directly for STOCK_LEVEL tracked items.
     * @return true if the level was updated, false if item not found
     */
    suspend fun setStockLevel(itemId: Long, level: StockLevel): Boolean

    /**
     * Delete an item from the pantry
     */
    suspend fun deleteItem(id: Long)

    /**
     * Clear all pantry items
     */
    suspend fun clearAll()

    /**
     * Add items to pantry from shopping list (after shopping trip)
     */
    suspend fun addFromShoppingList(items: List<PantryItem>)
}
