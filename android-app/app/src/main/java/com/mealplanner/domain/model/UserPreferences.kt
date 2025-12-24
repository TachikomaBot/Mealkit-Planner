package com.mealplanner.domain.model

/**
 * User preferences for meal planning
 */
data class UserPreferences(
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val targetServings: Int = 2,
    val geminiApiKey: String? = null
) {
    val hasApiKey: Boolean get() = !geminiApiKey.isNullOrBlank()

    val hasPreferences: Boolean get() = likes.isNotEmpty() || dislikes.isNotEmpty()
}

/**
 * Compacted preference summary from recipe history
 */
data class PreferenceSummary(
    val summary: String,
    val likes: List<String>,
    val dislikes: List<String>,
    val lastUpdated: Long,
    val entriesProcessed: Int
)

/**
 * Recipe history entry for tracking what was cooked and ratings
 */
data class RecipeHistory(
    val id: Long,
    val recipeName: String,
    val recipeHash: String,
    val cookedAt: Long,
    val rating: Int?, // 1-5
    val wouldMakeAgain: Boolean?,
    val notes: String?
)

/**
 * User profile (placeholder for future Firebase auth)
 */
data class User(
    val id: String,
    val displayName: String?,
    val email: String?,
    val isAnonymous: Boolean = true
) {
    companion object {
        fun local() = User(
            id = "local",
            displayName = null,
            email = null,
            isAnonymous = true
        )
    }
}
