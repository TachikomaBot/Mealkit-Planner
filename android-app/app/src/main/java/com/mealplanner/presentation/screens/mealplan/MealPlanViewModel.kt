package com.mealplanner.presentation.screens.mealplan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealplanner.domain.model.GeneratedMealPlan
import com.mealplanner.domain.model.GenerationProgress
import com.mealplanner.domain.model.MealPlan
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeSearchResult
import com.mealplanner.domain.model.ShoppingList
import com.mealplanner.domain.repository.GenerationResult
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.RecipeRepository
import com.mealplanner.domain.repository.ShoppingRepository
import com.mealplanner.domain.usecase.GenerateMealPlanUseCase
import com.mealplanner.domain.usecase.ManagePreferencesUseCase
import com.mealplanner.service.GenerationState
import com.mealplanner.service.MealGenerationService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MealPlanViewModel @Inject constructor(
    private val generateMealPlanUseCase: GenerateMealPlanUseCase,
    private val preferencesUseCase: ManagePreferencesUseCase,
    private val mealPlanRepository: MealPlanRepository,
    private val recipeRepository: RecipeRepository,
    private val shoppingRepository: ShoppingRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<MealPlanUiState>(MealPlanUiState.Loading)
    val uiState: StateFlow<MealPlanUiState> = _uiState.asStateFlow()

    private val _generationProgress = MutableStateFlow<GenerationProgress?>(null)
    val generationProgress: StateFlow<GenerationProgress?> = _generationProgress.asStateFlow()

    // For browsing recipes
    private val _browseState = MutableStateFlow(BrowseState())
    val browseState: StateFlow<BrowseState> = _browseState.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _shoppingList = MutableStateFlow<ShoppingList?>(null)
    val shoppingList: StateFlow<ShoppingList?> = _shoppingList.asStateFlow()

    init {
        observeCurrentPlan()
        observeServiceState()
        observeShoppingList()
        loadCategories()
    }

    private fun observeShoppingList() {
        viewModelScope.launch {
            shoppingRepository.observeCurrentShoppingList()
                .collect { _shoppingList.value = it }
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            MealGenerationService.generationState.collect { state ->
                when (state) {
                    is GenerationState.Idle -> {
                        // Don't change UI state on idle - let other observers handle it
                    }
                    is GenerationState.Generating -> {
                        _uiState.value = MealPlanUiState.Generating
                        _generationProgress.value = state.progress
                    }
                    is GenerationState.Success -> {
                        _uiState.value = MealPlanUiState.SelectingRecipes(
                            generatedPlan = state.mealPlan,
                            selectedIndices = state.mealPlan.defaultSelections.toMutableSet()
                        )
                        _generationProgress.value = null
                        MealGenerationService.resetState()
                    }
                    is GenerationState.Error -> {
                        _uiState.value = MealPlanUiState.Error(state.message)
                        _generationProgress.value = null
                        MealGenerationService.resetState()
                    }
                }
            }
        }
    }

    private fun observeCurrentPlan() {
        viewModelScope.launch {
            mealPlanRepository.observeCurrentMealPlan()
                .collect { plan ->
                    val currentState = _uiState.value
                    // Don't override if we're in a generation/selection/polishing flow
                    if (currentState !is MealPlanUiState.Generating &&
                        currentState !is MealPlanUiState.SelectingRecipes &&
                        currentState !is MealPlanUiState.Browsing &&
                        currentState !is MealPlanUiState.Saving &&
                        currentState !is MealPlanUiState.PolishingGroceryList) {

                        if (plan != null) {
                            // Preserve view mode if already in ActivePlan
                            val currentViewMode = (_uiState.value as? MealPlanUiState.ActivePlan)?.viewMode ?: ViewMode.PRIMARY
                            _uiState.value = MealPlanUiState.ActivePlan(plan, currentViewMode)
                        } else {
                            setEmpty()
                        }
                    }
                }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            recipeRepository.getStats()
                .onSuccess { stats ->
                    _categories.value = stats.categories.keys.toList().sorted()
                }
        }
    }

    private fun setEmpty() {
        _uiState.value = MealPlanUiState.Empty
    }

    fun startBrowsing() {
        _uiState.value = MealPlanUiState.Browsing
        _browseState.value = BrowseState()
        loadRecipes()
    }

    fun setCategory(category: String?) {
        _browseState.update { it.copy(selectedCategory = category, recipes = emptyList()) }
        loadRecipes()
    }

    private fun loadRecipes() {
        viewModelScope.launch {
            _browseState.update { it.copy(isLoading = true) }

            recipeRepository.searchRecipes(
                category = _browseState.value.selectedCategory,
                limit = 50,
                random = true
            ).onSuccess { results ->
                _browseState.update { it.copy(recipes = results, isLoading = false) }
            }.onFailure {
                _browseState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleRecipeInBrowse(recipe: RecipeSearchResult) {
        _browseState.update { state ->
            val newSelection = state.selectedRecipes.toMutableSet()
            val existing = newSelection.find { it.sourceId == recipe.sourceId }
            if (existing != null) {
                newSelection.remove(existing)
            } else if (newSelection.size < 6) {
                newSelection.add(recipe)
            }
            state.copy(selectedRecipes = newSelection)
        }
    }

    fun confirmBrowseSelection() {
        viewModelScope.launch {
            val selectedRecipes = _browseState.value.selectedRecipes.toList()
            if (selectedRecipes.isEmpty()) return@launch

            _uiState.value = MealPlanUiState.Saving

            // Convert search results to full recipes and save
            val recipes = mutableListOf<Recipe>()
            for (result in selectedRecipes) {
                recipeRepository.getRecipeById(result.sourceId)
                    .onSuccess { recipes.add(it) }
            }

            if (recipes.isNotEmpty()) {
                generateMealPlanUseCase.saveMealPlan(
                    recipes = recipes,
                    selectedIndices = recipes.indices.toList()
                ).onSuccess { mealPlanId ->
                    _browseState.value = BrowseState()
                    // Generate and polish the shopping list
                    polishShoppingList(mealPlanId)
                }.onFailure { error ->
                    _uiState.value = MealPlanUiState.Error(error.message ?: "Failed to save meal plan")
                }
            } else {
                _uiState.value = MealPlanUiState.Error("Failed to load recipes")
            }
        }
    }

    fun cancelBrowsing() {
        viewModelScope.launch {
            _browseState.value = BrowseState()
            val plan = mealPlanRepository.getCurrentMealPlan()
            if (plan != null) {
                _uiState.value = MealPlanUiState.ActivePlan(plan)
            } else {
                setEmpty()
            }
        }
    }

    fun generateMealPlan() {
        // Start the foreground service for generation (API key is on backend)
        _uiState.value = MealPlanUiState.Generating
        _generationProgress.value = null
        MealGenerationService.startGeneration(context)
    }

    fun cancelGeneration() {
        MealGenerationService.cancelGeneration(context)
        viewModelScope.launch {
            setEmpty()
        }
    }

    fun generateSimplePlan() {
        viewModelScope.launch {
            _uiState.value = MealPlanUiState.Generating
            _generationProgress.value = null

            generateMealPlanUseCase.generateSimple()
                .onSuccess { plan ->
                    _uiState.value = MealPlanUiState.SelectingRecipes(
                        generatedPlan = plan,
                        selectedIndices = plan.defaultSelections.toMutableSet()
                    )
                }
                .onFailure { error ->
                    _uiState.value = MealPlanUiState.Error(error.message ?: "Failed to generate meal plan")
                }
        }
    }

    fun toggleRecipeSelection(index: Int) {
        val currentState = _uiState.value
        if (currentState is MealPlanUiState.SelectingRecipes) {
            val newSelection = currentState.selectedIndices.toMutableSet()
            if (newSelection.contains(index)) {
                newSelection.remove(index)
            } else if (newSelection.size < 6) {
                newSelection.add(index)
            }
            _uiState.value = currentState.copy(selectedIndices = newSelection)
        }
    }

    fun saveMealPlan() {
        val currentState = _uiState.value
        if (currentState is MealPlanUiState.SelectingRecipes) {
            viewModelScope.launch {
                _uiState.value = MealPlanUiState.Saving

                generateMealPlanUseCase.saveMealPlan(
                    recipes = currentState.generatedPlan.recipes,
                    selectedIndices = currentState.selectedIndices.toList()
                ).onSuccess { mealPlanId ->
                    // Generate and polish the shopping list
                    polishShoppingList(mealPlanId)
                }.onFailure { error ->
                    _uiState.value = MealPlanUiState.Error(error.message ?: "Failed to save meal plan")
                }
            }
        }
    }

    private suspend fun polishShoppingList(mealPlanId: Long) {
        _uiState.value = MealPlanUiState.PolishingGroceryList
        android.util.Log.d("MealPlanVM", "Starting shopping list generation for meal plan $mealPlanId")

        // First generate the raw shopping list
        shoppingRepository.generateShoppingList(mealPlanId)
            .onSuccess { shoppingList ->
                android.util.Log.d("MealPlanVM", "Generated ${shoppingList.items.size} shopping items, now polishing...")
                // Then polish it with Gemini
                shoppingRepository.polishShoppingList(mealPlanId)
                    .onSuccess { polishedList ->
                        android.util.Log.d("MealPlanVM", "Polish complete: ${polishedList.items.size} items")
                        transitionToActivePlan()
                    }
                    .onFailure { error ->
                        android.util.Log.e("MealPlanVM", "Polish failed: ${error.message}")
                        // Polish failed but shopping list is still available (unpolished)
                        transitionToActivePlan()
                    }
            }
            .onFailure { error ->
                android.util.Log.e("MealPlanVM", "Shopping list generation failed: ${error.message}")
                // Shopping list generation failed, but meal plan is saved
                transitionToActivePlan()
            }
    }

    private suspend fun transitionToActivePlan() {
        val plan = mealPlanRepository.getCurrentMealPlan()
        if (plan != null) {
            _uiState.value = MealPlanUiState.ActivePlan(plan, ViewMode.PRIMARY)
        } else {
            setEmpty()
        }
    }

    fun toggleCooked(plannedRecipeId: Long, currentlyCooked: Boolean) {
        viewModelScope.launch {
            if (currentlyCooked) {
                mealPlanRepository.unmarkRecipeCooked(plannedRecipeId)
            } else {
                mealPlanRepository.markRecipeCooked(plannedRecipeId)
            }
        }
    }

    fun startNewPlan() {
        viewModelScope.launch {
            setEmpty()
        }
    }

    fun dismissError() {
        viewModelScope.launch {
            setEmpty()
        }
    }

    fun reset() {
        viewModelScope.launch {
            _generationProgress.value = null
            _browseState.value = BrowseState()
            setEmpty()
        }
    }

    fun returnToActivePlan() {
        viewModelScope.launch {
            val plan = mealPlanRepository.getCurrentMealPlan()
            if (plan != null) {
                _uiState.value = MealPlanUiState.ActivePlan(plan)
            } else {
                setEmpty()
            }
        }
    }

    fun toggleViewMode() {
        val current = _uiState.value
        if (current is MealPlanUiState.ActivePlan) {
            _uiState.value = current.copy(
                viewMode = if (current.viewMode == ViewMode.PRIMARY) ViewMode.SECONDARY else ViewMode.PRIMARY
            )
        }
    }

    fun markShoppingComplete() {
        viewModelScope.launch {
            val current = _uiState.value
            if (current is MealPlanUiState.ActivePlan) {
                mealPlanRepository.markShoppingComplete(current.mealPlan.id)
                // State will update via observer, but also update view mode to show meals
                _uiState.value = current.copy(viewMode = ViewMode.PRIMARY)
            }
        }
    }

    fun toggleItemChecked(itemId: Long) {
        viewModelScope.launch {
            shoppingRepository.toggleItemChecked(itemId)
        }
    }
}

sealed class MealPlanUiState {
    data object Loading : MealPlanUiState()
    data object Empty : MealPlanUiState()
    data object Generating : MealPlanUiState()
    data object Browsing : MealPlanUiState()
    data class SelectingRecipes(
        val generatedPlan: GeneratedMealPlan,
        val selectedIndices: Set<Int>
    ) : MealPlanUiState()
    data object Saving : MealPlanUiState()
    data object PolishingGroceryList : MealPlanUiState()
    data class ActivePlan(
        val mealPlan: MealPlan,
        val viewMode: ViewMode = ViewMode.PRIMARY
    ) : MealPlanUiState()
    data class Error(val message: String) : MealPlanUiState()
}

enum class ViewMode { PRIMARY, SECONDARY }

data class BrowseState(
    val selectedCategory: String? = null,
    val recipes: List<RecipeSearchResult> = emptyList(),
    val selectedRecipes: Set<RecipeSearchResult> = emptySet(),
    val isLoading: Boolean = false
)
