package com.mealplanner.data.repository

import com.mealplanner.data.local.dao.MealPlanDao
import com.mealplanner.data.local.entity.MealPlanEntity
import com.mealplanner.data.local.entity.PlannedRecipeEntity
import com.mealplanner.data.local.entity.RecipeHistoryEntity
import com.mealplanner.domain.model.CookingStep
import com.mealplanner.domain.model.MealPlan
import com.mealplanner.domain.model.PlannedRecipe
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeHistory
import com.mealplanner.domain.model.RecipeIngredient
import com.mealplanner.domain.repository.MealPlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
    private val json: Json
) : MealPlanRepository {

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
}
