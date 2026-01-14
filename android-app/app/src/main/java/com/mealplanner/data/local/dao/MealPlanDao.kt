package com.mealplanner.data.local.dao

import androidx.room.*
import com.mealplanner.data.local.entity.MealPlanEntity
import com.mealplanner.data.local.entity.PlannedRecipeEntity
import com.mealplanner.data.local.entity.RecipeHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealPlanDao {

    // Meal Plans
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPlan(mealPlan: MealPlanEntity): Long

    @Query("SELECT * FROM meal_plans ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestMealPlan(): MealPlanEntity?

    @Query("SELECT * FROM meal_plans ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestMealPlan(): Flow<MealPlanEntity?>

    @Delete
    suspend fun deleteMealPlan(mealPlan: MealPlanEntity)

    @Query("UPDATE meal_plans SET shoppingComplete = 1 WHERE id = :mealPlanId")
    suspend fun markShoppingComplete(mealPlanId: Long)

    // Planned Recipes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannedRecipes(recipes: List<PlannedRecipeEntity>)

    @Query("SELECT * FROM planned_recipes WHERE mealPlanId = :mealPlanId ORDER BY sequenceIndex")
    suspend fun getPlannedRecipes(mealPlanId: Long): List<PlannedRecipeEntity>

    @Query("SELECT * FROM planned_recipes WHERE mealPlanId = :mealPlanId ORDER BY sequenceIndex")
    fun observePlannedRecipes(mealPlanId: Long): Flow<List<PlannedRecipeEntity>>

    @Query("UPDATE planned_recipes SET cooked = :cooked, cookedAt = :cookedAt WHERE id = :id")
    suspend fun markRecipeCooked(id: Long, cooked: Boolean, cookedAt: Long?)

    @Query("DELETE FROM planned_recipes WHERE mealPlanId = :mealPlanId")
    suspend fun deletePlannedRecipes(mealPlanId: Long)

    // Recipe History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipeHistory(history: RecipeHistoryEntity): Long

    @Query("SELECT * FROM recipe_history WHERE cookedAt > :since ORDER BY cookedAt DESC")
    suspend fun getRecentHistory(since: Long): List<RecipeHistoryEntity>

    @Query("SELECT * FROM recipe_history WHERE recipeName = :name ORDER BY cookedAt DESC LIMIT 1")
    suspend fun getLatestHistoryForRecipe(name: String): RecipeHistoryEntity?

    @Query("UPDATE recipe_history SET rating = :rating, wouldMakeAgain = :wouldMakeAgain, notes = :notes WHERE id = :id")
    suspend fun updateRating(id: Long, rating: Int?, wouldMakeAgain: Boolean?, notes: String?)

    @Query("SELECT recipeHash FROM recipe_history WHERE cookedAt > :since")
    suspend fun getRecentRecipeHashes(since: Long): List<String>

    // Stats and History
    @Query("SELECT * FROM meal_plans ORDER BY createdAt DESC")
    fun observeAllMealPlans(): Flow<List<MealPlanEntity>>

    @Query("SELECT * FROM recipe_history ORDER BY cookedAt DESC")
    fun observeAllHistory(): Flow<List<RecipeHistoryEntity>>

    @Query("SELECT COUNT(*) FROM recipe_history")
    fun observeTotalCookedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM meal_plans")
    fun observeTotalMealPlansCount(): Flow<Int>

    @Query("SELECT AVG(rating) FROM recipe_history WHERE rating IS NOT NULL")
    fun observeAverageRating(): Flow<Double?>

    // Clear all data (for test mode)
    @Query("DELETE FROM meal_plans")
    suspend fun deleteAllMealPlans()

    @Query("DELETE FROM planned_recipes")
    suspend fun deleteAllPlannedRecipes()

    @Query("DELETE FROM recipe_history")
    suspend fun deleteAllHistory()
}
