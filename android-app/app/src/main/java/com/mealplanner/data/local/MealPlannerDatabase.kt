package com.mealplanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mealplanner.data.local.dao.MealPlanDao
import com.mealplanner.data.local.dao.PantryDao
import com.mealplanner.data.local.dao.PendingJobDao
import com.mealplanner.data.local.dao.PreferencesDao
import com.mealplanner.data.local.dao.ShoppingDao
import com.mealplanner.data.local.entity.*

@Database(
    entities = [
        MealPlanEntity::class,
        PlannedRecipeEntity::class,
        RecipeHistoryEntity::class,
        ShoppingItemEntity::class,
        ShoppingItemSourceEntity::class,
        UserPreferencesEntity::class,
        PreferenceSummaryEntity::class,
        PantryEntity::class,
        PendingJobEntity::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MealPlannerDatabase : RoomDatabase() {
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun preferencesDao(): PreferencesDao
    abstract fun pantryDao(): PantryDao
    abstract fun pendingJobDao(): PendingJobDao
}
