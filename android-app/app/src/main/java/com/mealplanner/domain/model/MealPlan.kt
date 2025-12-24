package com.mealplanner.domain.model

/**
 * A meal plan containing a flat list of planned recipes
 */
data class MealPlan(
    val id: Long,
    val recipes: List<PlannedRecipe>,
    val createdAt: Long,
    val shoppingComplete: Boolean = false
) {
    val totalRecipes: Int get() = recipes.size
    val cookedCount: Int get() = recipes.count { it.cooked }
}

/**
 * A recipe that has been added to a meal plan
 */
data class PlannedRecipe(
    val id: Long,
    val recipe: Recipe,
    val sequenceIndex: Int = 0,
    val cooked: Boolean = false,
    val cookedAt: Long? = null
)

/**
 * Progress during meal plan generation
 */
data class GenerationProgress(
    val phase: GenerationPhase,
    val current: Int,
    val total: Int,
    val message: String? = null
)

enum class GenerationPhase {
    CONNECTING,
    PLANNING,
    BUILDING,
    GENERATING_IMAGES,
    COMPLETE,
    ERROR
}

/**
 * Result of meal plan generation
 */
data class GeneratedMealPlan(
    val recipes: List<Recipe>,
    val defaultSelections: List<Int>
)