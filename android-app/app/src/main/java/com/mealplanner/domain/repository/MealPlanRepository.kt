package com.mealplanner.domain.repository

import com.mealplanner.domain.model.CookingStep
import com.mealplanner.domain.model.MealPlan
import com.mealplanner.domain.model.PlannedRecipe
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeCustomizationResult
import com.mealplanner.domain.model.RecipeHistory
import com.mealplanner.domain.model.RecipeIngredient
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
     * Falls back to rule-based logic if AI is not available.
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

    /**
     * Update a recipe with AI-determined substitution values.
     * Used when the AI has processed a substitution and returned updated recipe name,
     * ingredient name, quantity, unit, preparation style, and updated cooking steps.
     *
     * @param plannedRecipeId The ID of the planned recipe to update
     * @param ingredientIndex The index of the ingredient in the recipe's ingredients list
     * @param newRecipeName The AI-determined new recipe name
     * @param newIngredientName The new ingredient name
     * @param newQuantity The AI-adjusted ingredient quantity
     * @param newUnit The AI-adjusted ingredient unit
     * @param newPreparation The AI-adjusted preparation style (null to remove)
     * @param newSteps The AI-updated cooking steps (may have instruction changes)
     */
    suspend fun updateRecipeWithSubstitution(
        plannedRecipeId: Long,
        ingredientIndex: Int,
        newRecipeName: String,
        newIngredientName: String,
        newQuantity: Double,
        newUnit: String,
        newPreparation: String?,
        newSteps: List<CookingStep>
    ): Result<Unit>

    /**
     * Request AI-powered recipe customization.
     * Returns proposed changes for user review.
     *
     * @param recipe The recipe to customize
     * @param customizationRequest Free-form text describing desired changes
     * @param previousRequests Previous requests in this session (for refine loop)
     */
    suspend fun requestRecipeCustomization(
        recipe: Recipe,
        customizationRequest: String,
        previousRequests: List<String> = emptyList()
    ): Result<RecipeCustomizationResult>

    /**
     * Apply a customization result to a planned recipe.
     * Updates the recipe in the database.
     *
     * @param plannedRecipeId The ID of the planned recipe to update
     * @param customization The customization result to apply
     * @param originalIngredients The original ingredients (needed to compute final list)
     */
    suspend fun applyRecipeCustomization(
        plannedRecipeId: Long,
        customization: RecipeCustomizationResult,
        originalIngredients: List<RecipeIngredient>
    ): Result<Unit>
}
