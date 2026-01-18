package com.mealplanner.presentation.screens.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealplanner.domain.model.PantryItem
import com.mealplanner.domain.model.PendingDeductionItem
import com.mealplanner.domain.model.PlannedRecipe
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeCustomizationResult
import com.mealplanner.domain.model.RecipeHistory
import com.mealplanner.domain.model.RecipeIngredient
import com.mealplanner.domain.model.StockLevel
import com.mealplanner.domain.model.TrackingStyle
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.PantryRepository
import com.mealplanner.domain.repository.ShoppingRepository
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
    private val mealPlanRepository: MealPlanRepository,
    private val shoppingRepository: ShoppingRepository
) : ViewModel() {

    private val _pantryItems = MutableStateFlow<List<PantryItem>>(emptyList())
    val pantryItems: StateFlow<List<PantryItem>> = _pantryItems.asStateFlow()

    // UI state for deduction confirmation flow
    private val _uiState = MutableStateFlow<RecipeDetailUiState>(RecipeDetailUiState.ViewingRecipe)
    val uiState: StateFlow<RecipeDetailUiState> = _uiState.asStateFlow()

    private val _recipeHistory = MutableStateFlow<RecipeHistory?>(null)
    val recipeHistory: StateFlow<RecipeHistory?> = _recipeHistory.asStateFlow()

    private val _rating = MutableStateFlow<Int?>(null)
    val rating: StateFlow<Int?> = _rating.asStateFlow()

    private val _wouldMakeAgain = MutableStateFlow<Boolean?>(null)
    val wouldMakeAgain: StateFlow<Boolean?> = _wouldMakeAgain.asStateFlow()

    // Track the planned recipe from current meal plan
    private val _plannedRecipe = MutableStateFlow<PlannedRecipe?>(null)
    val plannedRecipe: StateFlow<PlannedRecipe?> = _plannedRecipe.asStateFlow()

    // Track if shopping is complete (to hide customization FAB)
    private val _shoppingComplete = MutableStateFlow(false)
    val shoppingComplete: StateFlow<Boolean> = _shoppingComplete.asStateFlow()

    // ========== Recipe Customization State ==========
    private val _customizationState = MutableStateFlow<CustomizationState>(CustomizationState.Idle)
    val customizationState: StateFlow<CustomizationState> = _customizationState.asStateFlow()

    // Track previous requests for refine loop context
    private val _previousCustomizationRequests = mutableListOf<String>()

    // Store the original ingredients when customization starts (needed for applying changes)
    private var customizationOriginalIngredients: List<RecipeIngredient> = emptyList()

    private var currentHistoryId: Long? = null
    private var currentRecipeName: String? = null
    private var currentPlannedRecipeId: Long? = null

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
                // Track shopping completion status
                _shoppingComplete.value = mealPlan?.shoppingComplete ?: false

                // Find the planned recipe by ID (survives name changes from customization)
                val plannedId = currentPlannedRecipeId
                val recipeName = currentRecipeName
                if (mealPlan != null) {
                    val planned = if (plannedId != null) {
                        // Prefer finding by ID once we have it
                        mealPlan.recipes.find { it.id == plannedId }
                    } else if (recipeName != null) {
                        // Fall back to name for initial load
                        mealPlan.recipes.find { it.recipe.name == recipeName }
                    } else {
                        null
                    }
                    _plannedRecipe.value = planned
                    // Store the ID for future lookups (handles name changes)
                    if (planned != null && currentPlannedRecipeId == null) {
                        currentPlannedRecipeId = planned.id
                    }
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
            // Store the ID for future lookups (handles name changes from customization)
            currentPlannedRecipeId = planned?.id
        }
    }

    /**
     * Refresh the planned recipe from database.
     * Called after customization to ensure UI shows updated data.
     */
    private suspend fun refreshPlannedRecipe() {
        val plannedId = currentPlannedRecipeId ?: return
        val mealPlan = mealPlanRepository.getCurrentMealPlan() ?: return
        val planned = mealPlan.recipes.find { it.id == plannedId }
        _plannedRecipe.value = planned
        android.util.Log.d("RecipeDetailVM", "Refreshed planned recipe: ${planned?.recipe?.name}")
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

    // ========== Deduction Confirmation Flow ==========

    /**
     * Start the deduction confirmation flow.
     * Builds a list of pending deductions from the recipe's ingredients,
     * matched against pantry items.
     */
    fun startDeductionConfirmation(recipe: Recipe) {
        viewModelScope.launch {
            val pantryItems = _pantryItems.value

            // Convert recipe ingredients to pending deduction items
            val pendingItems = recipe.ingredients.mapIndexed { index, ingredient ->
                val pantryMatch = findPantryMatch(ingredient.name, pantryItems)

                PendingDeductionItem(
                    id = index.toLong(),
                    ingredientName = ingredient.name,
                    originalQuantity = ingredient.quantity,
                    editedQuantity = ingredient.quantity,
                    unit = ingredient.unit,
                    isRemoved = false,
                    pantryItemId = pantryMatch?.id,
                    pantryItemName = pantryMatch?.name,
                    trackingStyle = pantryMatch?.trackingStyle,
                    currentStockLevel = pantryMatch?.stockLevel
                    // shouldReduceLevel defaults to false
                )
            }

            _uiState.value = RecipeDetailUiState.ConfirmingDeduction(
                recipe = recipe,
                items = pendingItems
            )
        }
    }

    /**
     * Find a matching pantry item using fuzzy matching.
     * Handles variations like "garlic cloves" matching "garlic".
     */
    private fun findPantryMatch(ingredientName: String, pantryItems: List<PantryItem>): PantryItem? {
        val normalizedName = ingredientName.lowercase().trim()

        // Try exact match first
        pantryItems.find { it.name.lowercase() == normalizedName }?.let { return it }

        // Try contains match
        pantryItems.find {
            it.name.lowercase().contains(normalizedName) ||
            normalizedName.contains(it.name.lowercase())
        }?.let { return it }

        // Try matching significant words (e.g., "garlic cloves" -> "garlic")
        val words = normalizedName.split(" ").filter { it.length > 3 }
        for (word in words) {
            pantryItems.find { it.name.lowercase().contains(word) }?.let { return it }
        }

        return null
    }

    /**
     * Update the quantity for a COUNT/PRECISE item.
     * Panel stays open for further adjustments.
     * Auto-restores skipped items when quantity is adjusted.
     */
    fun updateDeductionQuantity(itemId: Long, newQuantity: Double) {
        val current = _uiState.value
        if (current is RecipeDetailUiState.ConfirmingDeduction) {
            val updatedItems = current.items.map { item ->
                if (item.id == itemId) {
                    // Auto-restore if item was skipped and user adjusts quantity
                    item.copy(editedQuantity = newQuantity, isRemoved = false)
                } else item
            }
            _uiState.value = current.copy(items = updatedItems)
        }
    }

    /**
     * Set the target stock level for a STOCK_LEVEL item.
     * Panel stays open for further adjustments.
     * Auto-restores skipped items when level is adjusted.
     */
    fun setTargetStockLevel(itemId: Long, level: StockLevel) {
        val current = _uiState.value
        if (current is RecipeDetailUiState.ConfirmingDeduction) {
            val updatedItems = current.items.map { item ->
                if (item.id == itemId) {
                    // Auto-restore if item was skipped and user adjusts level
                    item.copy(targetStockLevel = level, isRemoved = false)
                } else item
            }
            _uiState.value = current.copy(items = updatedItems)
        }
    }

    /**
     * Skip an item from deduction (mark as skipped).
     * Closes the adjuster panel after skipping.
     */
    fun skipDeductionItem(itemId: Long) {
        val current = _uiState.value
        if (current is RecipeDetailUiState.ConfirmingDeduction) {
            val updatedItems = current.items.map { item ->
                if (item.id == itemId) item.copy(isRemoved = true)
                else item
            }
            _uiState.value = current.copy(items = updatedItems, editingItemId = null)
        }
    }

    /**
     * Set which item is currently being edited in the dialog.
     */
    fun setEditingDeductionItem(itemId: Long?) {
        val current = _uiState.value
        if (current is RecipeDetailUiState.ConfirmingDeduction) {
            _uiState.value = current.copy(editingItemId = itemId)
        }
    }

    /**
     * Confirm and execute the deductions.
     */
    fun confirmDeductions() {
        val current = _uiState.value
        if (current is RecipeDetailUiState.ConfirmingDeduction) {
            viewModelScope.launch {
                _uiState.value = RecipeDetailUiState.ProcessingDeduction

                // Deduct each non-removed item from pantry
                val itemsToDeduct = current.items.filter { !it.isRemoved && it.hasPantryMatch }
                for (item in itemsToDeduct) {
                    if (item.isStockLevelItem) {
                        // For stock level items, set the target level if changed
                        if (item.hasStockLevelChange && item.pantryItemId != null && item.targetStockLevel != null) {
                            pantryRepository.setStockLevel(item.pantryItemId, item.targetStockLevel)
                        }
                    } else {
                        // For count/precise items, deduct the quantity
                        pantryRepository.deductByName(item.ingredientName, item.editedQuantity)
                    }
                }

                // Mark recipe as cooked
                markAsCooked(current.recipe)

                // Return to viewing state
                _uiState.value = RecipeDetailUiState.ViewingRecipe
            }
        }
    }

    /**
     * Cancel the deduction confirmation and return to viewing.
     */
    fun cancelDeduction() {
        _uiState.value = RecipeDetailUiState.ViewingRecipe
    }

    // ========== Recipe Customization Flow ==========

    /**
     * Show the customization input dialog.
     */
    fun showCustomizeDialog(recipe: Recipe) {
        customizationOriginalIngredients = recipe.ingredients
        _previousCustomizationRequests.clear()
        _customizationState.value = CustomizationState.InputDialog(recipe)
    }

    /**
     * Submit a customization request to the AI.
     */
    fun submitCustomization(recipe: Recipe, request: String) {
        if (request.isBlank()) return

        viewModelScope.launch {
            _customizationState.value = CustomizationState.Loading(request)

            val result = mealPlanRepository.requestRecipeCustomization(
                recipe = recipe,
                customizationRequest = request,
                previousRequests = _previousCustomizationRequests.toList()
            )

            result.fold(
                onSuccess = { customization ->
                    _previousCustomizationRequests.add(request)
                    _customizationState.value = CustomizationState.Preview(
                        recipe = recipe,
                        customization = customization,
                        lastRequest = request
                    )
                },
                onFailure = { error ->
                    _customizationState.value = CustomizationState.Error(
                        recipe = recipe,
                        message = error.message ?: "Failed to customize recipe"
                    )
                }
            )
        }
    }

    /**
     * Refine the customization with an additional request.
     * Re-opens the input dialog with the current recipe state.
     */
    fun refineCustomization() {
        val current = _customizationState.value
        if (current is CustomizationState.Preview) {
            _customizationState.value = CustomizationState.InputDialog(current.recipe)
        }
    }

    /**
     * Apply the customization result to the recipe.
     */
    fun applyCustomization() {
        val current = _customizationState.value
        if (current !is CustomizationState.Preview) return

        val plannedRecipeId = _plannedRecipe.value?.id ?: return

        viewModelScope.launch {
            _customizationState.value = CustomizationState.Applying

            val result = mealPlanRepository.applyRecipeCustomization(
                plannedRecipeId = plannedRecipeId,
                customization = current.customization,
                originalIngredients = customizationOriginalIngredients
            )

            result.fold(
                onSuccess = {
                    // Refresh the recipe UI immediately
                    refreshPlannedRecipe()

                    // Apply targeted updates to shopping list (preserves polished quantities)
                    mealPlanRepository.getCurrentMealPlan()?.id?.let { mealPlanId ->
                        android.util.Log.d("RecipeDetailVM", "Applying targeted shopping list updates")
                        shoppingRepository.applyRecipeCustomization(
                            mealPlanId = mealPlanId,
                            ingredientsToRemove = current.customization.ingredientsToRemove,
                            ingredientsToAdd = current.customization.ingredientsToAdd,
                            ingredientsToModify = current.customization.ingredientsToModify
                        )
                    }

                    _customizationState.value = CustomizationState.Idle
                    _previousCustomizationRequests.clear()
                    customizationOriginalIngredients = emptyList()
                },
                onFailure = { error ->
                    _customizationState.value = CustomizationState.Error(
                        recipe = current.recipe,
                        message = error.message ?: "Failed to apply customization"
                    )
                }
            )
        }
    }

    /**
     * Cancel the customization and return to normal viewing.
     */
    fun cancelCustomization() {
        _customizationState.value = CustomizationState.Idle
        _previousCustomizationRequests.clear()
        customizationOriginalIngredients = emptyList()
    }

    /**
     * Dismiss an error and return to idle state.
     */
    fun dismissCustomizationError() {
        _customizationState.value = CustomizationState.Idle
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

/**
 * UI state for the recipe detail screen.
 */
sealed class RecipeDetailUiState {
    /** Normal recipe viewing mode */
    data object ViewingRecipe : RecipeDetailUiState()

    /** Confirming which ingredients to deduct from pantry */
    data class ConfirmingDeduction(
        val recipe: Recipe,
        val items: List<PendingDeductionItem>,
        val editingItemId: Long? = null
    ) : RecipeDetailUiState()

    /** Processing the deductions */
    data object ProcessingDeduction : RecipeDetailUiState()
}

/**
 * UI state for recipe customization flow.
 */
sealed class CustomizationState {
    /** No customization in progress */
    data object Idle : CustomizationState()

    /** Showing text input dialog */
    data class InputDialog(val recipe: Recipe) : CustomizationState()

    /** Loading/processing the customization request */
    data class Loading(val request: String) : CustomizationState()

    /** Showing preview of proposed changes */
    data class Preview(
        val recipe: Recipe,
        val customization: RecipeCustomizationResult,
        val lastRequest: String
    ) : CustomizationState()

    /** Applying the customization to the recipe */
    data object Applying : CustomizationState()

    /** Error occurred during customization */
    data class Error(
        val recipe: Recipe,
        val message: String
    ) : CustomizationState()
}
