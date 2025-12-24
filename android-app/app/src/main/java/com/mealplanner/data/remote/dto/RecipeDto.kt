package com.mealplanner.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RecipeSearchResponse(
    val count: Int,
    val total: Int,
    val results: List<RecipeDto>
)

@Serializable
data class RecipeStatsResponse(
    val total: Int,
    val categories: Map<String, Int>,
    val cuisines: Map<String, Int>
)

@Serializable
data class RecipeDto(
    val sourceId: Int,
    val name: String,
    val description: String,
    val servings: Int,
    val totalTimeMinutes: Int? = null,
    val ingredients: List<IngredientDto>,
    val steps: List<String>,
    val tags: List<String>,
    val category: String? = null,
    val cuisines: List<String> = emptyList(),
    val dietaryFlags: List<String> = emptyList(),
    val score: Double? = null,
    val matchedIngredients: List<String>? = null
)

@Serializable
data class IngredientDto(
    val quantity: Double? = null,
    val unit: String? = null,
    val name: String,
    val rawName: String? = null,
    val preparation: String? = null
)
