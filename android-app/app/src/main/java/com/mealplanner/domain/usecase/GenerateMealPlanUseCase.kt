package com.mealplanner.domain.usecase

import com.mealplanner.domain.model.GeneratedMealPlan
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.repository.GenerationResult
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.RecipeRepository
import com.mealplanner.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for generating a new meal plan.
 * Orchestrates recipe generation and meal plan saving.
 */
class GenerateMealPlanUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val mealPlanRepository: MealPlanRepository,
    private val userRepository: UserRepository
) {
    /**
     * Generate a meal plan using AI.
     * Returns a Flow of progress updates and final result.
     */
    fun generateWithAI(leftoversInput: String = ""): Flow<GenerationResult> {
        return kotlinx.coroutines.flow.flow {
            // Get user preferences
            val preferences = userRepository.getPreferences()

            if (!preferences.hasApiKey) {
                emit(GenerationResult.Error("Gemini API key not configured. Go to Profile to add your key."))
                return@flow
            }

            // Get recent recipe hashes to avoid repetition
            val recentHashes = mealPlanRepository.getRecentRecipeHashes(weeksBack = 3)

            // Delegate to repository which handles SSE streaming
            recipeRepository.generateMealPlan(preferences, recentHashes, leftoversInput)
                .collect { result ->
                    emit(result)
                }
        }
    }

    /**
     * Generate a simple meal plan from the dataset (no AI required)
     */
    suspend fun generateSimple(): Result<GeneratedMealPlan> {
        return recipeRepository.generateSimpleMealPlan()
    }

    /**
     * Save the generated meal plan with selected recipes
     */
    suspend fun saveMealPlan(
        recipes: List<Recipe>,
        selectedIndices: List<Int>
    ): Result<Long> {
        return try {
            val selectedRecipes = selectedIndices.map { recipes[it] }
            val mealPlanId = mealPlanRepository.saveMealPlan(selectedRecipes)
            Result.success(mealPlanId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
