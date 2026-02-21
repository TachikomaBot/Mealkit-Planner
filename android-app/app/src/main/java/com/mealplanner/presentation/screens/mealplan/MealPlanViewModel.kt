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
import com.mealplanner.service.GroceryPolishService
import com.mealplanner.service.MealGenerationService
import com.mealplanner.service.PolishState
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

    // Meal plan input — persists across tab navigation
    private val _ingredients = MutableStateFlow<List<String>>(emptyList())
    val ingredients: StateFlow<List<String>> = _ingredients.asStateFlow()

    private val _instructions = MutableStateFlow<List<String>>(emptyList())
    val instructions: StateFlow<List<String>> = _instructions.asStateFlow()

    init {
        observeCurrentPlan()
        observeServiceState()
        observePolishServiceState()
        observeShoppingList()
        observeSelectionCustomization()
        loadCategories()
        checkAndResumePendingJobs()
    }

    /**
     * Check for and resume any pending async jobs.
     * Called on init and when app returns from background.
     */
    fun checkAndResumePendingJobs() {
        // Don't check if we're already in an active flow state
        val currentState = _uiState.value
        if (currentState is MealPlanUiState.Generating ||
            currentState is MealPlanUiState.PolishingGroceryList ||
            currentState is MealPlanUiState.Saving) {
            android.util.Log.d("MealPlanVM", "Skipping pending job check - already in active state: $currentState")
            return
        }

        viewModelScope.launch {
            android.util.Log.d("MealPlanVM", "Checking for pending async jobs...")

            // Clean up stale jobs on startup
            shoppingRepository.cleanupStaleJobs()

            // Resume meal generation if pending
            recipeRepository.checkAndResumePendingGeneration()?.let { generationFlow ->
                android.util.Log.d("MealPlanVM", "Found pending meal generation, resuming...")
                _uiState.value = MealPlanUiState.Generating
                generationFlow.collect { result ->
                    when (result) {
                        is GenerationResult.Progress -> {
                            _generationProgress.value = result.progress
                        }
                        is GenerationResult.Success -> {
                            _uiState.value = MealPlanUiState.SelectingRecipes(
                                generatedPlan = result.mealPlan,
                                selectedIndices = result.mealPlan.defaultSelections.toMutableSet()
                            )
                            _generationProgress.value = null
                        }
                        is GenerationResult.Error -> {
                            android.util.Log.e("MealPlanVM", "Resumed generation failed: ${result.message}")
                            _uiState.value = MealPlanUiState.Error(result.message)
                            _generationProgress.value = null
                        }
                    }
                }
                return@launch  // Don't check other jobs if generation was resumed
            }

            // Resume polish if pending
            // checkAndResumePendingPolish returns null if no pending job, otherwise does the work
            val polishResult = shoppingRepository.checkAndResumePendingPolish()
            if (polishResult != null) {
                android.util.Log.d("MealPlanVM", "Found and resumed pending polish job")
                polishResult.onSuccess { polishedList ->
                    android.util.Log.d("MealPlanVM", "Resumed polish complete: ${polishedList.items.size} items")
                }.onFailure { error ->
                    android.util.Log.e("MealPlanVM", "Resumed polish failed: ${error.message}")
                }
                // Either way, transition to show the current state
                // The shopping list flow will automatically update with any new data
                transitionToActivePlan()
            } else {
                android.util.Log.d("MealPlanVM", "No pending polish job found")
            }
        }
    }

    private fun observeShoppingList() {
        viewModelScope.launch {
            shoppingRepository.observeCurrentShoppingList()
                .collect { _shoppingList.value = it }
        }
    }

    /**
     * Observe selection customization updates from RecipeDetailViewModel.
     * When a customization is applied in selection mode, update the recipe in the generated plan.
     */
    private fun observeSelectionCustomization() {
        viewModelScope.launch {
            mealPlanRepository.observeSelectionCustomization().collect { customization ->
                if (customization != null) {
                    val (index, updatedRecipe) = customization
                    applySelectionCustomization(index, updatedRecipe)
                    mealPlanRepository.clearSelectionCustomization()
                }
            }
        }
    }

    /**
     * Apply a customization to a recipe in the selection stage.
     */
    private fun applySelectionCustomization(recipeIndex: Int, updatedRecipe: Recipe) {
        val currentState = _uiState.value
        if (currentState !is MealPlanUiState.SelectingRecipes) {
            android.util.Log.w("MealPlanVM", "Cannot apply selection customization - not in SelectingRecipes state")
            return
        }

        // Update the recipe in the generated plan
        val updatedRecipes = currentState.generatedPlan.recipes.toMutableList()
        if (recipeIndex in updatedRecipes.indices) {
            updatedRecipes[recipeIndex] = updatedRecipe
            android.util.Log.d("MealPlanVM", "Applied selection customization to recipe at index $recipeIndex: ${updatedRecipe.name}")

            _uiState.value = currentState.copy(
                generatedPlan = currentState.generatedPlan.copy(recipes = updatedRecipes)
            )
        } else {
            android.util.Log.w("MealPlanVM", "Invalid recipe index for selection customization: $recipeIndex")
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

    private fun observePolishServiceState() {
        viewModelScope.launch {
            GroceryPolishService.polishState.collect { state ->
                when (state) {
                    is PolishState.Idle -> {
                        // Don't change UI state on idle
                    }
                    is PolishState.Polishing -> {
                        _uiState.value = MealPlanUiState.PolishingGroceryList
                    }
                    is PolishState.Success -> {
                        android.util.Log.d("MealPlanVM", "Polish service complete: ${state.shoppingList.items.size} items")
                        transitionToActivePlan()
                        GroceryPolishService.resetState()
                    }
                    is PolishState.Error -> {
                        android.util.Log.e("MealPlanVM", "Polish service error: ${state.message}")
                        // Polish failed but meal plan should still be available
                        transitionToActivePlan()
                        GroceryPolishService.resetState()
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
                    // Don't override if we're in a generation/selection/polishing/confirmation flow
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
            } else if (newSelection.size < 4) {
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

    // Meal plan input management
    fun addIngredient(ingredient: String) {
        _ingredients.value = _ingredients.value + ingredient
    }

    fun removeIngredient(index: Int) {
        _ingredients.value = _ingredients.value.toMutableList().apply { removeAt(index) }
    }

    fun addInstruction(instruction: String) {
        _instructions.value = _instructions.value + instruction
    }

    fun removeInstruction(index: Int) {
        _instructions.value = _instructions.value.toMutableList().apply { removeAt(index) }
    }

    fun generateMealPlan(leftoversInput: String = "") {
        // Start the foreground service for generation (API key is on backend)
        _uiState.value = MealPlanUiState.Generating
        _generationProgress.value = null
        MealGenerationService.startGeneration(context, leftoversInput)
        // Clear inputs after generation starts
        _ingredients.value = emptyList()
        _instructions.value = emptyList()
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
            } else if (newSelection.size < 4) {
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

    private fun polishShoppingList(mealPlanId: Long) {
        android.util.Log.d("MealPlanVM", "Starting grocery polish service for meal plan $mealPlanId")
        // Use foreground service to survive app backgrounding
        GroceryPolishService.startPolish(context, mealPlanId)
        // The observePolishServiceState() will handle state updates
    }

    private suspend fun transitionToActivePlan() {
        val plan = mealPlanRepository.getCurrentMealPlan()
        if (plan != null) {
            _uiState.value = MealPlanUiState.ActivePlan(plan, ViewMode.PRIMARY)
        } else {
            setEmpty()
        }
    }

    fun markShoppingComplete() {
        viewModelScope.launch {
            val plan = mealPlanRepository.getCurrentMealPlan() ?: return@launch
            mealPlanRepository.markShoppingComplete(plan.id)
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

