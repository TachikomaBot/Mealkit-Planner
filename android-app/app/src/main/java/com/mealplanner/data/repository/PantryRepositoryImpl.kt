package com.mealplanner.data.repository

import com.mealplanner.data.local.dao.PantryDao
import com.mealplanner.data.local.entity.PantryEntity
import com.mealplanner.domain.model.PantryCategory
import com.mealplanner.domain.model.PantryItem
import com.mealplanner.domain.model.StockLevel
import com.mealplanner.domain.repository.PantryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PantryRepositoryImpl @Inject constructor(
    private val pantryDao: PantryDao
) : PantryRepository {

    override fun observeAllItems(): Flow<List<PantryItem>> {
        return pantryDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeByCategory(category: PantryCategory): Flow<List<PantryItem>> {
        return pantryDao.observeByCategory(category.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeItemCount(): Flow<Int> {
        return pantryDao.observeCount()
    }

    override suspend fun getAllItems(): List<PantryItem> {
        return pantryDao.getAll().map { it.toDomain() }
    }

    override suspend fun getItemById(id: Long): PantryItem? {
        return pantryDao.getById(id)?.toDomain()
    }

    override suspend fun searchItems(query: String): List<PantryItem> {
        return pantryDao.search(query).map { it.toDomain() }
    }

    override suspend fun getByCategory(category: PantryCategory): List<PantryItem> {
        return pantryDao.getByCategory(category.name).map { it.toDomain() }
    }

    override suspend fun getItemsNeedingStockCheck(): List<PantryItem> {
        return pantryDao.getAll()
            .map { it.toDomain() }
            .filter { it.needsStockCheck }
    }

    override suspend fun getItemCount(): Int {
        return pantryDao.getCount()
    }

    override suspend fun addItem(item: PantryItem): Long {
        val entity = PantryEntity.fromDomain(item.copy(
            perishable = item.category.isPerishable
        ))
        return pantryDao.insert(entity)
    }

    override suspend fun updateItem(item: PantryItem) {
        val entity = PantryEntity.fromDomain(item.copy(
            lastUpdated = java.time.LocalDateTime.now()
        ))
        pantryDao.update(entity)
    }

    override suspend fun updateQuantity(id: Long, newQuantity: Double, markAsChecked: Boolean) {
        val now = System.currentTimeMillis()
        pantryDao.updateQuantity(
            id = id,
            quantity = newQuantity,
            timestamp = now,
            stockCheck = if (markAsChecked) now else null
        )
    }

    override suspend fun deductByName(ingredientName: String, amount: Double): Boolean {
        val rowsAffected = pantryDao.deductByName(
            ingredientName = ingredientName,
            amount = amount,
            timestamp = System.currentTimeMillis()
        )
        return rowsAffected > 0
    }

    override suspend fun reduceStockLevel(itemId: Long): Boolean {
        val entity = pantryDao.getById(itemId) ?: return false
        val currentLevel = StockLevel.fromString(entity.stockLevel)
        val newLevel = when (currentLevel) {
            StockLevel.PLENTY -> StockLevel.SOME
            StockLevel.SOME -> StockLevel.LOW
            StockLevel.LOW, StockLevel.OUT_OF_STOCK -> StockLevel.OUT_OF_STOCK
        }
        if (newLevel != currentLevel) {
            pantryDao.updateStockLevel(itemId, newLevel.name, System.currentTimeMillis())
            return true
        }
        return false
    }

    override suspend fun setStockLevel(itemId: Long, level: StockLevel): Boolean {
        val entity = pantryDao.getById(itemId) ?: return false
        pantryDao.updateStockLevel(itemId, level.name, System.currentTimeMillis())
        return true
    }

    override suspend fun deleteItem(id: Long) {
        pantryDao.delete(id)
    }

    override suspend fun clearAll() {
        pantryDao.deleteAll()
    }

    override suspend fun addFromShoppingList(items: List<PantryItem>) {
        val entities = items.map { item ->
            PantryEntity.fromDomain(item.copy(
                perishable = item.category.isPerishable,
                dateAdded = java.time.LocalDateTime.now(),
                lastUpdated = java.time.LocalDateTime.now()
            ))
        }
        pantryDao.insertAll(entities)
    }
}
