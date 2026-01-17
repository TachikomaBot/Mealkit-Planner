package com.mealplanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks pending async jobs (Gemini API calls) so they can be resumed if the app
 * goes to background or the process is killed.
 */
@Entity(tableName = "pending_jobs")
data class PendingJobEntity(
    @PrimaryKey
    val jobId: String,
    val jobType: String,  // GROCERY_POLISH, PANTRY_CATEGORIZE, etc.
    val relatedId: Long,  // mealPlanId or other context-specific ID
    val startedAt: Long = System.currentTimeMillis()
)

/**
 * Job types for pending async operations.
 */
object PendingJobType {
    const val MEAL_GENERATION = "MEAL_GENERATION"
    const val GROCERY_POLISH = "GROCERY_POLISH"
    const val PANTRY_CATEGORIZE = "PANTRY_CATEGORIZE"
}
