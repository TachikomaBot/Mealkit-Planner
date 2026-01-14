package com.mealplanner.domain.usecase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.PantryRepository
import com.mealplanner.domain.repository.ShoppingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case for managing Test Mode.
 *
 * Test Mode provides isolated data for testing features like pantry sync
 * without affecting real user data. When enabled, a banner is shown
 * indicating the app is in test mode.
 */
class TestModeUseCase @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val mealPlanRepository: MealPlanRepository,
    private val pantryRepository: PantryRepository,
    private val shoppingRepository: ShoppingRepository
) {
    companion object {
        private val TEST_MODE_KEY = booleanPreferencesKey("test_mode_enabled")
    }

    /**
     * Observe whether test mode is enabled
     */
    fun observeTestMode(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[TEST_MODE_KEY] ?: false
        }
    }

    /**
     * Enable test mode - clears all data first
     */
    suspend fun enableTestMode(): Result<Unit> {
        return try {
            // Clear all data before entering test mode
            clearAllData()

            // Set test mode flag
            dataStore.edit { preferences ->
                preferences[TEST_MODE_KEY] = true
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Disable test mode - clears test data
     */
    suspend fun disableTestMode(): Result<Unit> {
        return try {
            // Clear test data
            clearAllData()

            // Clear test mode flag
            dataStore.edit { preferences ->
                preferences[TEST_MODE_KEY] = false
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clear all data from the database
     */
    suspend fun clearAllData(): Result<Unit> {
        return try {
            pantryRepository.clearAll()
            shoppingRepository.clearAll()
            mealPlanRepository.clearAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
