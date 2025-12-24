package com.mealplanner.domain.usecase

import com.mealplanner.domain.model.PantryCategory
import com.mealplanner.domain.model.PantryItem
import com.mealplanner.domain.model.PantryUnit
import com.mealplanner.domain.model.ShoppingCategories
import com.mealplanner.domain.model.ShoppingList
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.PantryRepository
import com.mealplanner.domain.repository.ShoppingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Use case for managing shopping lists
 */
class ManageShoppingListUseCase @Inject constructor(
    private val shoppingRepository: ShoppingRepository,
    private val mealPlanRepository: MealPlanRepository,
    private val pantryRepository: PantryRepository
) {
    /**
     * Observe the shopping list for the current meal plan
     */
    fun observeCurrentShoppingList(): Flow<ShoppingList?> {
        return kotlinx.coroutines.flow.flow {
            val mealPlan = mealPlanRepository.observeCurrentMealPlan().first()
            if (mealPlan != null) {
                shoppingRepository.observeShoppingList(mealPlan.id).collect { emit(it) }
            } else {
                emit(null)
            }
        }
    }

    /**
     * Observe shopping list for a specific meal plan
     */
    fun observeShoppingList(mealPlanId: Long): Flow<ShoppingList?> {
        return shoppingRepository.observeShoppingList(mealPlanId)
    }

    /**
     * Generate shopping list for a meal plan
     */
    suspend fun generateShoppingList(mealPlanId: Long): Result<ShoppingList> {
        return shoppingRepository.generateShoppingList(mealPlanId)
    }

    /**
     * Add a custom item to the shopping list
     */
    suspend fun addItem(
        mealPlanId: Long,
        name: String,
        quantity: Double,
        unit: String,
        category: String
    ): Long {
        return shoppingRepository.addItem(mealPlanId, name, quantity, unit, category)
    }

    /**
     * Delete an item
     */
    suspend fun deleteItem(itemId: Long) {
        shoppingRepository.deleteItem(itemId)
    }

    /**
     * Toggle an item's checked state
     */
    suspend fun toggleItemChecked(itemId: Long) {
        shoppingRepository.toggleItemChecked(itemId)
    }

    /**
     * Toggle an item's in-cart state
     */
    suspend fun toggleItemInCart(itemId: Long) {
        shoppingRepository.toggleItemInCart(itemId)
    }

    /**
     * Reset all items to unchecked
     */
    suspend fun resetAllItems(mealPlanId: Long) {
        shoppingRepository.resetAllItems(mealPlanId)
    }

    /**
     * Get count of unchecked items
     */
    fun observeUncheckedCount(mealPlanId: Long): Flow<Int> {
        return shoppingRepository.observeUncheckedCount(mealPlanId)
    }

    /**
     * Complete shopping trip - move checked items to pantry
     */
    suspend fun completeShoppingTrip(mealPlanId: Long): Int {
        val checkedItems = shoppingRepository.getCheckedItems(mealPlanId)

        val pantryItems = checkedItems.map { item ->
            PantryItem(
                name = item.name,
                quantityInitial = item.quantity,
                quantityRemaining = item.quantity,
                unit = mapUnitToPantry(item.unit),
                category = mapCategoryToPantry(item.category),
                perishable = isPerishableCategory(item.category),
                expiryDate = if (isPerishableCategory(item.category)) {
                    LocalDate.now().plusDays(7) // Default 7 day expiry for perishables
                } else null,
                dateAdded = LocalDateTime.now(),
                lastUpdated = LocalDateTime.now()
            )
        }

        if (pantryItems.isNotEmpty()) {
            pantryRepository.addFromShoppingList(pantryItems)
        }

        // Reset all items after completing trip
        shoppingRepository.resetAllItems(mealPlanId)

        return pantryItems.size
    }

    private fun mapUnitToPantry(unit: String): PantryUnit {
        return when (unit.lowercase()) {
            "g", "grams", "gram" -> PantryUnit.GRAMS
            "ml", "milliliters", "milliliter" -> PantryUnit.MILLILITERS
            "bunch", "bunches" -> PantryUnit.BUNCH
            else -> PantryUnit.UNITS
        }
    }

    private fun mapCategoryToPantry(category: String): PantryCategory {
        return when (category) {
            ShoppingCategories.PRODUCE -> PantryCategory.PRODUCE
            ShoppingCategories.PROTEIN -> PantryCategory.PROTEIN
            ShoppingCategories.DAIRY -> PantryCategory.DAIRY
            ShoppingCategories.PANTRY -> PantryCategory.DRY_GOODS
            ShoppingCategories.FROZEN -> PantryCategory.FROZEN
            ShoppingCategories.CONDIMENTS -> PantryCategory.CONDIMENT
            ShoppingCategories.SPICES -> PantryCategory.SPICE
            else -> PantryCategory.OTHER
        }
    }

    private fun isPerishableCategory(category: String): Boolean {
        return category in listOf(
            ShoppingCategories.PRODUCE,
            ShoppingCategories.PROTEIN,
            ShoppingCategories.DAIRY,
            ShoppingCategories.BAKERY
        )
    }
}
