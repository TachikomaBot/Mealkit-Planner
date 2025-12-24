package com.mealplanner.domain.usecase

import com.mealplanner.domain.model.RecipeHistory
import com.mealplanner.domain.model.UserPreferences
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for managing user preferences and recipe ratings
 */
class ManagePreferencesUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val mealPlanRepository: MealPlanRepository
) {
    /**
     * Observe user preferences
     */
    fun observePreferences(): Flow<UserPreferences> {
        return userRepository.observePreferences()
    }

    /**
     * Get current preferences
     */
    suspend fun getPreferences(): UserPreferences {
        return userRepository.getPreferences()
    }

    /**
     * Add an ingredient to likes
     */
    suspend fun addLike(ingredient: String) {
        val current = userRepository.getPreferences()
        if (!current.likes.contains(ingredient)) {
            userRepository.updateLikes(current.likes + ingredient)
            // Remove from dislikes if present
            if (current.dislikes.contains(ingredient)) {
                userRepository.updateDislikes(current.dislikes - ingredient)
            }
        }
    }

    /**
     * Remove an ingredient from likes
     */
    suspend fun removeLike(ingredient: String) {
        val current = userRepository.getPreferences()
        userRepository.updateLikes(current.likes - ingredient)
    }

    /**
     * Add an ingredient to dislikes
     */
    suspend fun addDislike(ingredient: String) {
        val current = userRepository.getPreferences()
        if (!current.dislikes.contains(ingredient)) {
            userRepository.updateDislikes(current.dislikes + ingredient)
            // Remove from likes if present
            if (current.likes.contains(ingredient)) {
                userRepository.updateLikes(current.likes - ingredient)
            }
        }
    }

    /**
     * Remove an ingredient from dislikes
     */
    suspend fun removeDislike(ingredient: String) {
        val current = userRepository.getPreferences()
        userRepository.updateDislikes(current.dislikes - ingredient)
    }

    /**
     * Update the Gemini API key
     */
    suspend fun updateApiKey(apiKey: String?) {
        userRepository.updateApiKey(apiKey?.trim()?.takeIf { it.isNotEmpty() })
    }

    /**
     * Update target servings
     */
    suspend fun updateTargetServings(servings: Int) {
        userRepository.updateTargetServings(servings.coerceIn(1, 12))
    }

    /**
     * Rate a recipe after cooking
     */
    suspend fun rateRecipe(
        recipeName: String,
        rating: Int?,
        wouldMakeAgain: Boolean?,
        notes: String? = null
    ) {
        val history = mealPlanRepository.getRecipeHistory(recipeName)
        if (history != null) {
            mealPlanRepository.rateRecipe(history.id, rating, wouldMakeAgain, notes)
        }
    }

    /**
     * Get rating for a recipe
     */
    suspend fun getRecipeRating(recipeName: String): RecipeHistory? {
        return mealPlanRepository.getRecipeHistory(recipeName)
    }

    /**
     * Check if API key is configured
     */
    suspend fun hasApiKey(): Boolean {
        return userRepository.getPreferences().hasApiKey
    }

    /**
     * Get the Gemini API key
     */
    suspend fun getApiKey(): String? {
        return userRepository.getPreferences().geminiApiKey
    }
}
