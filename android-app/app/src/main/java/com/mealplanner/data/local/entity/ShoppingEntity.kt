package com.mealplanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mealPlanId: Long,
    val ingredientName: String,
    val quantity: Double,
    val unit: String,
    val category: String,
    val checked: Boolean = false,
    val inCart: Boolean = false,
    val notes: String?,
    val polishedDisplayQuantity: String? = null // Set by Gemini polish endpoint
)
