package com.mealplanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meal_plans")
data class MealPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val shoppingComplete: Boolean = false
)

@Entity(tableName = "planned_recipes")
data class PlannedRecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mealPlanId: Long,
    val recipeName: String,
    val recipeJson: String, // Full recipe stored as JSON
    val sequenceIndex: Int = 0, // Order within the meal plan
    val cooked: Boolean = false,
    val cookedAt: Long? = null
)

@Entity(tableName = "recipe_history")
data class RecipeHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recipeName: String,
    val recipeHash: String,
    val cookedAt: Long,
    val rating: Int?, // 1-5
    val wouldMakeAgain: Boolean?,
    val notes: String?
)
