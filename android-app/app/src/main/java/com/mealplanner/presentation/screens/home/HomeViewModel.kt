package com.mealplanner.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealplanner.domain.model.MealPlan
import com.mealplanner.domain.model.PlannedRecipe
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.PantryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mealPlanRepository: MealPlanRepository,
    private val pantryRepository: PantryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeMealPlan()
        observePantryCount()
    }

    private fun observeMealPlan() {
        viewModelScope.launch {
            mealPlanRepository.observeCurrentMealPlan()
                .collect { mealPlan ->
                    _uiState.update { it.copy(mealPlan = mealPlan) }
                }
        }
    }

    private fun observePantryCount() {
        viewModelScope.launch {
            pantryRepository.observeItemCount()
                .collect { count ->
                    _uiState.update { it.copy(pantryItemCount = count) }
                }
        }
    }

    fun markRecipeCooked(plannedRecipe: PlannedRecipe) {
        viewModelScope.launch {
            mealPlanRepository.markRecipeCooked(plannedRecipe.id)
        }
    }

    fun unmarkRecipeCooked(plannedRecipe: PlannedRecipe) {
        viewModelScope.launch {
            mealPlanRepository.unmarkRecipeCooked(plannedRecipe.id)
        }
    }
}

data class HomeUiState(
    val mealPlan: MealPlan? = null,
    val pantryItemCount: Int = 0,
    val savedRecipeCount: Int = 0
) {
    val weekDisplayText: String
        get() {
            val nextMonday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
            return "Week of ${nextMonday.format(DateTimeFormatter.ofPattern("MMM d"))}"
        }

    val plannedRecipes: List<PlannedRecipe>
        get() = mealPlan?.recipes ?: emptyList()

    val totalPlanned: Int
        get() = mealPlan?.totalRecipes ?: 0

    val cookedCount: Int
        get() = mealPlan?.cookedCount ?: 0

    val progressPercent: Float
        get() = if (totalPlanned > 0) cookedCount.toFloat() / totalPlanned else 0f
}
