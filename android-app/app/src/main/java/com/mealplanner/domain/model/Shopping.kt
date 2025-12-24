package com.mealplanner.domain.model

/**
 * A shopping list containing items grouped by category
 */
data class ShoppingList(
    val mealPlanId: Long,
    val items: List<ShoppingItem>,
    val generatedAt: Long
) {
    val categories: List<String> get() = items.map { it.category }.distinct().sorted()

    val totalItems: Int get() = items.size
    val checkedItems: Int get() = items.count { it.checked }
    val progress: Float get() = if (totalItems > 0) checkedItems.toFloat() / totalItems else 0f

    fun itemsByCategory(category: String): List<ShoppingItem> =
        items.filter { it.category == category }
}

/**
 * A single item on the shopping list
 */
data class ShoppingItem(
    val id: Long,
    val name: String,
    val quantity: Double,
    val unit: String,
    val category: String,
    val checked: Boolean = false,
    val inCart: Boolean = false,
    val notes: String? = null,
    val polishedDisplayQuantity: String? = null // From Gemini polish endpoint
) {
    // Use polished display if available, otherwise format the raw quantity
    val displayQuantity: String get() = polishedDisplayQuantity ?: run {
        val formattedQty = formatQuantityAsFraction(quantity)
        if (unit.isNotBlank()) "$formattedQty $unit" else formattedQty
    }

    val displayText: String get() = "$displayQuantity $name"

    companion object {
        /**
         * Format a quantity for display, converting decimals to fractions where appropriate.
         */
        private fun formatQuantityAsFraction(quantity: Double): String {
            if (quantity <= 0) return ""

            val wholePart = quantity.toLong()
            val fractionalPart = quantity - wholePart

            // If it's a whole number, just return it
            if (fractionalPart < 0.01) {
                return wholePart.toString()
            }

            // Convert fractional part to a fraction string
            val fractionStr = when {
                fractionalPart in 0.12..0.13 -> "1/8"
                fractionalPart in 0.24..0.26 -> "1/4"
                fractionalPart in 0.32..0.34 -> "1/3"
                fractionalPart in 0.37..0.38 -> "3/8"
                fractionalPart in 0.49..0.51 -> "1/2"
                fractionalPart in 0.62..0.63 -> "5/8"
                fractionalPart in 0.66..0.68 -> "2/3"
                fractionalPart in 0.74..0.76 -> "3/4"
                fractionalPart in 0.87..0.88 -> "7/8"
                else -> null
            }

            return when {
                fractionStr != null && wholePart > 0 -> "$wholePart $fractionStr"
                fractionStr != null -> fractionStr
                else -> String.format("%.1f", quantity)
            }
        }
    }
}

/**
 * Category groupings for shopping items
 */
object ShoppingCategories {
    const val PRODUCE = "Produce"
    const val PROTEIN = "Protein"
    const val DAIRY = "Dairy"
    const val PANTRY = "Pantry"
    const val FROZEN = "Frozen"
    const val BAKERY = "Bakery"
    const val CONDIMENTS = "Condiments"
    const val SPICES = "Spices"
    const val OTHER = "Other"

    val orderedCategories = listOf(
        PRODUCE, PROTEIN, DAIRY, BAKERY, FROZEN, PANTRY, CONDIMENTS, SPICES, OTHER
    )
}