package com.mealplanner.data.local.dao

import androidx.room.*
import com.mealplanner.data.local.entity.ShoppingItemEntity
import com.mealplanner.data.local.entity.ShoppingItemSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ShoppingItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItemEntity): Long

    @Query("SELECT * FROM shopping_items WHERE mealPlanId = :mealPlanId ORDER BY category, ingredientName")
    suspend fun getItemsForMealPlan(mealPlanId: Long): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE mealPlanId = :mealPlanId AND checked = 1 ORDER BY category, ingredientName")
    suspend fun getCheckedItemsForMealPlan(mealPlanId: Long): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE mealPlanId = :mealPlanId ORDER BY category, ingredientName")
    fun observeItemsForMealPlan(mealPlanId: Long): Flow<List<ShoppingItemEntity>>

    @Query("UPDATE shopping_items SET checked = :checked WHERE id = :id")
    suspend fun updateChecked(id: Long, checked: Boolean)

    @Query("UPDATE shopping_items SET checked = NOT checked WHERE id = :itemId")
    suspend fun toggleChecked(itemId: Long)

    @Query("UPDATE shopping_items SET inCart = :inCart WHERE id = :id")
    suspend fun updateInCart(id: Long, inCart: Boolean)

    @Query("UPDATE shopping_items SET checked = 0, inCart = 0 WHERE mealPlanId = :mealPlanId")
    suspend fun resetAllItems(mealPlanId: Long)

    @Query("DELETE FROM shopping_items WHERE mealPlanId = :mealPlanId")
    suspend fun deleteItemsForMealPlan(mealPlanId: Long)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT COUNT(*) FROM shopping_items WHERE mealPlanId = :mealPlanId AND checked = 0")
    fun observeUncheckedCount(mealPlanId: Long): Flow<Int>

    @Query("UPDATE shopping_items SET polishedDisplayQuantity = :polishedDisplayQuantity, category = :category WHERE id = :itemId")
    suspend fun updatePolishedData(itemId: Long, polishedDisplayQuantity: String, category: String)

    @Query("DELETE FROM shopping_items")
    suspend fun deleteAll()

    // Source tracking methods for ingredient substitution propagation
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<ShoppingItemSourceEntity>)

    @Query("SELECT * FROM shopping_item_sources WHERE shoppingItemId = :shoppingItemId")
    suspend fun getSourcesForItem(shoppingItemId: Long): List<ShoppingItemSourceEntity>

    @Query("""
        SELECT s.* FROM shopping_item_sources s
        INNER JOIN shopping_items i ON s.shoppingItemId = i.id
        WHERE i.mealPlanId = :mealPlanId
    """)
    suspend fun getSourcesForMealPlan(mealPlanId: Long): List<ShoppingItemSourceEntity>

    @Query("""
        DELETE FROM shopping_item_sources
        WHERE shoppingItemId IN (SELECT id FROM shopping_items WHERE mealPlanId = :mealPlanId)
    """)
    suspend fun deleteSourcesForMealPlan(mealPlanId: Long)

    @Query("UPDATE shopping_items SET ingredientName = :name, polishedDisplayQuantity = :displayQuantity WHERE id = :itemId")
    suspend fun updateItemNameAndQuantity(itemId: Long, name: String, displayQuantity: String)
}
