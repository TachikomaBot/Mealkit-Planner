package com.mealplanner.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * How an item's availability should be tracked
 */
enum class TrackingStyle {
    /**
     * Track as stock level (out, low, some, plenty).
     * Best for: spices, oils, condiments - items where precise quantity doesn't matter.
     */
    STOCK_LEVEL,

    /**
     * Track as discrete count (2 cans, 3 bottles).
     * Best for: canned goods, jars, boxes - items bought in units.
     */
    COUNT,

    /**
     * Track precise quantity (500g, 1L).
     * Best for: perishables, proteins, produce - items where amount matters.
     */
    PRECISE
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
    val trackingStyle: TrackingStyle = TrackingStyle.PRECISE,
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

    val needsStockCheck: Boolean
        get() {
            if (!perishable) return false

            val now = LocalDateTime.now()
            val threeDaysAgo = now.minusDays(3)
            val threeDaysFromNow = LocalDate.now().plusDays(3)

            // If checked within the last 3 days, skip
            if (lastStockCheck != null && lastStockCheck > threeDaysAgo) {
                return false
            }

            // Check if expiring within 3 days
            if (expiryDate != null && !expiryDate.isAfter(threeDaysFromNow)) {
                return true
            }

            // Check if added more than 3 days ago (might be stale)
            if (dateAdded <= threeDaysAgo) {
                return true
            }

            // Check if partially consumed
            if (quantityRemaining < quantityInitial) {
                return true
            }

            return false
        }

    /**
     * Get the effective stock level, computed from quantity for PRECISE/COUNT items
     */
    val effectiveStockLevel: StockLevel
        get() = when (trackingStyle) {
            TrackingStyle.STOCK_LEVEL -> stockLevel
            TrackingStyle.COUNT, TrackingStyle.PRECISE -> StockLevel.fromPercentage(percentRemaining)
        }

    /**
     * Human-readable availability description for display
     */
    val availabilityDescription: String
        get() = when (trackingStyle) {
            TrackingStyle.STOCK_LEVEL -> stockLevel.displayName
            TrackingStyle.COUNT -> "${quantityRemaining.toInt()} ${unit.displayName}"
            TrackingStyle.PRECISE -> "${quantityRemaining.toInt()}${unit.displayName}"
        }

    companion object {
        // Patterns for items that should use COUNT tracking (discrete countable items)
        private val countPatterns = listOf(
            "canned", "can of", "jar", "bottle", "box", "packet", "package",
            "bag of", "carton", "tin"
        )

        // Patterns for items that should always use PRECISE tracking regardless of category
        private val precisePatterns = listOf(
            "chicken", "beef", "pork", "lamb", "fish", "salmon", "shrimp", "turkey",
            "milk", "cream", "yogurt", "cheese", "egg"
        )

        /**
         * Determine the smart default tracking style based on category and item name.
         * Users can override this, but this provides sensible defaults.
         */
        fun smartTrackingStyle(name: String, category: PantryCategory): TrackingStyle {
            val lowerName = name.lowercase()

            // Check for precise patterns first (meats, dairy, etc.)
            if (precisePatterns.any { lowerName.contains(it) }) {
                return TrackingStyle.PRECISE
            }

            // Check for count patterns (canned goods, jars, etc.)
            if (countPatterns.any { lowerName.contains(it) }) {
                return TrackingStyle.COUNT
            }

            // Category-based defaults
            return when (category) {
                // Shelf-stable items where precise quantity doesn't matter
                PantryCategory.SPICE,
                PantryCategory.OILS,
                PantryCategory.CONDIMENT -> TrackingStyle.STOCK_LEVEL

                // Dry goods default to stock level, but canned items handled above
                PantryCategory.DRY_GOODS -> {
                    // Items like flour, rice, sugar, pasta - stock level is fine
                    if (lowerName.contains("flour") || lowerName.contains("rice") ||
                        lowerName.contains("sugar") || lowerName.contains("pasta") ||
                        lowerName.contains("cereal") || lowerName.contains("oats")
                    ) {
                        TrackingStyle.STOCK_LEVEL
                    } else {
                        // Default to precise for unknown dry goods
                        TrackingStyle.PRECISE
                    }
                }

                // Perishables need precise tracking
                PantryCategory.PRODUCE,
                PantryCategory.PROTEIN,
                PantryCategory.DAIRY,
                PantryCategory.FROZEN -> TrackingStyle.PRECISE

                PantryCategory.OTHER -> TrackingStyle.PRECISE
            }
        }
    }
}

enum class PantryUnit(val displayName: String) {
    GRAMS("g"),
    MILLILITERS("ml"),
    UNITS("units"),
    BUNCH("bunch");

    companion object {
        fun fromString(value: String): PantryUnit {
            return entries.find { it.name.equals(value, ignoreCase = true) || it.displayName == value }
                ?: UNITS
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
