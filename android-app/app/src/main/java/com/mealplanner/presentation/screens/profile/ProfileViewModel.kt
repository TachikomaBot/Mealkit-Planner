package com.mealplanner.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealplanner.domain.model.MealPlan
import com.mealplanner.domain.model.RecipeHistory
import com.mealplanner.domain.model.UserPreferences
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.PantryRepository
import com.mealplanner.domain.usecase.ManagePreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesUseCase: ManagePreferencesUseCase,
    private val mealPlanRepository: MealPlanRepository,
    private val pantryRepository: PantryRepository
) : ViewModel() {

    private val _preferences = MutableStateFlow(UserPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    private val _selectedTab = MutableStateFlow(ProfileTab.PREFERENCES)
    val selectedTab: StateFlow<ProfileTab> = _selectedTab.asStateFlow()

    // Stats data
    private val _stats = MutableStateFlow(ProfileStats())
    val stats: StateFlow<ProfileStats> = _stats.asStateFlow()

    // History data
    private val _mealPlanHistory = MutableStateFlow<List<MealPlan>>(emptyList())
    val mealPlanHistory: StateFlow<List<MealPlan>> = _mealPlanHistory.asStateFlow()

    private val _recipeHistory = MutableStateFlow<List<RecipeHistory>>(emptyList())
    val recipeHistory: StateFlow<List<RecipeHistory>> = _recipeHistory.asStateFlow()

    init {
        observePreferences()
        observeStats()
        observeHistory()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesUseCase.observePreferences()
                .collect { prefs ->
                    _preferences.value = prefs
                }
        }
    }

    private fun observeStats() {
        viewModelScope.launch {
            combine(
                mealPlanRepository.observeTotalMealPlansCount(),
                mealPlanRepository.observeTotalCookedCount(),
                mealPlanRepository.observeAverageRating(),
                pantryRepository.observeItemCount()
            ) { mealPlans, cooked, avgRating, pantryItems ->
                ProfileStats(
                    totalMealPlans = mealPlans,
                    totalRecipesCooked = cooked,
                    averageRating = avgRating,
                    pantryItems = pantryItems
                )
            }.collect { _stats.value = it }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            mealPlanRepository.observeAllMealPlans()
                .collect { _mealPlanHistory.value = it }
        }
        viewModelScope.launch {
            mealPlanRepository.observeAllRecipeHistory()
                .collect { _recipeHistory.value = it }
        }
    }

    fun selectTab(tab: ProfileTab) {
        _selectedTab.value = tab
    }

    fun updateApiKey(apiKey: String) {
        viewModelScope.launch {
            preferencesUseCase.updateApiKey(apiKey.takeIf { it.isNotBlank() })
            showSaved()
        }
    }

    fun updateTargetServings(servings: Int) {
        viewModelScope.launch {
            preferencesUseCase.updateTargetServings(servings)
            showSaved()
        }
    }

    fun toggleTheme(isDarkMode: Boolean?) {
        viewModelScope.launch {
            preferencesUseCase.updateIsDarkMode(isDarkMode)
        }
    }

    fun addLike(ingredient: String) {
        viewModelScope.launch {
            preferencesUseCase.addLike(ingredient.trim())
            showSaved()
        }
    }

    fun removeLike(ingredient: String) {
        viewModelScope.launch {
            preferencesUseCase.removeLike(ingredient)
        }
    }

    fun addDislike(ingredient: String) {
        viewModelScope.launch {
            preferencesUseCase.addDislike(ingredient.trim())
            showSaved()
        }
    }

    fun removeDislike(ingredient: String) {
        viewModelScope.launch {
            preferencesUseCase.removeDislike(ingredient)
        }
    }

    private fun showSaved() {
        viewModelScope.launch {
            _saveStatus.value = SaveStatus.Saved
            kotlinx.coroutines.delay(2000)
            _saveStatus.value = SaveStatus.Idle
        }
    }
}

enum class ProfileTab {
    PREFERENCES,
    HISTORY,
    STATS
}

data class ProfileStats(
    val totalMealPlans: Int = 0,
    val totalRecipesCooked: Int = 0,
    val averageRating: Double? = null,
    val pantryItems: Int = 0
)

sealed class SaveStatus {
    data object Idle : SaveStatus()
    data object Saved : SaveStatus()
}
