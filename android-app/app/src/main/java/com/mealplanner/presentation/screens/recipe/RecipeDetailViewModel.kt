package com.mealplanner.presentation.screens.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealplanner.domain.model.PlannedRecipe
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeCustomizationResult
import com.mealplanner.domain.model.RecipeHistory
import com.mealplanner.domain.model.RecipeIngredient
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.ShoppingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    private val mealPlanRepository: MealPlanRepository,
    private val shoppingRepository: ShoppingRepository
) : ViewModel() {

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

    // Selection mode: non-null means we're viewing a recipe from the selection stage
    private var selectionModeIndex: Int? = null

    // Observable state for selection mode recipe (updates after customization)
    private val _selectionModeRecipe = MutableStateFlow<Recipe?>(null)
    val selectionModeRecipe: StateFlow<Recipe?> = _selectionModeRecipe.asStateFlow()

    /** Returns true if viewing a recipe from the selection stage (not yet saved) */
    val isSelectionMode: Boolean
        get() = selectionModeIndex != null

    init {
        observeMealPlan()
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

    fun loadRecipeData(recipeName: String, selectionIndex: Int? = null) {
        currentRecipeName = recipeName
        selectionModeIndex = selectionIndex

        viewModelScope.launch {
            // Load recipe history
            val history = mealPlanRepository.getRecipeHistory(recipeName)
            _recipeHistory.value = history
            currentHistoryId = history?.id
            _rating.value = history?.rating
            _wouldMakeAgain.value = history?.wouldMakeAgain

            // Find planned recipe in current meal plan (only if not in selection mode)
            if (selectionIndex == null) {
                val mealPlan = mealPlanRepository.getCurrentMealPlan()
                val planned = mealPlan?.recipes?.find { it.recipe.name == recipeName }
                _plannedRecipe.value = planned
                // Store the ID for future lookups (handles name changes from customization)
                currentPlannedRecipeId = planned?.id
            } else {
                // In selection mode - no planned recipe yet
                _plannedRecipe.value = null
                currentPlannedRecipeId = null
            }
        }
    }

    /**
     * Set the recipe reference for selection mode customization.
     * Called from RecipeDetailScreen when we have the actual Recipe object.
     */
    fun setSelectionModeRecipe(recipe: Recipe) {
        _selectionModeRecipe.value = recipe
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

    fun setRating(rating: Int) {
        _rating.value = rating
        saveRating()
    }

    fun setWouldMakeAgain(value: Boolean) {
        _wouldMakeAgain.value = value
        saveRating()
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

        // Check if we're in selection mode (recipe not yet saved to meal plan)
        val selectionIndex = selectionModeIndex
        if (selectionIndex != null) {
            applySelectionModeCustomization(current, selectionIndex)
            return
        }

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

                    // Update shopping list via Gemini (proper polish, semantic matching)
                    mealPlanRepository.getCurrentMealPlan()?.id?.let { mealPlanId ->
                        // Map removed ingredient names to full RecipeIngredient with quantities
                        val removedWithQuantities = current.customization.ingredientsToRemove.mapNotNull { removeStr ->
                            customizationOriginalIngredients.find { original ->
                                ingredientNameMatches(original.name, removeStr)
                            }
                        }

                        android.util.Log.d("RecipeDetailVM", "Updating shopping list via Gemini: " +
                            "add=${current.customization.ingredientsToAdd.size}, " +
                            "remove=${removedWithQuantities.size}, " +
                            "modify=${current.customization.ingredientsToModify.size}")

                        val shoppingResult = shoppingRepository.updateShoppingListAfterCustomization(
                            mealPlanId = mealPlanId,
                            ingredientsToAdd = current.customization.ingredientsToAdd,
                            ingredientsToRemove = removedWithQuantities,
                            ingredientsToModify = current.customization.ingredientsToModify,
                            recipeName = current.recipe.name
                        )

                        shoppingResult.onFailure { error ->
                            android.util.Log.e("RecipeDetailVM", "Shopping list update failed: ${error.message}")
                        }
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
     * Apply customization in selection mode (recipe not yet saved to meal plan).
     * Builds the updated recipe and stores it via MealPlanRepository for the selection screen to pick up.
     */
    private fun applySelectionModeCustomization(preview: CustomizationState.Preview, selectionIndex: Int) {
        val originalRecipe = _selectionModeRecipe.value ?: return

        _customizationState.value = CustomizationState.Applying

        // Build the final ingredients list (same logic as MealPlanRepositoryImpl)
        val finalIngredients = mutableListOf<RecipeIngredient>()

        // Start with original ingredients, applying modifications and removals
        for (original in customizationOriginalIngredients) {
            val isRemoved = preview.customization.ingredientsToRemove.any { removeStr ->
                ingredientNameMatches(original.name, removeStr)
            }
            if (isRemoved) continue

            val modification = preview.customization.ingredientsToModify.find {
                ingredientNameMatches(original.name, it.originalName)
            }

            if (modification != null) {
                finalIngredients.add(
                    RecipeIngredient(
                        name = modification.newName ?: original.name,
                        quantity = modification.newQuantity ?: original.quantity,
                        unit = modification.newUnit ?: original.unit,
                        preparation = modification.newPreparation ?: original.preparation
                    )
                )
            } else {
                finalIngredients.add(original)
            }
        }

        // Add new ingredients with category-based ordering
        for (newIngredient in preview.customization.ingredientsToAdd) {
            insertIngredientByCategory(finalIngredients, newIngredient)
        }

        // Build updated recipe
        val updatedRecipe = originalRecipe.copy(
            name = preview.customization.updatedRecipeName,
            description = preview.customization.updatedDescription,
            ingredients = finalIngredients,
            steps = preview.customization.updatedSteps
        )

        // Store the customization for MealPlanViewModel to pick up
        mealPlanRepository.setSelectionCustomization(selectionIndex, updatedRecipe)

        // Reset state
        _customizationState.value = CustomizationState.Idle
        _previousCustomizationRequests.clear()
        customizationOriginalIngredients = emptyList()

        // Update the local recipe reference so the UI shows updated recipe
        _selectionModeRecipe.value = updatedRecipe
    }

    // ========== Ingredient Category Ordering ==========

    private enum class IngredientCategory(val priority: Int) {
        PROTEIN(0),
        DAIRY(1),
        PRODUCE(2),
        PANTRY(3),
        SPICE(4),
        OTHER(5)
    }

    private fun categorizeIngredient(name: String): IngredientCategory {
        val lower = name.lowercase()

        val proteinKeywords = listOf(
            "chicken", "beef", "pork", "lamb", "turkey", "duck",
            "salmon", "fish", "shrimp", "prawn", "tuna", "cod", "tilapia", "halibut",
            "tofu", "tempeh", "seitan", "steak",
            "bacon", "sausage", "ham", "fillet", "thigh", "breast", "ground"
        )
        if (proteinKeywords.any { lower.contains(it) }) return IngredientCategory.PROTEIN

        val dairyKeywords = listOf(
            "milk", "cream", "cheese", "butter", "yogurt", "sour cream",
            "parmesan", "mozzarella", "cheddar", "feta", "ricotta"
        )
        if (dairyKeywords.any { lower.contains(it) }) return IngredientCategory.DAIRY

        val spiceKeywords = listOf(
            "salt", "pepper", "paprika", "cumin", "oregano", "thyme", "basil",
            "rosemary", "parsley", "cilantro", "dill", "chili", "cayenne",
            "cinnamon", "nutmeg", "turmeric", "curry", "ginger", "garlic powder",
            "onion powder", "bay leaf", "clove", "coriander", "fennel seed"
        )
        if (spiceKeywords.any { lower.contains(it) }) return IngredientCategory.SPICE

        val produceKeywords = listOf(
            "onion", "garlic", "tomato", "potato", "carrot", "celery", "pepper",
            "broccoli", "spinach", "lettuce", "kale", "cabbage", "zucchini",
            "cucumber", "mushroom", "asparagus", "green bean", "pea", "corn",
            "avocado", "lemon", "lime", "orange", "apple", "banana", "berry",
            "scallion", "leek", "shallot", "jalapeño", "bell pepper", "snap pea"
        )
        if (produceKeywords.any { lower.contains(it) }) return IngredientCategory.PRODUCE

        val pantryKeywords = listOf(
            "rice", "pasta", "noodle", "quinoa", "couscous", "bread", "flour",
            "oil", "vinegar", "soy sauce", "sauce", "broth", "stock",
            "bean", "lentil", "chickpea", "canned", "sugar", "honey"
        )
        if (pantryKeywords.any { lower.contains(it) }) return IngredientCategory.PANTRY

        return IngredientCategory.OTHER
    }

    private fun insertIngredientByCategory(
        list: MutableList<RecipeIngredient>,
        ingredient: RecipeIngredient
    ) {
        val newCategory = categorizeIngredient(ingredient.name)
        val insertIndex = list.indexOfFirst { existing ->
            categorizeIngredient(existing.name).priority > newCategory.priority
        }
        if (insertIndex == -1) {
            list.add(ingredient)
        } else {
            list.add(insertIndex, ingredient)
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

    /**
     * Check if two ingredient names match, using fuzzy matching.
     * Handles cases where Gemini returns "2 pieces salmon fillets (pat dry)" but
     * the recipe ingredient is just "Salmon Fillets".
     */
    private fun ingredientNameMatches(ingredientName: String, searchString: String): Boolean {
        val normalizedIngredient = normalizeIngredientName(ingredientName)
        val normalizedSearch = normalizeIngredientName(searchString)

        return normalizedIngredient.contains(normalizedSearch) ||
               normalizedSearch.contains(normalizedIngredient)
    }

    /**
     * Normalize ingredient name for matching - strips quantities, sizes, preparations.
     */
    private fun normalizeIngredientName(name: String): String {
        return name.lowercase()
            // Remove leading quantities like "2 pieces", "1/2 cup", "350g"
            .replace(Regex("""^\d+(\.\d+)?\s*(pieces?|piece|g|kg|ml|l|oz|lb|cup|cups|tbsp|tsp)?\s*"""), "")
            // Remove parenthetical notes like "(pat dry)", "(boneless)"
            .replace(Regex("""\([^)]*\)"""), "")
            // Size qualifiers
            .replace("large ", "").replace("medium ", "").replace("small ", "")
            // Freshness/state
            .replace("fresh ", "").replace("dried ", "").replace("frozen ", "").replace("canned ", "")
            // Preparation styles
            .replace("chopped ", "").replace("minced ", "").replace("diced ", "")
            .replace("sliced ", "").replace("whole ", "").replace("ground ", "")
            .replace("crushed ", "").replace("grated ", "").replace("shredded ", "")
            .trim()
    }
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
