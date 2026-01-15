package com.mealplanner.data.remote.api

import com.mealplanner.data.remote.dto.GroceryPolishJobResponse
import com.mealplanner.data.remote.dto.GroceryPolishRequest
import com.mealplanner.data.remote.dto.GroceryPolishResponse
import com.mealplanner.data.remote.dto.JobStatusResponse
import com.mealplanner.data.remote.dto.MealPlanRequest
import com.mealplanner.data.remote.dto.MealPlanResponse
import com.mealplanner.data.remote.dto.PantryCategorizeJobResponse
import com.mealplanner.data.remote.dto.PantryCategorizeRequest
import com.mealplanner.data.remote.dto.StartJobResponse
import com.mealplanner.data.remote.dto.SubstitutionRequest
import com.mealplanner.data.remote.dto.SubstitutionResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MealPlanApi {

    @POST("api/meal-plan/generate")
    suspend fun generateMealPlan(
        @Body request: MealPlanRequest
    ): MealPlanResponse

    @POST("api/meal-plan/generate-simple")
    suspend fun generateSimpleMealPlan(
        @Body request: MealPlanRequest
    ): MealPlanResponse

    @POST("api/meal-plan/generate-async")
    suspend fun startMealPlanGeneration(
        @Body request: MealPlanRequest
    ): StartJobResponse

    @GET("api/meal-plan/jobs/{jobId}")
    suspend fun getJobStatus(
        @Path("jobId") jobId: String
    ): JobStatusResponse

    @DELETE("api/meal-plan/jobs/{jobId}")
    suspend fun deleteJob(
        @Path("jobId") jobId: String
    )

    @POST("api/meal-plan/polish-grocery-list")
    suspend fun polishGroceryList(
        @Body request: GroceryPolishRequest
    ): GroceryPolishResponse

    // Async grocery polish endpoints
    @POST("api/meal-plan/polish-grocery-list-async")
    suspend fun startGroceryPolish(
        @Body request: GroceryPolishRequest
    ): StartJobResponse

    @GET("api/meal-plan/grocery-polish-jobs/{jobId}")
    suspend fun getGroceryPolishJobStatus(
        @Path("jobId") jobId: String
    ): GroceryPolishJobResponse

    @DELETE("api/meal-plan/grocery-polish-jobs/{jobId}")
    suspend fun deleteGroceryPolishJob(
        @Path("jobId") jobId: String
    )

    // Pantry categorization endpoints
    @POST("api/meal-plan/categorize-pantry-items-async")
    suspend fun startPantryCategorize(
        @Body request: PantryCategorizeRequest
    ): StartJobResponse

    @GET("api/meal-plan/pantry-categorize-jobs/{jobId}")
    suspend fun getPantryCategorizeJobStatus(
        @Path("jobId") jobId: String
    ): PantryCategorizeJobResponse

    @DELETE("api/meal-plan/pantry-categorize-jobs/{jobId}")
    suspend fun deletePantryCategorizeJob(
        @Path("jobId") jobId: String
    )

    // Ingredient substitution endpoint
    @POST("api/meal-plan/process-substitution")
    suspend fun processSubstitution(
        @Body request: SubstitutionRequest
    ): SubstitutionResponse
}
