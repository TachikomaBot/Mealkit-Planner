package com.mealplanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey
    val id: Int = 1, // Single row
    val likesJson: String = "[]", // JSON array of strings
    val dislikesJson: String = "[]",
    val summaryText: String? = null,
    val targetServings: Int = 2,
    val geminiApiKey: String? = null,
    val isDarkMode: Boolean? = null
)

@Entity(tableName = "preference_summary")
data class PreferenceSummaryEntity(
    @PrimaryKey
    val id: Int = 1,
    val summary: String,
    val likesJson: String,
    val dislikesJson: String,
    val lastUpdated: Long,
    val entriesProcessed: Int
)
