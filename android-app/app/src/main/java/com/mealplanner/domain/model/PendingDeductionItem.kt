package com.mealplanner.domain.model

/**
 * Represents an ingredient pending deduction from pantry when cooking a recipe.
 * Used in the confirmation screen before deducting ingredients.
 */
data class PendingDeductionItem(
    val id: Long,                           // Index from recipe ingredients
    val ingredientName: String,             // Name from recipe
    val originalQuantity: Double,           // Quantity from recipe
    val editedQuantity: Double,             // User-edited quantity (for COUNT/PRECISE)
    val unit: String,                       // Unit from recipe
    val isRemoved: Boolean = false,         // Whether user skipped this

    // Pantry match info
    val pantryItemId: Long? = null,         // ID of matched pantry item (null if no match)
    val pantryItemName: String? = null,     // Name of matched pantry item for display
    val trackingStyle: TrackingStyle? = null, // How the pantry item is tracked
    val currentStockLevel: StockLevel? = null, // For STOCK_LEVEL items
    val targetStockLevel: StockLevel? = null  // User-selected target level (null = no change)
) {
    /** Whether this ingredient has a matching pantry item */
    val hasPantryMatch: Boolean get() = pantryItemId != null

    /** Whether this is tracked by stock level (vs. quantity) */
    val isStockLevelItem: Boolean get() = trackingStyle == TrackingStyle.STOCK_LEVEL

    /** Whether the user modified the quantity or stock level */
    val isModified: Boolean get() = editedQuantity != originalQuantity ||
        (isStockLevelItem && targetStockLevel != null && targetStockLevel != currentStockLevel)

    /** Human-readable quantity display */
    val displayQuantity: String get() = if (editedQuantity > 0) {
        "${formatQuantity(editedQuantity)} ${unit}".trim()
    } else ""

    /** Whether a stock level change is pending */
    val hasStockLevelChange: Boolean get() =
        isStockLevelItem && targetStockLevel != null && targetStockLevel != currentStockLevel
}

/**
 * Format a quantity for display, removing unnecessary decimal places.
 */
private fun formatQuantity(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        // Format with up to 2 decimal places, trimming trailing zeros
        String.format("%.2f", value).trimEnd('0').trimEnd('.')
    }
}
