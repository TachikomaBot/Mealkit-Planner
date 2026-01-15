package com.mealplanner.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MealPlanRequest(
    val pantryItems: List<PantryItemDto> = emptyList(),
    val preferences: PreferencesDto? = null,
    val recentRecipeHashes: List<String> = emptyList()
)

@Serializable
data class PantryItemDto(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val availability: String? = null // "plenty", "some", "low", "out" for stock-level items
)

@Serializable
data class PreferencesDto(
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val summary: String? = null,
    val targetServings: Int = 2
)

@Serializable
data class MealPlanResponse(
    val recipes: List<GeneratedRecipeDto>,
    val defaultSelections: List<Int>
)

@Serializable
data class GeneratedRecipeDto(
    val name: String,
    val description: String,
    val servings: Int,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val ingredients: List<RecipeIngredientDto>,
    val steps: List<CookingStepDto>,
    val tags: List<String>,
    val usesExpiringIngredients: Boolean = false,
    val expiringIngredientsUsed: List<String> = emptyList(),
    val sourceRecipeIds: List<Int> = emptyList()
)

@Serializable
data class RecipeIngredientDto(
    val ingredientName: String,
    val quantity: Double? = null, // Nullable - Gemini returns null for "to taste" ingredients
    val unit: String? = null,
    val preparation: String? = null
)

@Serializable
data class CookingStepDto(
    val title: String,
    val substeps: List<String>
)

// SSE Progress events
@Serializable
data class ProgressEventDto(
    val type: String, // "connected", "progress", "complete", "error"
    val phase: String? = null,
    val current: Int? = null,
    val total: Int? = null,
    val message: String? = null,
    val day: Int? = null,
    val result: MealPlanResponse? = null,
    val error: String? = null
)

// Async job DTOs
@Serializable
data class StartJobResponse(
    val jobId: String
)

@Serializable
data class JobStatusResponse(
    val id: String,
    val status: String, // "pending", "running", "completed", "failed"
    val progress: JobProgressDto? = null,
    val result: MealPlanResponse? = null,
    val error: String? = null
)

@Serializable
data class JobProgressDto(
    val phase: String,
    val current: Int,
    val total: Int,
    val message: String? = null
)

// Grocery polish DTOs
@Serializable
data class GroceryPolishRequest(
    val ingredients: List<GroceryIngredientDto>,
    val pantryItems: List<PantryItemDto> = emptyList()
)

@Serializable
data class GroceryIngredientDto(
    val id: Long,
    val name: String,
    val quantity: Double,
    val unit: String
)

@Serializable
data class GroceryPolishResponse(
    val items: List<PolishedGroceryItemDto>
)

@Serializable
data class PolishedGroceryItemDto(
    val name: String,
    val displayQuantity: String,
    val category: String
)

// Async grocery polish job DTOs
@Serializable
data class GroceryPolishJobResponse(
    val id: String,
    val status: String, // "pending", "running", "completed", "failed"
    val progress: GroceryPolishProgressDto? = null,
    val result: GroceryPolishResponse? = null,
    val error: String? = null
)

@Serializable
data class GroceryPolishProgressDto(
    val phase: String,
    val currentBatch: Int,
    val totalBatches: Int,
    val message: String? = null
)

// Pantry categorization DTOs
@Serializable
data class PantryCategorizeRequest(
    val items: List<ShoppingItemForPantryDto>
)

@Serializable
data class ShoppingItemForPantryDto(
    val id: Long,
    val name: String,
    val polishedDisplayQuantity: String,
    val shoppingCategory: String
)

@Serializable
data class PantryCategorizeResponse(
    val items: List<CategorizedPantryItemDto>
)

@Serializable
data class CategorizedPantryItemDto(
    val id: Long,
    val name: String,
    val quantity: Double,
    val unit: String,        // GRAMS, MILLILITERS, UNITS, PIECES, BUNCH
    val category: String,    // PRODUCE, PROTEIN, DAIRY, DRY_GOODS, SPICE, OILS, CONDIMENT, FROZEN, OTHER
    val trackingStyle: String,  // STOCK_LEVEL, COUNT, PRECISE
    val stockLevel: String? = null,  // FULL, HIGH, MEDIUM, LOW (for STOCK_LEVEL tracking)
    val expiryDays: Int? = null,
    val perishable: Boolean
)

@Serializable
data class PantryCategorizeJobResponse(
    val id: String,
    val status: String,  // "pending", "running", "completed", "failed"
    val progress: PantryCategorizeProgressDto? = null,
    val result: PantryCategorizeResponse? = null,
    val error: String? = null
)

@Serializable
data class PantryCategorizeProgressDto(
    val phase: String,
    val current: Int,
    val total: Int,
    val message: String? = null
)
