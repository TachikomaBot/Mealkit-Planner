package com.mealplanner.data.remote.api

import com.mealplanner.data.remote.dto.RecipeDto
import com.mealplanner.data.remote.dto.RecipeSearchResponse
import com.mealplanner.data.remote.dto.RecipeStatsResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RecipeApi {

    @GET("api/recipes")
    suspend fun searchRecipes(
        @Query("q") query: String? = null,
        @Query("category") category: String? = null,
        @Query("cuisines") cuisines: String? = null,
        @Query("dietaryFlags") dietaryFlags: String? = null,
        @Query("maxTotalTime") maxTotalTime: Int? = null,
        @Query("includeIngredients") includeIngredients: String? = null,
        @Query("excludeIngredients") excludeIngredients: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("random") random: Boolean = false
    ): RecipeSearchResponse

    @GET("api/recipes/stats")
    suspend fun getStats(): RecipeStatsResponse

    @GET("api/recipes/{id}")
    suspend fun getRecipeById(@Path("id") id: Int): RecipeDto

    @GET("api/recipes/source/{sourceId}")
    suspend fun getRecipeBySourceId(@Path("sourceId") sourceId: Int): RecipeDto
}
