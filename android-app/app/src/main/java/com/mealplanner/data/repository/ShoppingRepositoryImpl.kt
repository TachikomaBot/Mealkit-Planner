package com.mealplanner.data.repository

import com.mealplanner.data.local.dao.MealPlanDao
import com.mealplanner.data.local.dao.ShoppingDao
import com.mealplanner.data.local.entity.ShoppingItemEntity
import com.mealplanner.data.local.entity.ShoppingItemSourceEntity
import com.mealplanner.data.remote.api.MealPlanApi
import com.mealplanner.data.remote.dto.CategorizedPantryItemDto
import com.mealplanner.data.remote.dto.GroceryIngredientDto
import com.mealplanner.data.remote.dto.GroceryPolishRequest
import com.mealplanner.data.remote.dto.GroceryPolishResponse
import com.mealplanner.data.remote.dto.PantryCategorizeRequest
import com.mealplanner.data.remote.dto.PantryItemDto
import com.mealplanner.data.remote.dto.ShoppingItemForPantryDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.mealplanner.domain.model.IngredientSource
import com.mealplanner.domain.model.RecipeStepSource
import com.mealplanner.domain.model.PantryItem
import com.mealplanner.domain.model.ShoppingCategories
import com.mealplanner.domain.model.ShoppingItem
import com.mealplanner.domain.model.ShoppingList
import com.mealplanner.domain.model.StockLevel
import com.mealplanner.domain.model.TrackingStyle
import com.mealplanner.domain.repository.PantryRepository
import com.mealplanner.domain.repository.ShoppingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@kotlinx.coroutines.ExperimentalCoroutinesApi
class ShoppingRepositoryImpl @Inject constructor(
    private val shoppingDao: ShoppingDao,
    private val mealPlanDao: MealPlanDao,
    private val mealPlanApi: MealPlanApi,
    private val pantryRepository: PantryRepository
) : ShoppingRepository {

    // NOTE: Exclusion logic (salt, water, oil, etc.) has been moved to the Gemini polish prompt.
    // This keeps the local code simple and lets Gemini make smarter decisions.

    override fun observeShoppingList(mealPlanId: Long): Flow<ShoppingList?> {
        return shoppingDao.observeItemsForMealPlan(mealPlanId).map { entities ->
            if (entities.isEmpty()) {
                null
            } else {
                ShoppingList(
                    mealPlanId = mealPlanId,
                    items = entities.map { it.toShoppingItem() },
                    generatedAt = System.currentTimeMillis()
                )
            }
        }
    }

    override fun observeCurrentShoppingList(): Flow<ShoppingList?> {
        return mealPlanDao.observeLatestMealPlan().flatMapLatest { mealPlanEntity ->
            if (mealPlanEntity == null) {
                flowOf(null)
            } else {
                observeShoppingList(mealPlanEntity.id)
            }
        }
    }

    override suspend fun generateShoppingList(mealPlanId: Long): Result<ShoppingList> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("ShoppingRepo", "Generating shopping list for meal plan $mealPlanId")

            // Delete existing items
            shoppingDao.deleteItemsForMealPlan(mealPlanId)

            // Get all planned recipes for this meal plan
            val plannedRecipes = mealPlanDao.getPlannedRecipes(mealPlanId)
            android.util.Log.d("ShoppingRepo", "Found ${plannedRecipes.size} planned recipes")

            // Get pantry items for cross-referencing
            val pantryItems = pantryRepository.getAllItems()
            val pantryLookup = buildPantryLookup(pantryItems)
            android.util.Log.d("ShoppingRepo", "Loaded ${pantryItems.size} pantry items for cross-reference")

            // Aggregate ingredients across all recipes with source tracking
            // Only aggregate exact matches - Gemini will handle smart merging during polish
            val aggregatedIngredients = mutableMapOf<String, AggregatedIngredient>()

            for (recipe in plannedRecipes) {
                // Parse recipe JSON to get ingredients with their indices
                val ingredients = parseIngredientsFromRecipeJson(recipe.recipeJson)
                android.util.Log.d("ShoppingRepo", "Recipe '${recipe.recipeName}' has ${ingredients.size} ingredients")

                for ((ingredientIndex, ingredient) in ingredients.withIndex()) {
                    // Skip items that have sufficient stock in pantry
                    // (Gemini polish will handle other exclusions like salt, water, oil)
                    if (hasSufficientPantryStock(ingredient.name, pantryLookup)) continue

                    // Create source info for this ingredient
                    val sourceInfo = IngredientSourceInfo(
                        plannedRecipeId = recipe.id,
                        recipeName = recipe.recipeName,
                        ingredientIndex = ingredientIndex,
                        originalName = ingredient.name,
                        originalQuantity = ingredient.quantity,
                        originalUnit = ingredient.unit
                    )

                    // Use lowercase name + unit as key for basic aggregation of exact matches
                    // Gemini will handle smart merging (e.g., "2 small carrots" + "1 large carrot")
                    val key = "${ingredient.name.lowercase().trim()}_${ingredient.unit.lowercase().trim()}"
                    val existing = aggregatedIngredients[key]
                    if (existing != null) {
                        existing.sources.add(sourceInfo)
                        aggregatedIngredients[key] = existing.copy(
                            quantity = existing.quantity + ingredient.quantity
                        )
                    } else {
                        aggregatedIngredients[key] = AggregatedIngredient(
                            name = ingredient.name,
                            quantity = ingredient.quantity,
                            unit = ingredient.unit,
                            sources = mutableListOf(sourceInfo)
                        )
                    }
                }
            }

            android.util.Log.d("ShoppingRepo", "Aggregated to ${aggregatedIngredients.size} unique ingredients")

            // Convert to shopping items (category will be assigned by Gemini during polish)
            // We need to track which aggregated ingredient maps to which shopping item
            val aggregatedList = aggregatedIngredients.values.toList()
            val shoppingItems = aggregatedList.map { ingredient ->
                ShoppingItemEntity(
                    mealPlanId = mealPlanId,
                    ingredientName = ingredient.name,
                    quantity = ingredient.quantity,
                    unit = ingredient.unit,
                    category = ShoppingCategories.OTHER,
                    notes = null
                )
            }

            // Insert items (note: we need to get the generated IDs for source tracking)
            // Room doesn't return IDs for bulk insert with REPLACE, so insert one by one
            val insertedIds = mutableListOf<Long>()
            for (item in shoppingItems) {
                val id = shoppingDao.insertItem(item)
                insertedIds.add(id)
            }

            // Insert source records to track where each shopping item came from
            val sourceEntities = mutableListOf<ShoppingItemSourceEntity>()
            for ((index, aggregated) in aggregatedList.withIndex()) {
                val shoppingItemId = insertedIds[index]
                for (source in aggregated.sources) {
                    sourceEntities.add(
                        ShoppingItemSourceEntity(
                            shoppingItemId = shoppingItemId,
                            plannedRecipeId = source.plannedRecipeId,
                            ingredientIndex = source.ingredientIndex,
                            originalName = source.originalName,
                            originalQuantity = source.originalQuantity,
                            originalUnit = source.originalUnit
                        )
                    )
                }
            }
            if (sourceEntities.isNotEmpty()) {
                shoppingDao.insertSources(sourceEntities)
                android.util.Log.d("ShoppingRepo", "Inserted ${sourceEntities.size} source records")
            }

            // Return the shopping list
            val items = shoppingDao.getItemsForMealPlan(mealPlanId)
            android.util.Log.d("ShoppingRepo", "Shopping list generated with ${items.size} items")
            Result.success(
                ShoppingList(
                    mealPlanId = mealPlanId,
                    items = items.map { it.toShoppingItem() },
                    generatedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("ShoppingRepo", "Failed to generate shopping list: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun toggleItemChecked(itemId: Long) = withContext(Dispatchers.IO) {
        shoppingDao.toggleChecked(itemId)
    }

    override suspend fun toggleItemInCart(itemId: Long) = withContext(Dispatchers.IO) {
        shoppingDao.updateInCart(itemId, true)
    }

    override suspend fun resetAllItems(mealPlanId: Long) = withContext(Dispatchers.IO) {
        shoppingDao.resetAllItems(mealPlanId)
    }

    override suspend fun deleteShoppingList(mealPlanId: Long) = withContext(Dispatchers.IO) {
        shoppingDao.deleteItemsForMealPlan(mealPlanId)
    }

    override suspend fun deleteItem(itemId: Long) = withContext(Dispatchers.IO) {
        shoppingDao.deleteItem(itemId)
    }

    override fun observeUncheckedCount(mealPlanId: Long): Flow<Int> {
        return shoppingDao.observeUncheckedCount(mealPlanId)
    }

    override suspend fun addItem(
        mealPlanId: Long,
        name: String,
        quantity: Double,
        unit: String,
        category: String
    ): Long = withContext(Dispatchers.IO) {
        val entity = ShoppingItemEntity(
            mealPlanId = mealPlanId,
            ingredientName = name,
            quantity = quantity,
            unit = unit,
            category = category,
            notes = null
        )
        shoppingDao.insertItem(entity)
    }

    override suspend fun getCheckedItems(mealPlanId: Long): List<ShoppingItem> = withContext(Dispatchers.IO) {
        shoppingDao.getCheckedItemsForMealPlan(mealPlanId).map { it.toShoppingItem() }
    }

    override suspend fun polishShoppingList(mealPlanId: Long): Result<ShoppingList> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("ShoppingRepo", "Polishing shopping list for meal plan $mealPlanId")

            // Get current shopping items
            val items = shoppingDao.getItemsForMealPlan(mealPlanId)
            if (items.isEmpty()) {
                android.util.Log.w("ShoppingRepo", "No items to polish")
                return@withContext Result.failure(Exception("No items to polish"))
            }
            android.util.Log.d("ShoppingRepo", "Found ${items.size} items to polish")

            // Get pantry items to pass to Gemini for cross-referencing
            val pantryItems = pantryRepository.getAllItems()
            android.util.Log.d("ShoppingRepo", "Sending ${pantryItems.size} pantry items for context")

            // Convert to API request format
            // Gemini will handle exclusions (salt, water, oil, etc.) and pantry cross-referencing
            val request = GroceryPolishRequest(
                ingredients = items.map { item ->
                    GroceryIngredientDto(
                        id = item.id,
                        name = item.ingredientName,
                        quantity = item.quantity,
                        unit = item.unit
                    )
                },
                pantryItems = pantryItems.map { pantry ->
                    PantryItemDto(
                        name = pantry.name,
                        quantity = pantry.quantityRemaining,
                        unit = pantry.unit.displayName,
                        availability = pantry.effectiveStockLevel.name.lowercase()
                    )
                }
            )

            // Start async polish job
            android.util.Log.d("ShoppingRepo", "Starting async polish job...")
            val startResponse = mealPlanApi.startGroceryPolish(request)
            val jobId = startResponse.jobId
            android.util.Log.d("ShoppingRepo", "Polish job started: $jobId")

            // Poll for completion
            var polishResult: GroceryPolishResponse? = null
            var pollCount = 0
            while (polishResult == null) {
                kotlinx.coroutines.delay(1000) // Poll every second
                pollCount++

                val status = mealPlanApi.getGroceryPolishJobStatus(jobId)
                android.util.Log.d("ShoppingRepo", "Poll $pollCount: status=${status.status}")
                when (status.status) {
                    "completed" -> {
                        polishResult = status.result
                            ?: return@withContext Result.failure(Exception("Job completed but no result"))
                        // Clean up the job
                        try { mealPlanApi.deleteGroceryPolishJob(jobId) } catch (_: Exception) {}
                    }
                    "failed" -> {
                        android.util.Log.e("ShoppingRepo", "Polish job failed: ${status.error}")
                        // Clean up the job
                        try { mealPlanApi.deleteGroceryPolishJob(jobId) } catch (_: Exception) {}
                        return@withContext Result.failure(Exception(status.error ?: "Polish job failed"))
                    }
                    // "pending", "running" - continue polling
                }
            }

            android.util.Log.d("ShoppingRepo", "Polish complete: ${polishResult.items.size} items returned")

            // REPLACE STRATEGY: Gemini merges/filters items freely, so we replace the list entirely
            // Delete existing items and insert the polished results as new items
            shoppingDao.deleteItemsForMealPlan(mealPlanId)

            val newItems = polishResult.items.map { polished ->
                ShoppingItemEntity(
                    mealPlanId = mealPlanId,
                    ingredientName = polished.name,
                    quantity = 0.0, // Not used - display uses polishedDisplayQuantity
                    unit = "",
                    category = polished.category,
                    polishedDisplayQuantity = polished.displayQuantity,
                    notes = null
                )
            }
            shoppingDao.insertItems(newItems)

            // Regenerate source records by matching polished item names to recipe ingredients
            // This is necessary because the old sources were cascade-deleted with the old items
            regenerateSourcesAfterPolish(mealPlanId)

            // Return updated shopping list
            val updatedItems = shoppingDao.getItemsForMealPlan(mealPlanId)
            android.util.Log.d("ShoppingRepo", "Polish saved: ${updatedItems.size} items in DB")
            Result.success(
                ShoppingList(
                    mealPlanId = mealPlanId,
                    items = updatedItems.map { it.toShoppingItem() },
                    generatedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("ShoppingRepo", "Polish failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun ShoppingItemEntity.toShoppingItem() = ShoppingItem(
        id = id,
        name = ingredientName,
        quantity = quantity,
        unit = unit,
        category = category,
        checked = checked,
        inCart = inCart,
        notes = notes,
        polishedDisplayQuantity = polishedDisplayQuantity
    )

    // Helper class for aggregation with source tracking
    private data class AggregatedIngredient(
        val name: String,
        val quantity: Double,
        val unit: String,
        val sources: MutableList<IngredientSourceInfo> = mutableListOf()
    )

    // Source info for tracking where an ingredient came from
    private data class IngredientSourceInfo(
        val plannedRecipeId: Long,
        val recipeName: String,
        val ingredientIndex: Int,
        val originalName: String,
        val originalQuantity: Double,
        val originalUnit: String
    )

    // JSON structures matching what's stored in the database by MealPlanRepositoryImpl
    @Serializable
    private data class StoredRecipeJson(
        val ingredients: List<StoredIngredientJson> = emptyList(),
        val steps: List<StoredStepJson> = emptyList()
    )

    @Serializable
    private data class StoredIngredientJson(
        val name: String,
        val quantity: Double = 1.0,
        val unit: String = "",
        val preparation: String? = null  // e.g., "torn", "minced"
    )

    @Serializable
    private data class StoredStepJson(
        val title: String,
        val substeps: List<String> = emptyList()
    )

    // JSON parser configured to ignore unknown keys
    private val json = Json { ignoreUnknownKeys = true }

    // Parse ingredients from recipe JSON using proper deserialization
    // Matches the RecipeJson format used in MealPlanRepositoryImpl
    private fun parseIngredientsFromRecipeJson(recipeJson: String): List<AggregatedIngredient> {
        return try {
            val recipe = json.decodeFromString<StoredRecipeJson>(recipeJson)
            recipe.ingredients.map { ingredient ->
                AggregatedIngredient(
                    name = ingredient.name,
                    quantity = ingredient.quantity,
                    unit = ingredient.unit
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ShoppingRepo", "Failed to parse recipe JSON: ${e.message}")
            emptyList()
        }
    }

    // Normalize ingredient name for pantry matching
    // Removes size qualifiers so "garlic cloves" matches "garlic" in pantry
    private fun normalizeForPantryMatch(name: String): String {
        return name.lowercase()
            .replace("cloves", "")
            .replace("clove", "")
            .replace("large ", "")
            .replace("medium ", "")
            .replace("small ", "")
            .replace("fresh ", "")
            .replace("dried ", "")
            .replace("chopped ", "")
            .replace("minced ", "")
            .replace("diced ", "")
            .replace("sliced ", "")
            .trim()
    }

    // Build a lookup map from pantry items, keyed by normalized name
    private fun buildPantryLookup(items: List<PantryItem>): Map<String, PantryItem> {
        return items.associateBy { normalizeForPantryMatch(it.name) }
    }

    // Check if an ingredient has sufficient stock in the pantry
    private fun hasSufficientPantryStock(ingredientName: String, lookup: Map<String, PantryItem>): Boolean {
        val normalizedName = normalizeForPantryMatch(ingredientName)
        val pantryItem = lookup[normalizedName] ?: return false

        return when (pantryItem.trackingStyle) {
            TrackingStyle.STOCK_LEVEL ->
                pantryItem.effectiveStockLevel in listOf(StockLevel.SOME, StockLevel.PLENTY)
            TrackingStyle.COUNT, TrackingStyle.PRECISE ->
                pantryItem.quantityRemaining > 0 && !pantryItem.isLowStock
        }
    }

    /**
     * Regenerate source records after polish by matching polished item names to recipe ingredients.
     * This is necessary because polish deletes old items (cascade-deleting sources) and creates new ones.
     */
    private suspend fun regenerateSourcesAfterPolish(mealPlanId: Long) {
        try {
            val shoppingItems = shoppingDao.getItemsForMealPlan(mealPlanId)
            val recipes = mealPlanDao.getPlannedRecipes(mealPlanId)

            android.util.Log.d("ShoppingRepo", "Regenerating sources for ${shoppingItems.size} items across ${recipes.size} recipes")

            val allSources = mutableListOf<ShoppingItemSourceEntity>()

            for (item in shoppingItems) {
                val itemNameNormalized = normalizeForSourceMatch(item.ingredientName)

                for (recipe in recipes) {
                    val ingredients = parseIngredientsFromRecipeJson(recipe.recipeJson)

                    ingredients.forEachIndexed { index, ingredient ->
                        val ingredientNameNormalized = normalizeForSourceMatch(ingredient.name)

                        // Match if the normalized names are similar
                        if (ingredientNameNormalized.contains(itemNameNormalized) ||
                            itemNameNormalized.contains(ingredientNameNormalized)) {

                            allSources.add(
                                ShoppingItemSourceEntity(
                                    shoppingItemId = item.id,
                                    plannedRecipeId = recipe.id,
                                    ingredientIndex = index,
                                    originalName = ingredient.name,
                                    originalQuantity = ingredient.quantity,
                                    originalUnit = ingredient.unit
                                )
                            )
                            android.util.Log.d("ShoppingRepo",
                                "Linked '${item.ingredientName}' -> '${recipe.recipeName}' ingredient[$index]: ${ingredient.name}")
                        }
                    }
                }
            }

            if (allSources.isNotEmpty()) {
                shoppingDao.insertSources(allSources)
                android.util.Log.d("ShoppingRepo", "Regenerated ${allSources.size} source records")
            }
        } catch (e: Exception) {
            android.util.Log.e("ShoppingRepo", "Failed to regenerate sources: ${e.message}", e)
            // Non-fatal - substitution will fall back to name-only update
        }
    }

    /**
     * Normalize ingredient name for source matching.
     * More aggressive than pantry matching - strips common prefixes/suffixes to match
     * polished names (e.g., "Fresh Basil" polished to "Basil") back to recipe ingredients.
     */
    private fun normalizeForSourceMatch(name: String): String {
        return name.lowercase()
            .replace("fresh ", "")
            .replace("dried ", "")
            .replace("frozen ", "")
            .replace("canned ", "")
            .replace("chopped ", "")
            .replace("minced ", "")
            .replace("diced ", "")
            .replace("sliced ", "")
            .replace("whole ", "")
            .replace("ground ", "")
            .replace("crushed ", "")
            .replace("leaves", "")
            .replace("leaf", "")
            .trim()
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        shoppingDao.deleteAll()
    }

    override suspend fun categorizeForPantry(items: List<ShoppingItem>): Result<List<CategorizedPantryItemDto>> =
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("ShoppingRepo", "Categorizing ${items.size} items for pantry")

                if (items.isEmpty()) {
                    return@withContext Result.success(emptyList())
                }

                // Convert shopping items to request format
                val request = PantryCategorizeRequest(
                    items = items.map { item ->
                        ShoppingItemForPantryDto(
                            id = item.id,
                            name = item.name,
                            polishedDisplayQuantity = item.polishedDisplayQuantity ?: "${item.quantity} ${item.unit}".trim(),
                            shoppingCategory = item.category
                        )
                    }
                )

                // Start async categorization job
                android.util.Log.d("ShoppingRepo", "Starting async pantry categorize job...")
                val startResponse = mealPlanApi.startPantryCategorize(request)
                val jobId = startResponse.jobId
                android.util.Log.d("ShoppingRepo", "Pantry categorize job started: $jobId")

                // Poll for completion (with 60 second timeout)
                var result: List<CategorizedPantryItemDto>? = null
                var pollCount = 0
                val maxPolls = 60  // 60 seconds timeout
                while (result == null && pollCount < maxPolls) {
                    kotlinx.coroutines.delay(1000)  // Poll every second
                    pollCount++

                    val status = mealPlanApi.getPantryCategorizeJobStatus(jobId)
                    android.util.Log.d("ShoppingRepo", "Poll $pollCount: status=${status.status}")
                    when (status.status) {
                        "completed" -> {
                            result = status.result?.items
                                ?: return@withContext Result.failure(Exception("Job completed but no result"))
                            // Clean up the job
                            try { mealPlanApi.deletePantryCategorizeJob(jobId) } catch (_: Exception) {}
                        }
                        "failed" -> {
                            android.util.Log.e("ShoppingRepo", "Categorize job failed: ${status.error}")
                            try { mealPlanApi.deletePantryCategorizeJob(jobId) } catch (_: Exception) {}
                            return@withContext Result.failure(Exception(status.error ?: "Categorize job failed"))
                        }
                        // "pending", "running" - continue polling
                    }
                }

                if (result == null) {
                    android.util.Log.e("ShoppingRepo", "Categorize job timed out after ${maxPolls}s")
                    try { mealPlanApi.deletePantryCategorizeJob(jobId) } catch (_: Exception) {}
                    return@withContext Result.failure(Exception("Categorization timed out"))
                }

                android.util.Log.d("ShoppingRepo", "Categorization complete: ${result.size} items")
                Result.success(result)
            } catch (e: Exception) {
                android.util.Log.e("ShoppingRepo", "Categorization failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    override suspend fun getItemsWithSources(mealPlanId: Long): Map<Long, List<IngredientSource>> =
        withContext(Dispatchers.IO) {
            val sources = shoppingDao.getSourcesForMealPlan(mealPlanId)
            android.util.Log.d("ShoppingRepo", "Found ${sources.size} source records for meal plan $mealPlanId")

            // Group by shopping item ID and convert to domain model
            sources.groupBy { it.shoppingItemId }
                .mapValues { (_, sourceEntities) ->
                    sourceEntities.map { entity ->
                        // Get recipe from the planned recipe
                        val recipe = mealPlanDao.getPlannedRecipeById(entity.plannedRecipeId)
                        // Parse steps from recipe JSON
                        val steps = recipe?.recipeJson?.let { parseStepsFromRecipeJson(it) } ?: emptyList()
                        // Parse preparation from recipe JSON at the specific ingredient index
                        val preparation = recipe?.recipeJson?.let {
                            parseIngredientPreparation(it, entity.ingredientIndex)
                        }
                        IngredientSource(
                            plannedRecipeId = entity.plannedRecipeId,
                            recipeName = recipe?.recipeName ?: "Unknown Recipe",
                            ingredientIndex = entity.ingredientIndex,
                            originalQuantity = entity.originalQuantity,
                            originalUnit = entity.originalUnit,
                            originalPreparation = preparation,
                            recipeSteps = steps
                        )
                    }
                }
        }

    // Parse steps from recipe JSON
    private fun parseStepsFromRecipeJson(recipeJson: String): List<RecipeStepSource> {
        return try {
            val recipe = json.decodeFromString<StoredRecipeJson>(recipeJson)
            recipe.steps.map { step ->
                RecipeStepSource(title = step.title, substeps = step.substeps)
            }
        } catch (e: Exception) {
            android.util.Log.e("ShoppingRepo", "Failed to parse recipe steps: ${e.message}")
            emptyList()
        }
    }

    // Parse preparation style for a specific ingredient from recipe JSON
    private fun parseIngredientPreparation(recipeJson: String, ingredientIndex: Int): String? {
        return try {
            val recipe = json.decodeFromString<StoredRecipeJson>(recipeJson)
            recipe.ingredients.getOrNull(ingredientIndex)?.preparation
        } catch (e: Exception) {
            android.util.Log.e("ShoppingRepo", "Failed to parse ingredient preparation: ${e.message}")
            null
        }
    }

    override suspend fun updateItem(itemId: Long, name: String, displayQuantity: String): Unit =
        withContext(Dispatchers.IO) {
            shoppingDao.updateItemNameAndQuantity(itemId, name, displayQuantity)
            android.util.Log.d("ShoppingRepo", "Updated item $itemId: name='$name', qty='$displayQuantity'")
            Unit
        }
}
