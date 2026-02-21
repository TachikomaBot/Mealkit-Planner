package com.mealplanner.domain.usecase

import com.mealplanner.domain.model.ShoppingList
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.ShoppingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for managing shopping lists
 */
class ManageShoppingListUseCase @Inject constructor(
    private val shoppingRepository: ShoppingRepository,
    private val mealPlanRepository: MealPlanRepository
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
}
