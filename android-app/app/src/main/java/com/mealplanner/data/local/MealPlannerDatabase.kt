package com.mealplanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MealPlannerDatabase : RoomDatabase() {
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun preferencesDao(): PreferencesDao
    abstract fun pantryDao(): PantryDao
    abstract fun pendingJobDao(): PendingJobDao

    companion object {
        /**
         * Migration from version 9 to 10: Simplify TrackingStyle from 3 types to 2.
         * - COUNT → UNITS (direct mapping)
         * - PRECISE → UNITS or STOCK_LEVEL (based on category/name patterns)
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // COUNT → UNITS (direct mapping, discrete countable items)
                db.execSQL("UPDATE pantry_items SET trackingStyle = 'UNITS' WHERE trackingStyle = 'COUNT'")

                // PRECISE → STOCK_LEVEL for categories that use stock level tracking
                db.execSQL("""
                    UPDATE pantry_items SET trackingStyle = 'STOCK_LEVEL'
                    WHERE trackingStyle = 'PRECISE'
                    AND category IN ('SPICE', 'OILS', 'CONDIMENT', 'DRY_GOODS', 'FROZEN', 'DAIRY')
                """)

                // PRECISE → STOCK_LEVEL for ground/minced meats (despite PROTEIN category)
                db.execSQL("""
                    UPDATE pantry_items SET trackingStyle = 'STOCK_LEVEL'
                    WHERE trackingStyle = 'PRECISE'
                    AND (LOWER(name) LIKE '%ground%' OR LOWER(name) LIKE '%minced%' OR LOWER(name) LIKE '%mince%')
                """)

                // PRECISE → STOCK_LEVEL for bulk liquids
                db.execSQL("""
                    UPDATE pantry_items SET trackingStyle = 'STOCK_LEVEL'
                    WHERE trackingStyle = 'PRECISE'
                    AND (LOWER(name) LIKE '%milk%' OR LOWER(name) LIKE '%cream%' OR LOWER(name) LIKE '%broth%' OR LOWER(name) LIKE '%stock%')
                """)

                // Remaining PRECISE → UNITS (produce, whole proteins)
                db.execSQL("UPDATE pantry_items SET trackingStyle = 'UNITS' WHERE trackingStyle = 'PRECISE'")
            }
        }
    }
}
