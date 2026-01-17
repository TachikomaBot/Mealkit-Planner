package com.mealplanner.data.local.dao

import androidx.room.*
import com.mealplanner.data.local.entity.PantryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {

    @Query("SELECT * FROM pantry_items ORDER BY name ASC")
    fun observeAll(): Flow<List<PantryEntity>>

    @Query("SELECT * FROM pantry_items WHERE category = :category ORDER BY name ASC")
    fun observeByCategory(category: String): Flow<List<PantryEntity>>

    @Query("SELECT * FROM pantry_items ORDER BY name ASC")
    suspend fun getAll(): List<PantryEntity>

    @Query("SELECT * FROM pantry_items WHERE id = :id")
    suspend fun getById(id: Long): PantryEntity?

    @Query("SELECT * FROM pantry_items WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun search(query: String): List<PantryEntity>

    @Query("SELECT * FROM pantry_items WHERE category = :category ORDER BY name ASC")
    suspend fun getByCategory(category: String): List<PantryEntity>

    @Query("SELECT COUNT(*) FROM pantry_items")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pantry_items")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PantryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PantryEntity>)

    @Update
    suspend fun update(item: PantryEntity)

    @Query("UPDATE pantry_items SET quantityRemaining = :quantity, lastUpdated = :timestamp, lastStockCheck = :stockCheck WHERE id = :id")
    suspend fun updateQuantity(id: Long, quantity: Double, timestamp: Long, stockCheck: Long?)

    @Query("DELETE FROM pantry_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pantry_items")
    suspend fun deleteAll()

    // Deduct quantity from pantry when items are used (e.g., when cooking a recipe)
    @Query("UPDATE pantry_items SET quantityRemaining = MAX(0, quantityRemaining - :amount), lastUpdated = :timestamp WHERE LOWER(name) = LOWER(:ingredientName)")
    suspend fun deductByName(ingredientName: String, amount: Double, timestamp: Long): Int

    // Update stock level for STOCK_LEVEL tracked items (spices, oils, condiments)
    @Query("UPDATE pantry_items SET stockLevel = :newLevel, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateStockLevel(id: Long, newLevel: String, timestamp: Long)
}
