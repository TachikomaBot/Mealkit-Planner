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
            val updatedItems = current.items.filter { it.shoppingItemId != itemId }
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
                    // 1. Propagate name substitutions to recipes using AI
                    for (item in current.items.filter { it.hasSubstitution }) {
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
                                        unit = source.originalUnit
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

                    // 2. Update shopping items with edits
                    for (item in current.items.filter { it.isModified }) {
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
