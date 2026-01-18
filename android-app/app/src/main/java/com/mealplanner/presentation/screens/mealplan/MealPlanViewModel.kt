package com.mealplanner.presentation.screens.mealplan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealplanner.data.remote.api.MealPlanApi
import com.mealplanner.data.remote.dto.OriginalIngredientDto
import com.mealplanner.data.remote.dto.RecipeStepDto
import com.mealplanner.data.remote.dto.SubstitutionRequest
import com.mealplanner.domain.model.GeneratedMealPlan
import com.mealplanner.domain.model.GenerationProgress
import com.mealplanner.domain.model.IngredientSource
import com.mealplanner.domain.model.MealPlan
import com.mealplanner.domain.model.PendingPantryItem
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeSearchResult
import com.mealplanner.domain.model.ShoppingList
import com.mealplanner.domain.repository.GenerationResult
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.RecipeRepository
import com.mealplanner.domain.repository.ShoppingRepository
import com.mealplanner.domain.usecase.GenerateMealPlanUseCase
import com.mealplanner.domain.usecase.ManagePreferencesUseCase
import com.mealplanner.domain.usecase.ManageShoppingListUseCase
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
    private val manageShoppingListUseCase: ManageShoppingListUseCase,
    private val mealPlanApi: MealPlanApi,
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

    private val _shoppingCompletionState = MutableStateFlow<ShoppingCompletionState?>(null)
    val shoppingCompletionState: StateFlow<ShoppingCompletionState?> = _shoppingCompletionState.asStateFlow()

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
            currentState is MealPlanUiState.StockingPantry ||
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

            // Note: categorize jobs are handled in ManageShoppingListUseCase during completeShoppingTrip
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
                        currentState !is MealPlanUiState.PolishingGroceryList &&
                        currentState !is MealPlanUiState.ConfirmingPantryItems &&
                        currentState !is MealPlanUiState.StockingPantry) {

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
                val mealPlanId = current.mealPlan.id

                try {
                    // Get checked items and their sources for the confirmation screen
                    val checkedItems = shoppingRepository.getCheckedItems(mealPlanId)
                    if (checkedItems.isEmpty()) {
                        android.util.Log.w("MealPlanVM", "No checked items to add to pantry")
                        return@launch
                    }

                    val itemsWithSources = shoppingRepository.getItemsWithSources(mealPlanId)
                    android.util.Log.d("MealPlanVM", "Building confirmation screen with ${checkedItems.size} items")

                    // Build pending pantry items for confirmation
                    val pendingItems = checkedItems.map { item ->
                        val sources = itemsWithSources[item.id] ?: emptyList()
                        PendingPantryItem(
                            shoppingItemId = item.id,
                            name = item.name,
                            displayQuantity = item.displayQuantity,
                            isModified = false,
                            originalName = item.name,
                            sources = sources
                        )
                    }

                    // Show confirmation screen
                    _uiState.value = MealPlanUiState.ConfirmingPantryItems(
                        mealPlanId = mealPlanId,
                        items = pendingItems
                    )
                } catch (e: Exception) {
                    android.util.Log.e("MealPlanVM", "Failed to prepare confirmation: ${e.message}", e)
                    // Stay on active plan
                }
            }
        }
    }

    fun updatePendingItem(itemId: Long, newName: String, newQuantity: String) {
        val current = _uiState.value
        if (current is MealPlanUiState.ConfirmingPantryItems) {
            val updatedItems = current.items.map { item ->
                if (item.shoppingItemId == itemId) {
                    item.copy(
                        name = newName,
                        displayQuantity = newQuantity,
                        isModified = newName != item.originalName || newQuantity != item.displayQuantity
                    )
                } else item
            }
            _uiState.value = current.copy(items = updatedItems, editingItemId = null)
        }
    }

    fun setEditingItem(itemId: Long?) {
        val current = _uiState.value
        if (current is MealPlanUiState.ConfirmingPantryItems) {
            _uiState.value = current.copy(editingItemId = itemId)
        }
    }

    fun removeItemFromConfirmation(itemId: Long) {
        val current = _uiState.value
        if (current is MealPlanUiState.ConfirmingPantryItems) {
            // Toggle isRemoved flag instead of removing from list (allows "skipped" display)
            val updatedItems = current.items.map { item ->
                if (item.shoppingItemId == itemId) {
                    item.copy(isRemoved = !item.isRemoved)
                } else item
            }
            _uiState.value = current.copy(items = updatedItems)
        }
    }

    fun confirmPantryItems() {
        viewModelScope.launch {
            val current = _uiState.value
            if (current is MealPlanUiState.ConfirmingPantryItems) {
                val mealPlanId = current.mealPlanId

                // Show stocking progress UI
                _uiState.value = MealPlanUiState.StockingPantry

                try {
                    // First, uncheck any items that were skipped so they won't be added to pantry
                    val removedItems = current.items.filter { it.isRemoved }
                    for (item in removedItems) {
                        shoppingRepository.toggleItemChecked(item.shoppingItemId)
                    }

                    // 1. Propagate name substitutions to recipes using AI (only for active items)
                    for (item in current.items.filter { it.hasSubstitution && !it.isRemoved }) {
                        android.util.Log.d("MealPlanVM", "Processing substitution: '${item.originalName}' -> '${item.name}'")

                        for (source in item.sources) {
                            try {
                                // Convert recipe steps to DTO format
                                val stepsDto = source.recipeSteps.map { step ->
                                    RecipeStepDto(title = step.title, substeps = step.substeps)
                                }

                                // Call AI to intelligently process the substitution
                                val substitutionRequest = SubstitutionRequest(
                                    recipeName = source.recipeName,
                                    originalIngredient = OriginalIngredientDto(
                                        name = item.originalName,
                                        quantity = source.originalQuantity,
                                        unit = source.originalUnit,
                                        preparation = source.originalPreparation
                                    ),
                                    newIngredientName = item.name,
                                    steps = stepsDto
                                )

                                val aiResponse = mealPlanApi.processSubstitution(substitutionRequest)
                                android.util.Log.d("MealPlanVM",
                                    "AI substitution result: recipe='${aiResponse.updatedRecipeName}', " +
                                    "ingredient=${aiResponse.updatedIngredient.quantity} ${aiResponse.updatedIngredient.unit} ${aiResponse.updatedIngredient.name}, " +
                                    "${aiResponse.updatedSteps.size} steps" +
                                    (aiResponse.notes?.let { ", notes=$it" } ?: "")
                                )

                                // Convert updated steps back to domain format
                                val updatedSteps = aiResponse.updatedSteps.map { step ->
                                    com.mealplanner.domain.model.CookingStep(title = step.title, substeps = step.substeps)
                                }

                                // Apply the AI-determined updates including steps
                                mealPlanRepository.updateRecipeWithSubstitution(
                                    plannedRecipeId = source.plannedRecipeId,
                                    ingredientIndex = source.ingredientIndex,
                                    newRecipeName = aiResponse.updatedRecipeName,
                                    newIngredientName = aiResponse.updatedIngredient.name,
                                    newQuantity = aiResponse.updatedIngredient.quantity,
                                    newUnit = aiResponse.updatedIngredient.unit,
                                    newPreparation = aiResponse.updatedIngredient.preparation,
                                    newSteps = updatedSteps
                                )
                            } catch (e: Exception) {
                                // If AI call fails, fall back to simple name update
                                android.util.Log.w("MealPlanVM", "AI substitution failed, using fallback: ${e.message}")
                                mealPlanRepository.updateRecipeIngredient(
                                    plannedRecipeId = source.plannedRecipeId,
                                    ingredientIndex = source.ingredientIndex,
                                    newName = item.name
                                )
                            }
                        }
                    }

                    // 2. Update shopping items with edits (only for active items)
                    for (item in current.items.filter { it.isModified && !it.isRemoved }) {
                        shoppingRepository.updateItem(
                            itemId = item.shoppingItemId,
                            name = item.name,
                            displayQuantity = item.displayQuantity
                        )
                    }

                    // 3. Proceed with AI categorization and pantry stocking
                    val itemsAdded = manageShoppingListUseCase.completeShoppingTrip(mealPlanId)

                    // Mark shopping as complete in the meal plan
                    mealPlanRepository.markShoppingComplete(mealPlanId)

                    // Transition back to active plan
                    val updatedPlan = mealPlanRepository.getCurrentMealPlan()
                    if (updatedPlan != null) {
                        _uiState.value = MealPlanUiState.ActivePlan(updatedPlan, ViewMode.PRIMARY)
                    } else {
                        setEmpty()
                    }

                    // Show completion dialog
                    _shoppingCompletionState.value = ShoppingCompletionState(itemsAddedToPantry = itemsAdded)
                } catch (e: Exception) {
                    android.util.Log.e("MealPlanVM", "Failed to stock pantry: ${e.message}", e)
                    // Return to active plan on error
                    val plan = mealPlanRepository.getCurrentMealPlan()
                    if (plan != null) {
                        _uiState.value = MealPlanUiState.ActivePlan(plan, ViewMode.SECONDARY)
                    } else {
                        setEmpty()
                    }
                }
            }
        }
    }

    fun cancelConfirmation() {
        viewModelScope.launch {
            val plan = mealPlanRepository.getCurrentMealPlan()
            if (plan != null) {
                _uiState.value = MealPlanUiState.ActivePlan(plan, ViewMode.SECONDARY)
            } else {
                setEmpty()
            }
        }
    }

    fun dismissShoppingCompletion() {
        _shoppingCompletionState.value = null
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
    data class ConfirmingPantryItems(
        val mealPlanId: Long,
        val items: List<PendingPantryItem>,
        val editingItemId: Long? = null
    ) : MealPlanUiState()
    data object StockingPantry : MealPlanUiState()
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

data class ShoppingCompletionState(
    val itemsAddedToPantry: Int
)
