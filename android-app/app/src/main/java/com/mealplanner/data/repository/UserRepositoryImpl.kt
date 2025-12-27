package com.mealplanner.data.repository

import com.mealplanner.data.local.dao.PreferencesDao
import com.mealplanner.data.local.entity.PreferenceSummaryEntity
import com.mealplanner.data.local.entity.UserPreferencesEntity
import com.mealplanner.domain.model.PreferenceSummary
import com.mealplanner.domain.model.User
import com.mealplanner.domain.model.UserPreferences
import com.mealplanner.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-only implementation of UserRepository.
 *
 * This implementation stores all data locally using Room.
 * It's designed to be easily swapped with a Firebase implementation
 * when cloud sync is needed - just implement UserRepository with
 * Firebase calls and bind it in the Hilt module instead.
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val preferencesDao: PreferencesDao,
    private val json: Json
) : UserRepository {

    override suspend fun getCurrentUser(): User = User.local()

    override fun observePreferences(): Flow<UserPreferences> {
        return preferencesDao.observePreferences().map { entity ->
            entity?.toUserPreferences() ?: UserPreferences()
        }
    }

    override suspend fun getPreferences(): UserPreferences = withContext(Dispatchers.IO) {
        preferencesDao.getPreferences()?.toUserPreferences() ?: run {
            // Create default preferences if none exist
            val default = UserPreferencesEntity()
            preferencesDao.insertPreferences(default)
            UserPreferences()
        }
    }

    override suspend fun updatePreferences(preferences: UserPreferences) = withContext(Dispatchers.IO) {
        preferencesDao.insertPreferences(
            UserPreferencesEntity(
                likesJson = json.encodeToString(preferences.likes),
                dislikesJson = json.encodeToString(preferences.dislikes),
                targetServings = preferences.targetServings,
                geminiApiKey = preferences.geminiApiKey,
                isDarkMode = preferences.isDarkMode
            )
        )
    }

    override suspend fun updateLikes(likes: List<String>) = withContext(Dispatchers.IO) {
        val current = preferencesDao.getPreferences() ?: UserPreferencesEntity()
        val likesJson = json.encodeToString(likes)
        preferencesDao.updateLikesDislikes(likesJson, current.dislikesJson)
    }

    override suspend fun updateDislikes(dislikes: List<String>) = withContext(Dispatchers.IO) {
        val current = preferencesDao.getPreferences() ?: UserPreferencesEntity()
        val dislikesJson = json.encodeToString(dislikes)
        preferencesDao.updateLikesDislikes(current.likesJson, dislikesJson)
    }

    override suspend fun updateApiKey(apiKey: String?) = withContext(Dispatchers.IO) {
        // Ensure preferences exist
        if (preferencesDao.getPreferences() == null) {
            preferencesDao.insertPreferences(UserPreferencesEntity())
        }
        preferencesDao.updateApiKey(apiKey)
    }

    override suspend fun updateTargetServings(servings: Int) = withContext(Dispatchers.IO) {
        // Ensure preferences exist
        if (preferencesDao.getPreferences() == null) {
            preferencesDao.insertPreferences(UserPreferencesEntity())
        }
        preferencesDao.updateTargetServings(servings)
    }

    override suspend fun updateIsDarkMode(isDarkMode: Boolean?) = withContext(Dispatchers.IO) {
        val current = preferencesDao.getPreferences() ?: UserPreferencesEntity()
        preferencesDao.insertPreferences(current.copy(isDarkMode = isDarkMode))
    }

    override suspend fun getPreferenceSummary(): PreferenceSummary? = withContext(Dispatchers.IO) {
        preferencesDao.getSummary()?.toPreferenceSummary()
    }

    override suspend fun savePreferenceSummary(summary: PreferenceSummary) = withContext(Dispatchers.IO) {
        preferencesDao.insertSummary(
            PreferenceSummaryEntity(
                summary = summary.summary,
                likesJson = json.encodeToString(summary.likes),
                dislikesJson = json.encodeToString(summary.dislikes),
                lastUpdated = summary.lastUpdated,
                entriesProcessed = summary.entriesProcessed
            )
        )
    }

    override suspend fun syncToCloud(): Result<Unit> {
        // No-op for local implementation
        // Firebase implementation would sync data here
        return Result.success(Unit)
    }

    override suspend fun isSignedIn(): Boolean {
        // Always signed in for local implementation
        return true
    }

    override suspend fun signOut() {
        // No-op for local implementation
        // Firebase implementation would sign out here
    }

    // Mappers
    private fun UserPreferencesEntity.toUserPreferences(): UserPreferences {
        val likes: List<String> = try {
            json.decodeFromString(likesJson)
        } catch (e: Exception) {
            emptyList()
        }

        val dislikes: List<String> = try {
            json.decodeFromString(dislikesJson)
        } catch (e: Exception) {
            emptyList()
        }

        return UserPreferences(
            likes = likes,
            dislikes = dislikes,
            targetServings = targetServings,
            geminiApiKey = geminiApiKey,
            isDarkMode = isDarkMode
        )
    }

    private fun PreferenceSummaryEntity.toPreferenceSummary(): PreferenceSummary {
        val likes: List<String> = try {
            json.decodeFromString(likesJson)
        } catch (e: Exception) {
            emptyList()
        }

        val dislikes: List<String> = try {
            json.decodeFromString(dislikesJson)
        } catch (e: Exception) {
            emptyList()
        }

        return PreferenceSummary(
            summary = summary,
            likes = likes,
            dislikes = dislikes,
            lastUpdated = lastUpdated,
            entriesProcessed = entriesProcessed
        )
    }
}
