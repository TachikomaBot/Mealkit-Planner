package com.mealplanner.domain.repository

import com.mealplanner.domain.model.PreferenceSummary
import com.mealplanner.domain.model.User
import com.mealplanner.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Repository for user data and preferences.
 *
 * This interface is designed to support both local-only storage
 * and future Firebase authentication/sync. Implementations can be
 * swapped via Hilt to enable cloud sync when ready.
 */
interface UserRepository {

    /**
     * Get the current user (local or authenticated)
     */
    suspend fun getCurrentUser(): User

    /**
     * Observe user preferences
     */
    fun observePreferences(): Flow<UserPreferences>

    /**
     * Get current preferences
     */
    suspend fun getPreferences(): UserPreferences

    /**
     * Update user preferences
     */
    suspend fun updatePreferences(preferences: UserPreferences)

    /**
     * Update just the likes list
     */
    suspend fun updateLikes(likes: List<String>)

    /**
     * Update just the dislikes list
     */
    suspend fun updateDislikes(dislikes: List<String>)

    /**
     * Update the Gemini API key
     */
    suspend fun updateApiKey(apiKey: String?)

    /**
     * Update target servings
     */
    suspend fun updateTargetServings(servings: Int)

    /**
     * Get the preference summary (compacted history)
     */
    suspend fun getPreferenceSummary(): PreferenceSummary?

    /**
     * Save a new preference summary
     */
    suspend fun savePreferenceSummary(summary: PreferenceSummary)

    /**
     * Sync data to cloud (no-op for local, real sync for Firebase)
     */
    suspend fun syncToCloud(): Result<Unit>

    /**
     * Check if user is signed in (always true for local)
     */
    suspend fun isSignedIn(): Boolean

    /**
     * Sign out (no-op for local)
     */
    suspend fun signOut()
}
