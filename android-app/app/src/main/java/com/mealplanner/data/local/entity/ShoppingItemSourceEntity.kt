package com.mealplanner.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction table tracking which recipe ingredients each shopping item came from.
 * Used to propagate ingredient substitutions back to recipes.
 */
@Entity(
    tableName = "shopping_item_sources",
    primaryKeys = ["shoppingItemId", "plannedRecipeId", "ingredientIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ShoppingItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["shoppingItemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlannedRecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["plannedRecipeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("shoppingItemId"),
        Index("plannedRecipeId")
    ]
)
data class ShoppingItemSourceEntity(
    val shoppingItemId: Long,
    val plannedRecipeId: Long,
    val ingredientIndex: Int,      // Index in recipe's ingredients list
    val originalName: String,      // Original ingredient name from recipe
    val originalQuantity: Double,
    val originalUnit: String
)
