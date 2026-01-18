package com.mealplanner.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * How an item's availability should be tracked.
 *
 * Simplified to TWO types based on subdivision behavior:
 * - UNITS: Discrete countable items that maintain identity (apples, eggs, chicken breasts)
 * - STOCK_LEVEL: Items subdivided imprecisely where you'd ask "how much is left?" (ground beef, flour, oil)
 */
enum class TrackingStyle {
    /**
     * Track as discrete countable units (6 apples → use 2 → 4 apples).
     * Best for: produce (apples, onions), portioned proteins (chicken breasts, salmon fillets),
     * packaged items (cans, jars, boxes), eggs.
     */
    UNITS,

    /**
     * Track as stock level (out, low, some, plenty).
     * Best for: items subdivided imprecisely where you'd ask "how much is left?" after use.
     * Examples: ground beef, milk, flour, rice, oils, spices, condiments.
     */
    STOCK_LEVEL
}

/**
 * Stock level for items tracked via STOCK_LEVEL style
 */
enum class StockLevel(val displayName: String) {
    OUT_OF_STOCK("Out"),
    LOW("Low"),
    SOME("Some"),
    PLENTY("Plenty");

    companion object {
        fun fromPercentage(percent: Float): StockLevel = when {
            percent <= 0f -> OUT_OF_STOCK
            percent < 0.25f -> LOW
            percent < 0.6f -> SOME
            else -> PLENTY
        }

        fun fromString(value: String): StockLevel {
            return entries.find { it.name.equals(value, ignoreCase = true) }
                ?: SOME
        }
    }
}

/**
 * A pantry ingredient with flexible quantity tracking
 */
data class PantryItem(
    val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val quantityInitial: Double,
    val quantityRemaining: Double,
    val unit: PantryUnit,
    val category: PantryCategory,
    val trackingStyle: TrackingStyle = TrackingStyle.UNITS,
    val stockLevel: StockLevel = StockLevel.PLENTY,
    val perishable: Boolean = false,
    val expiryDate: LocalDate? = null,
    val dateAdded: LocalDateTime = LocalDateTime.now(),
    val lastUpdated: LocalDateTime = LocalDateTime.now(),
    val lastStockCheck: LocalDateTime? = null,
    val imageUrl: String? = null
) {
    val percentRemaining: Float
        get() = if (quantityInitial > 0) {
            (quantityRemaining / quantityInitial).toFloat().coerceIn(0f, 1f)
        } else 0f

    val isLowStock: Boolean
        get() = percentRemaining < 0.2f

    /**
     * Whether this item needs attention - expiring soon, getting old, or partially used.
     * Used for the "Use Soon" filter in the pantry.
     */
    val needsAttention: Boolean
        get() {
            if (!perishable) return false

            val now = LocalDateTime.now()
            val today = LocalDate.now()
            val oneDayAgo = now.minusDays(1)
            val threeDaysAgo = now.minusDays(3)
            val twoDaysFromNow = today.plusDays(2)

            // If checked within the last 3 days, skip
            if (lastStockCheck != null && lastStockCheck > threeDaysAgo) {
                return false
            }

            // Grace period: don't flag items added today or yesterday for expiry
            val isNewlyPurchased = dateAdded > oneDayAgo

            // Check if expiring within 2 days (more urgent threshold)
            // But give newly purchased items a pass - user just bought them
            if (expiryDate != null && !expiryDate.isAfter(twoDaysFromNow) && !isNewlyPurchased) {
                return true
            }

            // Check if added more than 3 days ago (might be stale)
            if (dateAdded <= threeDaysAgo) {
                return true
            }

            // Check if partially consumed (should use the rest)
            if (quantityRemaining < quantityInitial) {
                return true
            }

            return false
        }

    // Keep old name for backwards compatibility during transition
    @Deprecated("Use needsAttention instead", ReplaceWith("needsAttention"))
    val needsStockCheck: Boolean get() = needsAttention

    /**
     * Get the effective stock level, computed from quantity for UNITS items
     */
    val effectiveStockLevel: StockLevel
        get() = when (trackingStyle) {
            TrackingStyle.STOCK_LEVEL -> stockLevel
            TrackingStyle.UNITS -> StockLevel.fromPercentage(percentRemaining)
        }

    /**
     * Human-readable availability description for display
     */
    val availabilityDescription: String
        get() = when (trackingStyle) {
            TrackingStyle.STOCK_LEVEL -> stockLevel.displayName
            TrackingStyle.UNITS -> {
                val qty = quantityRemaining.toInt()
                val unitStr = unit.pluralize(qty)
                "$qty $unitStr"
            }
        }

    companion object {
        // Patterns for items that should use STOCK_LEVEL (subdivided imprecisely)
        private val stockLevelPatterns = listOf(
            // Bulk proteins (ground/minced meat)
            "ground", "minced", "mince",
            // Dairy (poured/sliced)
            "milk", "cream", "yogurt", "cheese", "butter",
            // Bulk dry goods (scooped from bulk)
            "flour", "sugar", "rice", "pasta", "oats",
            // Liquids
            "oil", "vinegar", "sauce", "broth", "stock"
        )

        // Patterns for items that should use UNITS (discrete countable)
        private val unitsPatterns = listOf(
            // Produce
            "egg", "apple", "onion", "carrot", "potato", "lemon", "lime", "tomato",
            // Portioned proteins
            "chicken breast", "salmon fillet", "steak", "chop", "thigh", "drumstick",
            // Packaged items
            "can of", "canned", "jar", "bottle", "box", "packet", "package", "carton", "tin"
        )

        /**
         * Determine the smart default tracking style based on category and item name.
         * Uses simplified TWO-type system:
         * - UNITS: discrete countable items that maintain identity
         * - STOCK_LEVEL: items subdivided imprecisely
         */
        fun smartTrackingStyle(name: String, category: PantryCategory): TrackingStyle {
            val lowerName = name.lowercase()

            // Check for STOCK_LEVEL patterns first (subdivided imprecisely)
            if (stockLevelPatterns.any { lowerName.contains(it) }) {
                return TrackingStyle.STOCK_LEVEL
            }

            // Check for UNITS patterns (discrete countable)
            if (unitsPatterns.any { lowerName.contains(it) }) {
                return TrackingStyle.UNITS
            }

            // Category-based defaults
            return when (category) {
                // STOCK_LEVEL categories (subdivided imprecisely)
                PantryCategory.SPICE,
                PantryCategory.OILS,
                PantryCategory.CONDIMENT,
                PantryCategory.DRY_GOODS,
                PantryCategory.FROZEN,
                PantryCategory.DAIRY -> TrackingStyle.STOCK_LEVEL

                // UNITS categories (discrete countable)
                PantryCategory.PRODUCE,
                PantryCategory.PROTEIN,
                PantryCategory.OTHER -> TrackingStyle.UNITS
            }
        }
    }
}

enum class PantryUnit(val displayName: String, val pluralName: String) {
    GRAMS("g", "g"),                    // Already abbreviated, no change
    MILLILITERS("ml", "ml"),            // Already abbreviated, no change
    UNITS("unit", "units"),
    BUNCH("bunch", "bunches");

    /**
     * Get the appropriate singular or plural form based on quantity
     */
    fun pluralize(quantity: Int): String {
        return if (quantity == 1) displayName else pluralName
    }

    companion object {
        fun fromString(value: String): PantryUnit {
            return entries.find {
                it.name.equals(value, ignoreCase = true) ||
                it.displayName == value ||
                it.pluralName == value
            } ?: UNITS
        }
    }
}

enum class PantryCategory(val displayName: String) {
    PRODUCE("Produce"),
    PROTEIN("Protein"),
    DAIRY("Dairy"),
    DRY_GOODS("Dry Goods"),
    SPICE("Dried Herbs & Spices"),
    OILS("Oils"),
    CONDIMENT("Condiments"),
    FROZEN("Frozen"),
    OTHER("Other");

    val isPerishable: Boolean
        get() = this in listOf(PRODUCE, PROTEIN, DAIRY)

    companion object {
        fun fromString(value: String): PantryCategory {
            return entries.find {
                it.name.equals(value.replace(" ", "_"), ignoreCase = true) ||
                it.displayName.equals(value, ignoreCase = true)
            } ?: OTHER
        }
    }
}
