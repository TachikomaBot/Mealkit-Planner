package com.mealplanner.domain.usecase

import com.mealplanner.domain.model.RecipeSearchResult
import com.mealplanner.domain.repository.RecipeRepository
import com.mealplanner.domain.repository.RecipeStats
import javax.inject.Inject

/**
 * Use case for searching and browsing recipes
 */
class SearchRecipesUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository
) {
    /**
     * Search recipes with filters
     */
    suspend fun search(
        query: String? = null,
        category: String? = null,
        cuisines: List<String>? = null,
        maxTime: Int? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<RecipeSearchResult>> {
        return recipeRepository.searchRecipes(
            query = query,
            category = category,
            cuisines = cuisines,
            maxTime = maxTime,
            limit = limit,
            offset = offset,
            random = false
        )
    }

    /**
     * Get random recipe suggestions
     */
    suspend fun getRandomSuggestions(
        category: String? = null,
        limit: Int = 10
    ): Result<List<RecipeSearchResult>> {
        return recipeRepository.searchRecipes(
            category = category,
            limit = limit,
            random = true
        )
    }

    /**
     * Get recipe statistics for filters
     */
    suspend fun getStats(): Result<RecipeStats> {
        return recipeRepository.getStats()
    }

    /**
     * Quick search by text only
     */
    suspend fun quickSearch(query: String): Result<List<RecipeSearchResult>> {
        return search(query = query, limit = 10)
    }
}
