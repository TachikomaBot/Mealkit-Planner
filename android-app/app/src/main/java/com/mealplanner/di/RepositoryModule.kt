package com.mealplanner.di

import com.mealplanner.data.repository.MealPlanRepositoryImpl
import com.mealplanner.data.repository.PantryRepositoryImpl
import com.mealplanner.data.repository.RecipeRepositoryImpl
import com.mealplanner.data.repository.ShoppingRepositoryImpl
import com.mealplanner.data.repository.UserRepositoryImpl
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.PantryRepository
import com.mealplanner.domain.repository.RecipeRepository
import com.mealplanner.domain.repository.ShoppingRepository
import com.mealplanner.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRecipeRepository(
        impl: RecipeRepositoryImpl
    ): RecipeRepository

    @Binds
    @Singleton
    abstract fun bindMealPlanRepository(
        impl: MealPlanRepositoryImpl
    ): MealPlanRepository

    @Binds
    @Singleton
    abstract fun bindShoppingRepository(
        impl: ShoppingRepositoryImpl
    ): ShoppingRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        impl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindPantryRepository(
        impl: PantryRepositoryImpl
    ): PantryRepository
}
