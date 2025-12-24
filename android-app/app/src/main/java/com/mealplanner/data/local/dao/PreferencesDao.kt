package com.mealplanner.data.local.dao

import androidx.room.*
import com.mealplanner.data.local.entity.PreferenceSummaryEntity
import com.mealplanner.data.local.entity.UserPreferencesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferencesDao {

    // User Preferences
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreferences(preferences: UserPreferencesEntity)

    @Query("SELECT * FROM user_preferences WHERE id = 1")
    suspend fun getPreferences(): UserPreferencesEntity?

    @Query("SELECT * FROM user_preferences WHERE id = 1")
    fun observePreferences(): Flow<UserPreferencesEntity?>

    @Query("UPDATE user_preferences SET likesJson = :likes, dislikesJson = :dislikes WHERE id = 1")
    suspend fun updateLikesDislikes(likes: String, dislikes: String)

    @Query("UPDATE user_preferences SET geminiApiKey = :key WHERE id = 1")
    suspend fun updateApiKey(key: String?)

    @Query("UPDATE user_preferences SET targetServings = :servings WHERE id = 1")
    suspend fun updateTargetServings(servings: Int)

    // Preference Summary (compacted history)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: PreferenceSummaryEntity)

    @Query("SELECT * FROM preference_summary WHERE id = 1")
    suspend fun getSummary(): PreferenceSummaryEntity?

    @Query("DELETE FROM preference_summary")
    suspend fun clearSummary()
}
