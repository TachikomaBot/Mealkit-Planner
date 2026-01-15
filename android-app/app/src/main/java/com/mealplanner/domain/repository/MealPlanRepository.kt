package com.mealplanner.domain.repository

import com.mealplanner.domain.model.MealPlan
import com.mealplanner.domain.model.PlannedRecipe
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeHistory
import kotlinx.coroutines.flow.Flow

/**
 * Repository for meal plan persistence and management
 */
interface MealPlanRepository {

    /**
     * Get the current/latest meal plan
     */
    fun observeCurrentMealPlan(): Flow<MealPlan?>

    /**
     * Get the current/latest meal plan (one-shot)
     */
    suspend fun getCurrentMealPlan(): MealPlan?

    /**
     * Save a new meal plan with selected recipes
     */
    suspend fun saveMealPlan(recipes: List<Recipe>): Long

    /**
     * Mark a recipe as cooked
     */
    suspend fun markRecipeCooked(plannedRecipeId: Long): Result<Unit>

    /**
     * Unmark a recipe as cooked
     */
    suspend fun unmarkRecipeCooked(plannedRecipeId: Long)

    /**
     * Delete a meal plan
     */
    suspend fun deleteMealPlan(mealPlanId: Long)

    /**
     * Mark shopping as complete for a meal plan
     */
    suspend fun markShoppingComplete(mealPlanId: Long)

    /**
     * Get recent recipe hashes to avoid repetition
     */
    suspend fun getRecentRecipeHashes(weeksBack: Int = 3): List<String>

    /**
     * Record recipe history when cooked
     */
    suspend fun recordHistory(
        recipeName: String,
        recipeHash: String
    ): Long

    /**
     * Update recipe rating
     */
    suspend fun rateRecipe(
        historyId: Long,
        rating: Int?,
        wouldMakeAgain: Boolean?,
        notes: String?
    )

    /**
     * Get history for a recipe by name
     */
    suspend fun getRecipeHistory(recipeName: String): RecipeHistory?

    /**
     * Observe all meal plans for history view
     */
    fun observeAllMealPlans(): Flow<List<MealPlan>>

    /**
     * Observe all recipe history
     */
    fun observeAllRecipeHistory(): Flow<List<RecipeHistory>>

    /**
     * Observe total cooked recipes count
     */
    fun observeTotalCookedCount(): Flow<Int>

    /**
     * Observe total meal plans count
     */
    fun observeTotalMealPlansCount(): Flow<Int>

    /**
     * Observe average recipe rating
     */
    fun observeAverageRating(): Flow<Double?>

    /**
     * Clear all meal plan data (plans, recipes, history)
     */
    suspend fun clearAll()

    /**
     * Update an ingredient name in a recipe.
     * Used to propagate ingredient substitutions from shopping list back to recipes.
     *
     * @param plannedRecipeId The ID of the planned recipe to update
     * @param ingredientIndex The index of the ingredient in the recipe's ingredients list
     * @param newName The new ingredient name
     */
    suspend fun updateRecipeIngredient(
        plannedRecipeId: Long,
        ingredientIndex: Int,
        newName: String
    ): Result<Unit>
}
