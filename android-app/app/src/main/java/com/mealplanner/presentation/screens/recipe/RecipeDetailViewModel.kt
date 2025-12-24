package com.mealplanner.presentation.screens.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealplanner.domain.model.PantryItem
import com.mealplanner.domain.model.PlannedRecipe
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeHistory
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.PantryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    private val pantryRepository: PantryRepository,
    private val mealPlanRepository: MealPlanRepository
) : ViewModel() {

    private val _pantryItems = MutableStateFlow<List<PantryItem>>(emptyList())
    val pantryItems: StateFlow<List<PantryItem>> = _pantryItems.asStateFlow()

    private val _recipeHistory = MutableStateFlow<RecipeHistory?>(null)
    val recipeHistory: StateFlow<RecipeHistory?> = _recipeHistory.asStateFlow()

    private val _rating = MutableStateFlow<Int?>(null)
    val rating: StateFlow<Int?> = _rating.asStateFlow()

    private val _wouldMakeAgain = MutableStateFlow<Boolean?>(null)
    val wouldMakeAgain: StateFlow<Boolean?> = _wouldMakeAgain.asStateFlow()

    // Track the planned recipe from current meal plan
    private val _plannedRecipe = MutableStateFlow<PlannedRecipe?>(null)
    val plannedRecipe: StateFlow<PlannedRecipe?> = _plannedRecipe.asStateFlow()

    private var currentHistoryId: Long? = null
    private var currentRecipeName: String? = null

    init {
        loadPantryItems()
        observeMealPlan()
    }

    private fun loadPantryItems() {
        viewModelScope.launch {
            pantryRepository.observeAllItems().collect { items ->
                _pantryItems.value = items
            }
        }
    }

    private fun observeMealPlan() {
        viewModelScope.launch {
            mealPlanRepository.observeCurrentMealPlan().collect { mealPlan ->
                // Find the planned recipe matching the current recipe name
                val recipeName = currentRecipeName
                if (recipeName != null && mealPlan != null) {
                    val planned = mealPlan.recipes.find { it.recipe.name == recipeName }
                    _plannedRecipe.value = planned
                }
            }
        }
    }

    fun loadRecipeData(recipeName: String) {
        currentRecipeName = recipeName

        viewModelScope.launch {
            // Load recipe history
            val history = mealPlanRepository.getRecipeHistory(recipeName)
            _recipeHistory.value = history
            currentHistoryId = history?.id
            _rating.value = history?.rating
            _wouldMakeAgain.value = history?.wouldMakeAgain

            // Find planned recipe in current meal plan
            val mealPlan = mealPlanRepository.getCurrentMealPlan()
            val planned = mealPlan?.recipes?.find { it.recipe.name == recipeName }
            _plannedRecipe.value = planned
        }
    }

    fun markAsCooked(recipe: Recipe) {
        val planned = _plannedRecipe.value ?: return

        viewModelScope.launch {
            // Mark as cooked in meal plan
            mealPlanRepository.markRecipeCooked(planned.id)

            // Record history for rating
            val historyId = mealPlanRepository.recordHistory(
                recipeName = recipe.name,
                recipeHash = recipe.id
            )
            currentHistoryId = historyId

            // Update local state
            _recipeHistory.value = RecipeHistory(
                id = historyId,
                recipeName = recipe.name,
                recipeHash = recipe.id,
                cookedAt = System.currentTimeMillis(),
                rating = null,
                wouldMakeAgain = null,
                notes = null
            )
        }
    }

    fun getIngredientStatus(ingredientName: String): IngredientStatus {
        val normalizedName = ingredientName.lowercase().trim()
        val pantryItem = _pantryItems.value.find {
            it.name.lowercase().contains(normalizedName) ||
            normalizedName.contains(it.name.lowercase())
        }

        return when {
            pantryItem == null -> IngredientStatus.NOT_IN_PANTRY
            pantryItem.isLowStock -> IngredientStatus.LOW_STOCK
            pantryItem.isExpiringSoon -> IngredientStatus.EXPIRING_SOON
            else -> IngredientStatus.IN_STOCK
        }
    }

    fun setRating(rating: Int) {
        _rating.value = rating
        saveRating()
    }

    fun setWouldMakeAgain(value: Boolean) {
        _wouldMakeAgain.value = value
        saveRating()
    }

    private fun saveRating() {
        val historyId = currentHistoryId ?: return
        viewModelScope.launch {
            mealPlanRepository.rateRecipe(
                historyId = historyId,
                rating = _rating.value,
                wouldMakeAgain = _wouldMakeAgain.value,
                notes = null
            )
        }
    }

    // Check if an ingredient is expiring soon (within 3 days)
    private val PantryItem.isExpiringSoon: Boolean
        get() = expiryDate?.let {
            it.isBefore(LocalDate.now().plusDays(3)) && it.isAfter(LocalDate.now().minusDays(1))
        } ?: false

    // Check if an ingredient is low stock (less than 25% remaining)
    private val PantryItem.isLowStock: Boolean
        get() = quantityInitial > 0 && (quantityRemaining / quantityInitial) < 0.25
}

enum class IngredientStatus {
    IN_STOCK,
    LOW_STOCK,
    EXPIRING_SOON,
    NOT_IN_PANTRY
}
