package com.mealplanner.domain.model

/**
 * Result of AI recipe customization request.
 * Contains proposed changes for user review before applying.
 */
data class RecipeCustomizationResult(
    val updatedRecipeName: String,
    val ingredientsToAdd: List<RecipeIngredient>,
    val ingredientsToRemove: List<String>,  // Names of ingredients to remove
    val ingredientsToModify: List<ModifiedIngredient>,
    val updatedSteps: List<CookingStep>,
    val changesSummary: String,
    val notes: String? = null
)

/**
 * An ingredient that has been modified (quantity, unit, or preparation changed)
 */
data class ModifiedIngredient(
    val originalName: String,
    val newName: String?,  // null if name unchanged
    val newQuantity: Double?,
    val newUnit: String?,
    val newPreparation: String?
)
