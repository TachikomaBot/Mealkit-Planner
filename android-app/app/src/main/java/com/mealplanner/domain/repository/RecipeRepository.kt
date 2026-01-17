package com.mealplanner.domain.repository

import com.mealplanner.domain.model.GeneratedMealPlan
import com.mealplanner.domain.model.GenerationProgress
import com.mealplanner.domain.model.PantryItem
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeSearchResult
import com.mealplanner.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Repository for recipe operations - search, generation, etc.
 */
interface RecipeRepository {

    /**
     * Search recipes from the backend API
     */
    suspend fun searchRecipes(
        query: String? = null,
        category: String? = null,
        cuisines: List<String>? = null,
        maxTime: Int? = null,
        limit: Int = 20,
        offset: Int = 0,
        random: Boolean = false
    ): Result<List<RecipeSearchResult>>

    /**
     * Get recipe statistics (categories, cuisines, counts)
     */
    suspend fun getStats(): Result<RecipeStats>

    /**
     * Get a specific recipe by ID
     */
    suspend fun getRecipeById(id: Int): Result<Recipe>

    /**
     * Generate a new meal plan using AI
     * Returns a Flow to support progress updates via SSE
     */
    fun generateMealPlan(
        preferences: UserPreferences,
        pantryItems: List<PantryItem>,
        recentRecipeHashes: List<String>
    ): Flow<GenerationResult>

    /**
     * Generate a simple meal plan without AI (from dataset)
     */
    suspend fun generateSimpleMealPlan(): Result<GeneratedMealPlan>

    /**
     * Check if there's a pending meal generation job and resume polling if so.
     * Call this when app resumes from background.
     * @return Flow of generation results, or null if no pending job
     */
    fun checkAndResumePendingGeneration(): Flow<GenerationResult>?
}

/**
 * Result type for meal plan generation with progress updates
 */
sealed class GenerationResult {
    data class Progress(val progress: GenerationProgress) : GenerationResult()
    data class Success(val mealPlan: GeneratedMealPlan) : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}

/**
 * Recipe database statistics
 */
data class RecipeStats(
    val total: Int,
    val categories: Map<String, Int>,
    val cuisines: Map<String, Int>
)
