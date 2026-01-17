package com.mealplanner.di

import android.content.Context
import androidx.room.Room
import com.mealplanner.data.local.MealPlannerDatabase
import com.mealplanner.data.local.dao.MealPlanDao
import com.mealplanner.data.local.dao.PantryDao
import com.mealplanner.data.local.dao.PendingJobDao
import com.mealplanner.data.local.dao.PreferencesDao
import com.mealplanner.data.local.dao.ShoppingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): MealPlannerDatabase {
        return Room.databaseBuilder(
            context,
            MealPlannerDatabase::class.java,
            "meal_planner.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMealPlanDao(database: MealPlannerDatabase): MealPlanDao {
        return database.mealPlanDao()
    }

    @Provides
    fun provideShoppingDao(database: MealPlannerDatabase): ShoppingDao {
        return database.shoppingDao()
    }

    @Provides
    fun providePreferencesDao(database: MealPlannerDatabase): PreferencesDao {
        return database.preferencesDao()
    }

    @Provides
    fun providePantryDao(database: MealPlannerDatabase): PantryDao {
        return database.pantryDao()
    }

    @Provides
    fun providePendingJobDao(database: MealPlannerDatabase): PendingJobDao {
        return database.pendingJobDao()
    }
}
