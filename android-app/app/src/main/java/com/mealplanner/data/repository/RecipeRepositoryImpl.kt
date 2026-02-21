package com.mealplanner.data.repository

import com.mealplanner.data.local.dao.PendingJobDao
import com.mealplanner.data.local.entity.PendingJobEntity
import com.mealplanner.data.local.entity.PendingJobType
import com.mealplanner.data.remote.api.MealPlanApi
import com.mealplanner.data.remote.api.RecipeApi
import com.mealplanner.data.remote.dto.CookingStepDto
import com.mealplanner.data.remote.dto.GeneratedRecipeDto
import com.mealplanner.data.remote.dto.MealPlanRequest
import com.mealplanner.data.remote.dto.PreferencesDto
import com.mealplanner.data.remote.dto.RecipeDto
import com.mealplanner.data.remote.dto.RecipeIngredientDto
import com.mealplanner.domain.model.CookingStep
import com.mealplanner.domain.model.GeneratedMealPlan
import com.mealplanner.domain.model.GenerationPhase
import com.mealplanner.domain.model.GenerationProgress
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeIngredient
import com.mealplanner.domain.model.RecipeSearchResult
import com.mealplanner.domain.model.UserPreferences
import com.mealplanner.domain.repository.GenerationResult
import com.mealplanner.domain.repository.RecipeRepository
import com.mealplanner.domain.repository.RecipeStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepositoryImpl @Inject constructor(
    private val recipeApi: RecipeApi,
    private val mealPlanApi: MealPlanApi,
    private val pendingJobDao: PendingJobDao
) : RecipeRepository {

    override suspend fun searchRecipes(
        query: String?,
        category: String?,
        cuisines: List<String>?,
        maxTime: Int?,
        limit: Int,
        offset: Int,
        random: Boolean
    ): Result<List<RecipeSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val response = recipeApi.searchRecipes(
                query = query,
                category = category,
                cuisines = cuisines?.joinToString(","),
                maxTotalTime = maxTime,
                limit = limit,
                offset = offset,
                random = random
            )
            Result.success(response.results.map { it.toSearchResult() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStats(): Result<RecipeStats> = withContext(Dispatchers.IO) {
        try {
            val response = recipeApi.getStats()
            Result.success(
                RecipeStats(
                    total = response.total,
                    categories = response.categories,
                    cuisines = response.cuisines
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRecipeById(id: Int): Result<Recipe> = withContext(Dispatchers.IO) {
        try {
            val response = recipeApi.getRecipeById(id)
            Result.success(response.toRecipe())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun generateMealPlan(
        preferences: UserPreferences,
        recentRecipeHashes: List<String>,
        leftoversInput: String
    ): Flow<GenerationResult> = flow {
        // API key is handled by backend
        
        // Emit connecting status
        emit(
            GenerationResult.Progress(
                GenerationProgress(
                    phase = GenerationPhase.CONNECTING,
                    current = 0,
                    total = 1,
                    message = "Starting meal plan generation..."
                )
            )
        )

        try {
            // Start the async job
            val request = MealPlanRequest(
                leftoversInput = leftoversInput,
                preferences = PreferencesDto(
                    likes = preferences.likes,
                    dislikes = preferences.dislikes,
                    targetServings = preferences.targetServings
                ),
                recentRecipeHashes = recentRecipeHashes
            )

            val startResponse = withContext(Dispatchers.IO) {
                mealPlanApi.startMealPlanGeneration(request)
            }

            val jobId = startResponse.jobId

            // Persist job to DB so we can resume if app goes to background
            withContext(Dispatchers.IO) {
                pendingJobDao.insert(PendingJobEntity(
                    jobId = jobId,
                    jobType = PendingJobType.MEAL_GENERATION,
                    relatedId = 0  // No specific related ID for generation
                ))
            }

            // Poll for status every 2 seconds
            var lastPhase: String? = null
            while (currentCoroutineContext().isActive) {
                delay(2000) // Poll every 2 seconds

                val status = withContext(Dispatchers.IO) {
                    mealPlanApi.getJobStatus(jobId)
                }

                when (status.status) {
                    "pending" -> {
                        emit(
                            GenerationResult.Progress(
                                GenerationProgress(
                                    phase = GenerationPhase.CONNECTING,
                                    current = 0,
                                    total = 1,
                                    message = "Waiting to start..."
                                )
                            )
                        )
                    }
                    "running" -> {
                        val progress = status.progress
                        val phase = when (progress?.phase) {
                            "planning" -> GenerationPhase.PLANNING
                            "building" -> GenerationPhase.BUILDING
                            "generating_images" -> GenerationPhase.GENERATING_IMAGES
                            else -> GenerationPhase.BUILDING
                        }
                        // Only emit if phase changed or progress updated
                        if (progress != null && (progress.phase != lastPhase || progress.current > 0)) {
                            lastPhase = progress.phase
                            emit(
                                GenerationResult.Progress(
                                    GenerationProgress(
                                        phase = phase,
                                        current = progress.current,
                                        total = progress.total,
                                        message = progress.message
                                    )
                                )
                            )
                        }
                    }
                    "completed" -> {
                        status.result?.let { result ->
                            emit(
                                GenerationResult.Success(
                                    GeneratedMealPlan(
                                        recipes = result.recipes.map { it.toRecipe() },
                                        defaultSelections = result.defaultSelections
                                    )
                                )
                            )
                        } ?: emit(GenerationResult.Error("Job completed but no result returned"))

                        // Clean up the job on the server and from DB
                        withContext(Dispatchers.IO) {
                            pendingJobDao.delete(jobId)
                            try { mealPlanApi.deleteJob(jobId) } catch (_: Exception) {}
                        }
                        return@flow
                    }
                    "failed" -> {
                        emit(GenerationResult.Error(status.error ?: "Job failed"))

                        // Clean up the job on the server and from DB
                        withContext(Dispatchers.IO) {
                            pendingJobDao.delete(jobId)
                            try { mealPlanApi.deleteJob(jobId) } catch (_: Exception) {}
                        }
                        return@flow
                    }
                }
            }
        } catch (e: Exception) {
            emit(GenerationResult.Error(e.message ?: "Connection failed"))
        }
    }

    override suspend fun generateSimpleMealPlan(): Result<GeneratedMealPlan> = withContext(Dispatchers.IO) {
        try {
            val response = mealPlanApi.generateSimpleMealPlan(
                MealPlanRequest()
            )
            Result.success(
                GeneratedMealPlan(
                    recipes = response.recipes.map { it.toRecipe() },
                    defaultSelections = response.defaultSelections
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Mappers
    private fun RecipeDto.toSearchResult() = RecipeSearchResult(
        sourceId = sourceId,
        name = name,
        description = description,
        servings = servings,
        totalTimeMinutes = totalTimeMinutes,
        tags = tags,
        cuisines = cuisines,
        category = category,
        score = score
    )

    private fun RecipeDto.toRecipe() = Recipe(
        id = sourceId.toString(),
        name = name,
        description = description,
        servings = servings,
        prepTimeMinutes = totalTimeMinutes?.div(2) ?: 0,
        cookTimeMinutes = totalTimeMinutes?.div(2) ?: 0,
        ingredients = ingredients.map { it.toIngredient() },
        steps = steps.mapIndexed { index, step ->
            CookingStep(
                title = "Step ${index + 1}",
                substeps = listOf(step)
            )
        },
        tags = tags,
        sourceRecipeIds = listOf(sourceId)
    )

    private fun com.mealplanner.data.remote.dto.IngredientDto.toIngredient() = RecipeIngredient(
        name = name,
        quantity = quantity ?: 0.0,
        unit = unit ?: "",
        preparation = preparation
    )

    private fun GeneratedRecipeDto.toRecipe() = Recipe(
        id = UUID.randomUUID().toString(),
        name = name,
        description = description,
        servings = servings,
        prepTimeMinutes = prepTimeMinutes,
        cookTimeMinutes = cookTimeMinutes,
        ingredients = ingredients.map { it.toIngredient() },
        steps = steps.map { it.toStep() },
        tags = tags,
        sourceRecipeIds = sourceRecipeIds
    )

    private fun RecipeIngredientDto.toIngredient() = RecipeIngredient(
        name = ingredientName,
        quantity = quantity ?: 0.0, // Default null (e.g., "to taste") to 0
        unit = unit ?: "",
        preparation = preparation
    )

    private fun CookingStepDto.toStep() = CookingStep(
        title = title,
        substeps = substeps
    )

    /**
     * Check if there's a pending meal generation job and resume polling if so.
     * Call this when app resumes from background.
     */
    override fun checkAndResumePendingGeneration(): Flow<GenerationResult>? {
        // Check synchronously if there's a pending job - we need to return Flow or null
        // This is a blocking call but it's quick (just DB read)
        val pendingJob = kotlinx.coroutines.runBlocking {
            pendingJobDao.getByType(PendingJobType.MEAL_GENERATION)
        } ?: return null

        val jobId = pendingJob.jobId
        android.util.Log.d("RecipeRepo", "Resuming pending generation job: $jobId")

        return flow {
            try {
                // Poll for status every 2 seconds
                var lastPhase: String? = null
                while (currentCoroutineContext().isActive) {
                    delay(2000)

                    val status = withContext(Dispatchers.IO) {
                        mealPlanApi.getJobStatus(jobId)
                    }

                    when (status.status) {
                        "pending" -> {
                            emit(
                                GenerationResult.Progress(
                                    GenerationProgress(
                                        phase = GenerationPhase.CONNECTING,
                                        current = 0,
                                        total = 1,
                                        message = "Resuming generation..."
                                    )
                                )
                            )
                        }
                        "running" -> {
                            val progress = status.progress
                            val phase = when (progress?.phase) {
                                "planning" -> GenerationPhase.PLANNING
                                "building" -> GenerationPhase.BUILDING
                                "generating_images" -> GenerationPhase.GENERATING_IMAGES
                                else -> GenerationPhase.BUILDING
                            }
                            if (progress != null && (progress.phase != lastPhase || progress.current > 0)) {
                                lastPhase = progress.phase
                                emit(
                                    GenerationResult.Progress(
                                        GenerationProgress(
                                            phase = phase,
                                            current = progress.current,
                                            total = progress.total,
                                            message = progress.message
                                        )
                                    )
                                )
                            }
                        }
                        "completed" -> {
                            status.result?.let { result ->
                                emit(
                                    GenerationResult.Success(
                                        GeneratedMealPlan(
                                            recipes = result.recipes.map { it.toRecipe() },
                                            defaultSelections = result.defaultSelections
                                        )
                                    )
                                )
                            } ?: emit(GenerationResult.Error("Job completed but no result returned"))

                            // Clean up
                            withContext(Dispatchers.IO) {
                                pendingJobDao.delete(jobId)
                                try { mealPlanApi.deleteJob(jobId) } catch (_: Exception) {}
                            }
                            return@flow
                        }
                        "failed" -> {
                            emit(GenerationResult.Error(status.error ?: "Job failed"))

                            // Clean up
                            withContext(Dispatchers.IO) {
                                pendingJobDao.delete(jobId)
                                try { mealPlanApi.deleteJob(jobId) } catch (_: Exception) {}
                            }
                            return@flow
                        }
                    }
                }
            } catch (e: Exception) {
                // Clean up on error
                withContext(Dispatchers.IO) {
                    pendingJobDao.delete(jobId)
                }
                emit(GenerationResult.Error(e.message ?: "Resume failed"))
            }
        }
    }
}
