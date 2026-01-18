package com.mealplanner.data.repository

import com.mealplanner.data.local.dao.MealPlanDao
import com.mealplanner.data.local.entity.MealPlanEntity
import com.mealplanner.data.local.entity.PlannedRecipeEntity
import com.mealplanner.data.local.entity.RecipeHistoryEntity
import com.mealplanner.data.remote.api.MealPlanApi
import com.mealplanner.data.remote.dto.RecipeCustomizationRequest
import com.mealplanner.data.remote.dto.RecipeIngredientDto
import com.mealplanner.data.remote.dto.RecipeStepDto
import com.mealplanner.domain.model.CookingStep
import com.mealplanner.domain.model.MealPlan
import com.mealplanner.domain.model.ModifiedIngredient
import com.mealplanner.domain.model.PlannedRecipe
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeCustomizationResult
import com.mealplanner.domain.model.RecipeHistory
import com.mealplanner.domain.model.RecipeIngredient
import com.mealplanner.domain.repository.MealPlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealPlanRepositoryImpl @Inject constructor(
    private val mealPlanDao: MealPlanDao,
    private val mealPlanApi: MealPlanApi,
    private val json: Json
) : MealPlanRepository {

    // Selection-stage customization state (before meal plan is saved)
    private val _selectionCustomization = MutableStateFlow<Pair<Int, Recipe>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrentMealPlan(): Flow<MealPlan?> {
        return mealPlanDao.observeLatestMealPlan().flatMapLatest { mealPlanEntity ->
            if (mealPlanEntity == null) {
                flowOf(null)
            } else {
                mealPlanDao.observePlannedRecipes(mealPlanEntity.id).map { plannedRecipes ->
                    MealPlan(
                        id = mealPlanEntity.id,
                        recipes = plannedRecipes.map { it.toPlannedRecipe() },
                        createdAt = mealPlanEntity.createdAt,
                        shoppingComplete = mealPlanEntity.shoppingComplete
                    )
                }
            }
        }
    }

    override suspend fun getCurrentMealPlan(): MealPlan? = withContext(Dispatchers.IO) {
        mealPlanDao.getLatestMealPlan()?.let { entity ->
            val plannedRecipes = mealPlanDao.getPlannedRecipes(entity.id)
            MealPlan(
                id = entity.id,
                recipes = plannedRecipes.map { it.toPlannedRecipe() },
                createdAt = entity.createdAt,
                shoppingComplete = entity.shoppingComplete
            )
        }
    }

    override suspend fun saveMealPlan(recipes: List<Recipe>): Long = withContext(Dispatchers.IO) {
        val mealPlanEntity = MealPlanEntity()
        val mealPlanId = mealPlanDao.insertMealPlan(mealPlanEntity)

        val plannedRecipes = recipes.mapIndexed { index, recipe ->
            PlannedRecipeEntity(
                mealPlanId = mealPlanId,
                recipeName = recipe.name,
                recipeJson = json.encodeToString(RecipeJson.serializer(), recipe.toJson()),
                sequenceIndex = index
            )
        }
        mealPlanDao.insertPlannedRecipes(plannedRecipes)

        mealPlanId
    }

    override suspend fun markRecipeCooked(plannedRecipeId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mealPlanDao.markRecipeCooked(plannedRecipeId, cooked = true, cookedAt = System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unmarkRecipeCooked(plannedRecipeId: Long) = withContext(Dispatchers.IO) {
        mealPlanDao.markRecipeCooked(plannedRecipeId, cooked = false, cookedAt = null)
    }

    override suspend fun deleteMealPlan(mealPlanId: Long) = withContext(Dispatchers.IO) {
        val mealPlan = mealPlanDao.getLatestMealPlan()
        if (mealPlan?.id == mealPlanId) {
            mealPlanDao.deletePlannedRecipes(mealPlanId)
            mealPlanDao.deleteMealPlan(mealPlan)
        }
    }

    override suspend fun markShoppingComplete(mealPlanId: Long) = withContext(Dispatchers.IO) {
        mealPlanDao.markShoppingComplete(mealPlanId)
    }

    override suspend fun getRecentRecipeHashes(weeksBack: Int): List<String> = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - (weeksBack * 7 * 24 * 60 * 60 * 1000L)
        mealPlanDao.getRecentRecipeHashes(since)
    }

    override suspend fun recordHistory(recipeName: String, recipeHash: String): Long = withContext(Dispatchers.IO) {
        mealPlanDao.insertRecipeHistory(
            RecipeHistoryEntity(
                recipeName = recipeName,
                recipeHash = recipeHash,
                cookedAt = System.currentTimeMillis(),
                rating = null,
                wouldMakeAgain = null,
                notes = null
            )
        )
    }

    override suspend fun rateRecipe(
        historyId: Long,
        rating: Int?,
        wouldMakeAgain: Boolean?,
        notes: String?
    ) = withContext(Dispatchers.IO) {
        mealPlanDao.updateRating(historyId, rating, wouldMakeAgain, notes)
    }

    override suspend fun getRecipeHistory(recipeName: String): RecipeHistory? = withContext(Dispatchers.IO) {
        mealPlanDao.getLatestHistoryForRecipe(recipeName)?.toModel()
    }

    private fun PlannedRecipeEntity.toPlannedRecipe(): PlannedRecipe {
        val recipeData = json.decodeFromString(RecipeJson.serializer(), recipeJson)
        return PlannedRecipe(
            id = id,
            recipe = recipeData.toRecipe(),
            sequenceIndex = sequenceIndex,
            cooked = cooked,
            cookedAt = cookedAt
        )
    }

    private fun RecipeHistoryEntity.toModel() = RecipeHistory(
        id = id,
        recipeName = recipeName,
        recipeHash = recipeHash,
        cookedAt = cookedAt,
        rating = rating,
        wouldMakeAgain = wouldMakeAgain,
        notes = notes
    )

    // JSON representation for storing recipes
    @Serializable
    private data class RecipeJson(
        val id: String,
        val name: String,
        val description: String,
        val servings: Int,
        val prepTimeMinutes: Int,
        val cookTimeMinutes: Int,
        val ingredients: List<IngredientJson>,
        val steps: List<StepJson>,
        val tags: List<String>,
        val imageUrl: String? = null,
        val sourceRecipeIds: List<Int> = emptyList()
    )

    @Serializable
    private data class IngredientJson(
        val name: String,
        val quantity: Double,
        val unit: String,
        val preparation: String? = null
    )

    @Serializable
    private data class StepJson(
        val title: String,
        val substeps: List<String>
    )

    private fun Recipe.toJson() = RecipeJson(
        id = id,
        name = name,
        description = description,
        servings = servings,
        prepTimeMinutes = prepTimeMinutes,
        cookTimeMinutes = cookTimeMinutes,
        ingredients = ingredients.map {
            IngredientJson(it.name, it.quantity, it.unit, it.preparation)
        },
        steps = steps.map {
            StepJson(it.title, it.substeps)
        },
        tags = tags,
        imageUrl = imageUrl,
        sourceRecipeIds = sourceRecipeIds
    )

    private fun RecipeJson.toRecipe() = Recipe(
        id = id,
        name = name,
        description = description,
        servings = servings,
        prepTimeMinutes = prepTimeMinutes,
        cookTimeMinutes = cookTimeMinutes,
        ingredients = ingredients.map {
            RecipeIngredient(it.name, it.quantity, it.unit, it.preparation)
        },
        steps = steps.map {
            CookingStep(it.title, it.substeps)
        },
        tags = tags,
        imageUrl = imageUrl,
        sourceRecipeIds = sourceRecipeIds
    )

    // Stats and History methods
    override fun observeAllMealPlans(): Flow<List<MealPlan>> {
        return mealPlanDao.observeAllMealPlans().map { entities ->
            entities.map { entity ->
                MealPlan(
                    id = entity.id,
                    recipes = emptyList(), // Will load on demand
                    createdAt = entity.createdAt,
                    shoppingComplete = entity.shoppingComplete
                )
            }
        }
    }

    override fun observeAllRecipeHistory(): Flow<List<RecipeHistory>> {
        return mealPlanDao.observeAllHistory().map { entities ->
            entities.map { it.toModel() }
        }
    }

    override fun observeTotalCookedCount(): Flow<Int> {
        return mealPlanDao.observeTotalCookedCount()
    }

    override fun observeTotalMealPlansCount(): Flow<Int> {
        return mealPlanDao.observeTotalMealPlansCount()
    }

    override fun observeAverageRating(): Flow<Double?> {
        return mealPlanDao.observeAverageRating()
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        mealPlanDao.deleteAllMealPlans()
        mealPlanDao.deleteAllPlannedRecipes()
        mealPlanDao.deleteAllHistory()
    }

    override suspend fun updateRecipeIngredient(
        plannedRecipeId: Long,
        ingredientIndex: Int,
        newName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // Simple fallback method - just updates the ingredient name without AI logic
        // Used when AI substitution fails
        try {
            val entity = mealPlanDao.getPlannedRecipeById(plannedRecipeId)
                ?: return@withContext Result.failure(Exception("Recipe not found"))

            // Parse the recipe JSON
            val recipeData = json.decodeFromString(RecipeJson.serializer(), entity.recipeJson)

            // Check if the ingredient index is valid
            if (ingredientIndex !in recipeData.ingredients.indices) {
                return@withContext Result.failure(
                    Exception("Invalid ingredient index: $ingredientIndex (recipe has ${recipeData.ingredients.size} ingredients)")
                )
            }

            // Create updated ingredients list with the new name
            val updatedIngredients = recipeData.ingredients.toMutableList()
            val originalIngredient = updatedIngredients[ingredientIndex]
            updatedIngredients[ingredientIndex] = originalIngredient.copy(name = newName)

            // Create updated recipe (ingredient name only, no recipe name changes in fallback mode)
            val updatedRecipe = recipeData.copy(ingredients = updatedIngredients)

            // Serialize and save
            val updatedJson = json.encodeToString(RecipeJson.serializer(), updatedRecipe)
            mealPlanDao.updatePlannedRecipeJson(plannedRecipeId, updatedJson)

            android.util.Log.d("MealPlanRepo",
                "Fallback update: ingredient '${originalIngredient.name}' -> '$newName' in recipe ${entity.recipeName}"
            )

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MealPlanRepo", "Failed to update recipe ingredient: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateRecipeWithSubstitution(
        plannedRecipeId: Long,
        ingredientIndex: Int,
        newRecipeName: String,
        newIngredientName: String,
        newQuantity: Double,
        newUnit: String,
        newPreparation: String?,
        newSteps: List<CookingStep>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = mealPlanDao.getPlannedRecipeById(plannedRecipeId)
                ?: return@withContext Result.failure(Exception("Recipe not found"))

            // Parse the recipe JSON
            val recipeData = json.decodeFromString(RecipeJson.serializer(), entity.recipeJson)

            // Check if the ingredient index is valid
            if (ingredientIndex !in recipeData.ingredients.indices) {
                return@withContext Result.failure(
                    Exception("Invalid ingredient index: $ingredientIndex (recipe has ${recipeData.ingredients.size} ingredients)")
                )
            }

            // Create updated ingredients list with AI-determined values
            val updatedIngredients = recipeData.ingredients.toMutableList()
            updatedIngredients[ingredientIndex] = IngredientJson(
                name = newIngredientName,
                quantity = newQuantity,
                unit = newUnit,
                preparation = newPreparation  // AI may update or nullify preparation style
            )

            // Convert AI-updated steps to JSON format
            val updatedStepsJson = newSteps.map { step ->
                StepJson(title = step.title, substeps = step.substeps)
            }

            // Create updated recipe with AI-determined name, ingredients, and steps
            val updatedRecipe = recipeData.copy(
                name = newRecipeName,
                ingredients = updatedIngredients,
                steps = updatedStepsJson
            )

            // Serialize and save
            val updatedJson = json.encodeToString(RecipeJson.serializer(), updatedRecipe)

            // Update both recipe name and JSON
            mealPlanDao.updatePlannedRecipe(plannedRecipeId, newRecipeName, updatedJson)
            android.util.Log.d("MealPlanRepo",
                "AI substitution applied: recipe='$newRecipeName', ingredient='$newIngredientName', qty=$newQuantity $newUnit, ${newSteps.size} steps"
            )

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MealPlanRepo", "Failed to apply AI substitution: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun requestRecipeCustomization(
        recipe: Recipe,
        customizationRequest: String,
        previousRequests: List<String>
    ): Result<RecipeCustomizationResult> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("MealPlanRepo", "Requesting customization for '${recipe.name}': $customizationRequest")

            val request = RecipeCustomizationRequest(
                recipeName = recipe.name,
                description = recipe.description,
                ingredients = recipe.ingredients.map { ing ->
                    RecipeIngredientDto(
                        ingredientName = ing.name,
                        quantity = ing.quantity,
                        unit = ing.unit,
                        preparation = ing.preparation
                    )
                },
                steps = recipe.steps.map { step ->
                    RecipeStepDto(
                        title = step.title,
                        substeps = step.substeps
                    )
                },
                customizationRequest = customizationRequest,
                previousRequests = previousRequests
            )

            val response = mealPlanApi.customizeRecipe(request)

            val result = RecipeCustomizationResult(
                updatedRecipeName = response.updatedRecipeName,
                updatedDescription = response.updatedDescription,
                ingredientsToAdd = response.ingredientsToAdd.map { dto ->
                    RecipeIngredient(
                        name = dto.ingredientName,
                        quantity = dto.quantity ?: 0.0,  // Default to 0 for "to taste" ingredients
                        unit = dto.unit ?: "",
                        preparation = dto.preparation
                    )
                },
                ingredientsToRemove = response.ingredientsToRemove,
                ingredientsToModify = response.ingredientsToModify.map { dto ->
                    ModifiedIngredient(
                        originalName = dto.originalName,
                        newName = dto.newName,
                        newQuantity = dto.newQuantity,
                        newUnit = dto.newUnit,
                        newPreparation = dto.newPreparation
                    )
                },
                updatedSteps = response.updatedSteps.map { step ->
                    CookingStep(
                        title = step.title,
                        substeps = step.substeps
                    )
                },
                changesSummary = response.changesSummary,
                notes = response.notes
            )

            android.util.Log.d("MealPlanRepo", "Customization result: ${result.changesSummary}")
            Result.success(result)
        } catch (e: Exception) {
            android.util.Log.e("MealPlanRepo", "Recipe customization failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun applyRecipeCustomization(
        plannedRecipeId: Long,
        customization: RecipeCustomizationResult,
        originalIngredients: List<RecipeIngredient>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("MealPlanRepo", "Applying customization to recipe $plannedRecipeId")
            android.util.Log.d("MealPlanRepo", "ingredientsToRemove: ${customization.ingredientsToRemove}")

            // Compute final ingredients list
            val finalIngredients = mutableListOf<RecipeIngredient>()

            // Start with original ingredients, applying modifications and removals
            for (original in originalIngredients) {
                // Check if removed (fuzzy match - Gemini may return "2 pieces salmon fillets" for "Salmon Fillets")
                val isRemoved = customization.ingredientsToRemove.any { removeStr ->
                    ingredientNameMatches(original.name, removeStr)
                }
                if (isRemoved) {
                    android.util.Log.d("MealPlanRepo", "Removing ingredient: ${original.name}")
                    continue  // Skip removed ingredients
                }

                // Check if modified (fuzzy match)
                val modification = customization.ingredientsToModify.find {
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

            // Add new ingredients at appropriate positions based on category
            for (newIngredient in customization.ingredientsToAdd) {
                insertIngredientByCategory(finalIngredients, newIngredient)
            }

            // Get existing planned recipe
            val existingEntity = mealPlanDao.getPlannedRecipeById(plannedRecipeId)
                ?: return@withContext Result.failure(Exception("Planned recipe not found"))

            // Parse existing recipe
            val existingRecipe = json.decodeFromString(RecipeJson.serializer(), existingEntity.recipeJson)

            // Create updated recipe JSON
            val updatedRecipe = existingRecipe.copy(
                name = customization.updatedRecipeName,
                description = customization.updatedDescription,
                ingredients = finalIngredients.map { ing ->
                    IngredientJson(
                        name = ing.name,
                        quantity = ing.quantity,
                        unit = ing.unit,
                        preparation = ing.preparation
                    )
                },
                steps = customization.updatedSteps.map { step ->
                    StepJson(title = step.title, substeps = step.substeps)
                }
            )

            val updatedJson = json.encodeToString(RecipeJson.serializer(), updatedRecipe)

            // Update database
            mealPlanDao.updatePlannedRecipe(plannedRecipeId, customization.updatedRecipeName, updatedJson)

            android.util.Log.d("MealPlanRepo", "Customization applied: ${customization.updatedRecipeName}")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MealPlanRepo", "Failed to apply customization: ${e.message}", e)
            Result.failure(e)
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

        // Check if either contains the other (handles partial matches)
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

    /**
     * Ingredient category for ordering purposes.
     * Lower priority number = appears earlier in list.
     */
    private enum class IngredientCategory(val priority: Int) {
        PROTEIN(0),
        DAIRY(1),
        PRODUCE(2),
        PANTRY(3),
        SPICE(4),
        OTHER(5)
    }

    /**
     * Categorize an ingredient by name for ordering purposes.
     */
    private fun categorizeIngredient(name: String): IngredientCategory {
        val lower = name.lowercase()

        // Protein keywords
        val proteinKeywords = listOf(
            "chicken", "beef", "pork", "lamb", "turkey", "duck",
            "salmon", "fish", "shrimp", "prawn", "tuna", "cod", "tilapia", "halibut",
            "tofu", "tempeh", "seitan",
            "bacon", "sausage", "ham", "steak", "fillet", "thigh", "breast", "ground"
        )
        if (proteinKeywords.any { lower.contains(it) }) return IngredientCategory.PROTEIN

        // Dairy keywords
        val dairyKeywords = listOf(
            "milk", "cream", "cheese", "butter", "yogurt", "sour cream",
            "parmesan", "mozzarella", "cheddar", "feta", "ricotta"
        )
        if (dairyKeywords.any { lower.contains(it) }) return IngredientCategory.DAIRY

        // Spice/seasoning keywords (check before produce since "fresh basil" should be spice)
        val spiceKeywords = listOf(
            "salt", "pepper", "paprika", "cumin", "oregano", "thyme", "basil",
            "rosemary", "parsley", "cilantro", "dill", "chili", "cayenne",
            "cinnamon", "nutmeg", "turmeric", "curry", "ginger", "garlic powder",
            "onion powder", "bay leaf", "clove", "coriander", "fennel seed"
        )
        if (spiceKeywords.any { lower.contains(it) }) return IngredientCategory.SPICE

        // Produce keywords
        val produceKeywords = listOf(
            "onion", "garlic", "tomato", "potato", "carrot", "celery", "pepper",
            "broccoli", "spinach", "lettuce", "kale", "cabbage", "zucchini",
            "cucumber", "mushroom", "asparagus", "green bean", "pea", "corn",
            "avocado", "lemon", "lime", "orange", "apple", "banana", "berry",
            "scallion", "leek", "shallot", "jalapeño", "bell pepper", "snap pea"
        )
        if (produceKeywords.any { lower.contains(it) }) return IngredientCategory.PRODUCE

        // Pantry keywords
        val pantryKeywords = listOf(
            "rice", "pasta", "noodle", "quinoa", "couscous", "bread", "flour",
            "oil", "vinegar", "soy sauce", "sauce", "broth", "stock",
            "bean", "lentil", "chickpea", "canned", "sugar", "honey"
        )
        if (pantryKeywords.any { lower.contains(it) }) return IngredientCategory.PANTRY

        return IngredientCategory.OTHER
    }

    /**
     * Insert an ingredient at the appropriate position based on its category.
     * Maintains category ordering: Protein > Dairy > Produce > Pantry > Spice > Other
     */
    private fun insertIngredientByCategory(
        list: MutableList<RecipeIngredient>,
        ingredient: RecipeIngredient
    ) {
        val newCategory = categorizeIngredient(ingredient.name)

        // Find the first ingredient with a lower priority (higher number) category
        val insertIndex = list.indexOfFirst { existing ->
            categorizeIngredient(existing.name).priority > newCategory.priority
        }

        if (insertIndex == -1) {
            // No ingredient with lower priority found, append to end
            list.add(ingredient)
        } else {
            list.add(insertIndex, ingredient)
        }
    }

    // ========== Selection Stage Customization ==========

    override fun observeSelectionCustomization(): Flow<Pair<Int, Recipe>?> {
        return _selectionCustomization.asStateFlow()
    }

    override fun setSelectionCustomization(recipeIndex: Int, updatedRecipe: Recipe) {
        _selectionCustomization.value = Pair(recipeIndex, updatedRecipe)
    }

    override fun clearSelectionCustomization() {
        _selectionCustomization.value = null
    }
}
