package com.mealplanner.domain.model

/**
 * A recipe with full details for display and cooking
 */
data class Recipe(
    val id: String,
    val name: String,
    val description: String,
    val servings: Int,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val ingredients: List<RecipeIngredient>,
    val steps: List<CookingStep>,
    val tags: List<String>,
    val imageUrl: String? = null,
    val sourceRecipeIds: List<Int> = emptyList()
) {
    val totalTimeMinutes: Int get() = prepTimeMinutes + cookTimeMinutes
}

/**
 * An ingredient in a recipe with quantity and preparation instructions
 */
data class RecipeIngredient(
    val name: String,
    val quantity: Double,
    val unit: String,
    val preparation: String? = null
)

/**
 * A cooking step with a title and substeps
 */
data class CookingStep(
    val title: String,
    val substeps: List<String>
)

/**
 * A recipe from the search results (lighter weight than full Recipe)
 */
data class RecipeSearchResult(
    val sourceId: Int,
    val name: String,
    val description: String,
    val servings: Int,
    val totalTimeMinutes: Int?,
    val tags: List<String>,
    val cuisines: List<String>,
    val category: String?,
    val score: Double? = null
)