package com.mealplanner.data.local.dao

import androidx.room.*
import com.mealplanner.data.local.entity.ShoppingItemEntity
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
}
