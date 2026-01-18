package com.mealplanner.domain.usecase

import com.mealplanner.data.remote.dto.CategorizedPantryItemDto
import com.mealplanner.domain.model.PantryCategory
import com.mealplanner.domain.model.PantryItem
import com.mealplanner.domain.model.PantryUnit
import com.mealplanner.domain.model.ShoppingCategories
import com.mealplanner.domain.model.ShoppingItem
import com.mealplanner.domain.model.ShoppingList
import com.mealplanner.domain.model.StockLevel
import com.mealplanner.domain.model.TrackingStyle
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.PantryRepository
import com.mealplanner.domain.repository.ShoppingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Use case for managing shopping lists
 */
class ManageShoppingListUseCase @Inject constructor(
    private val shoppingRepository: ShoppingRepository,
    private val mealPlanRepository: MealPlanRepository,
    private val pantryRepository: PantryRepository
) {
    /**
     * Observe the shopping list for the current meal plan
     */
    fun observeCurrentShoppingList(): Flow<ShoppingList?> {
        return kotlinx.coroutines.flow.flow {
            val mealPlan = mealPlanRepository.observeCurrentMealPlan().first()
            if (mealPlan != null) {
                shoppingRepository.observeShoppingList(mealPlan.id).collect { emit(it) }
            } else {
                emit(null)
            }
        }
    }

    /**
     * Observe shopping list for a specific meal plan
     */
    fun observeShoppingList(mealPlanId: Long): Flow<ShoppingList?> {
        return shoppingRepository.observeShoppingList(mealPlanId)
    }

    /**
     * Generate shopping list for a meal plan
     */
    suspend fun generateShoppingList(mealPlanId: Long): Result<ShoppingList> {
        return shoppingRepository.generateShoppingList(mealPlanId)
    }

    /**
     * Add a custom item to the shopping list
     */
    suspend fun addItem(
        mealPlanId: Long,
        name: String,
        quantity: Double,
        unit: String,
        category: String
    ): Long {
        return shoppingRepository.addItem(mealPlanId, name, quantity, unit, category)
    }

    /**
     * Delete an item
     */
    suspend fun deleteItem(itemId: Long) {
        shoppingRepository.deleteItem(itemId)
    }

    /**
     * Toggle an item's checked state
     */
    suspend fun toggleItemChecked(itemId: Long) {
        shoppingRepository.toggleItemChecked(itemId)
    }

    /**
     * Toggle an item's in-cart state
     */
    suspend fun toggleItemInCart(itemId: Long) {
        shoppingRepository.toggleItemInCart(itemId)
    }

    /**
     * Reset all items to unchecked
     */
    suspend fun resetAllItems(mealPlanId: Long) {
        shoppingRepository.resetAllItems(mealPlanId)
    }

    /**
     * Get count of unchecked items
     */
    fun observeUncheckedCount(mealPlanId: Long): Flow<Int> {
        return shoppingRepository.observeUncheckedCount(mealPlanId)
    }

    /**
     * Complete shopping trip - move checked items to pantry with AI-powered categorization.
     * Falls back to local categorization if AI fails.
     */
    suspend fun completeShoppingTrip(mealPlanId: Long): Int {
        android.util.Log.d("ShoppingUseCase", "completeShoppingTrip called for mealPlanId=$mealPlanId")

        val checkedItems = shoppingRepository.getCheckedItems(mealPlanId)
        android.util.Log.d("ShoppingUseCase", "Found ${checkedItems.size} checked items")

        if (checkedItems.isEmpty()) {
            android.util.Log.d("ShoppingUseCase", "No items to process")
            return 0
        }

        // Try AI categorization first
        val pantryItems = try {
            val categorizeResult = shoppingRepository.categorizeForPantry(checkedItems)
            if (categorizeResult.isSuccess) {
                val categorizedItems = categorizeResult.getOrThrow()
                android.util.Log.d("ShoppingUseCase", "AI categorization succeeded with ${categorizedItems.size} items")
                categorizedItems.map { createPantryItemFromAI(it) }
            } else {
                android.util.Log.w("ShoppingUseCase", "AI categorization failed, using fallback")
                checkedItems.map { createPantryItemLocally(it) }
            }
        } catch (e: Exception) {
            android.util.Log.e("ShoppingUseCase", "AI categorization error: ${e.message}, using fallback")
            checkedItems.map { createPantryItemLocally(it) }
        }

        android.util.Log.d("ShoppingUseCase", "Created ${pantryItems.size} pantry items")

        if (pantryItems.isNotEmpty()) {
            try {
                pantryRepository.addFromShoppingList(pantryItems)
                android.util.Log.d("ShoppingUseCase", "Successfully added items to pantry")
            } catch (e: Exception) {
                android.util.Log.e("ShoppingUseCase", "Failed to add items to pantry: ${e.message}", e)
                throw e
            }
        }

        // Don't reset items - keep them checked as a record of what was purchased
        // The shopping list becomes read-only after completion

        return pantryItems.size
    }

    /**
     * Create a PantryItem from AI categorization result
     */
    private fun createPantryItemFromAI(dto: CategorizedPantryItemDto): PantryItem {
        android.util.Log.d("ShoppingUseCase", "AI item: ${dto.name} -> ${dto.category}, ${dto.trackingStyle}, qty=${dto.quantity} ${dto.unit}, expires=${dto.expiryDays}")

        val parsedCategory = mapCategoryFromAI(dto.category)

        // Map AI tracking style to simplified 2-type system
        val trackingStyle = when (dto.trackingStyle) {
            "STOCK_LEVEL" -> TrackingStyle.STOCK_LEVEL
            "UNITS" -> TrackingStyle.UNITS
            // Legacy mappings for backwards compatibility with AI
            "COUNT" -> TrackingStyle.UNITS
            "PRECISE" -> PantryItem.smartTrackingStyle(dto.name, parsedCategory)
            else -> PantryItem.smartTrackingStyle(dto.name, parsedCategory)
        }

        val stockLevel = when (dto.stockLevel) {
            "FULL" -> StockLevel.PLENTY
            "HIGH" -> StockLevel.PLENTY
            "MEDIUM" -> StockLevel.SOME
            "LOW" -> StockLevel.LOW
            else -> StockLevel.PLENTY  // New purchases start as FULL
        }

        return PantryItem(
            name = dto.name,
            quantityInitial = dto.quantity,
            quantityRemaining = dto.quantity,
            unit = mapUnitFromAI(dto.unit),
            category = parsedCategory,
            trackingStyle = trackingStyle,
            stockLevel = stockLevel,
            perishable = dto.perishable,
            expiryDate = dto.expiryDays?.let { LocalDate.now().plusDays(it.toLong()) },
            dateAdded = LocalDateTime.now(),
            lastUpdated = LocalDateTime.now()
        )
    }

    /**
     * Create a PantryItem using local fallback logic
     */
    private fun createPantryItemLocally(item: ShoppingItem): PantryItem {
        val (parsedQty, parsedUnit) = parseDisplayQuantity(item.polishedDisplayQuantity, item.quantity, item.unit)
        android.util.Log.d("ShoppingUseCase", "Local item: ${item.name}, polishedQty='${item.polishedDisplayQuantity}', rawQty=${item.quantity}, parsed: qty=$parsedQty, unit=$parsedUnit")

        val parsedCategory = mapCategoryToPantry(item.category)
        val trackingStyle = PantryItem.smartTrackingStyle(item.name, parsedCategory)

        return PantryItem(
            name = item.name,
            quantityInitial = parsedQty,
            quantityRemaining = parsedQty,
            unit = mapUnitToPantry(parsedUnit),
            category = parsedCategory,
            trackingStyle = trackingStyle,
            stockLevel = if (trackingStyle == TrackingStyle.STOCK_LEVEL) StockLevel.PLENTY else StockLevel.PLENTY,
            perishable = isPerishableCategory(item.category),
            expiryDate = if (isPerishableCategory(item.category)) {
                LocalDate.now().plusDays(7) // Default 7 day expiry for perishables
            } else null,
            dateAdded = LocalDateTime.now(),
            lastUpdated = LocalDateTime.now()
        )
    }

    private fun mapUnitFromAI(unit: String): PantryUnit {
        return when (unit.uppercase()) {
            "GRAMS" -> PantryUnit.GRAMS
            "MILLILITERS" -> PantryUnit.MILLILITERS
            "BUNCH" -> PantryUnit.BUNCH
            "PIECES" -> PantryUnit.UNITS
            "UNITS" -> PantryUnit.UNITS
            "COUNT" -> PantryUnit.UNITS  // COUNT from AI maps to UNITS
            else -> PantryUnit.UNITS
        }
    }

    private fun mapCategoryFromAI(category: String): PantryCategory {
        return when (category.uppercase()) {
            "PRODUCE" -> PantryCategory.PRODUCE
            "PROTEIN" -> PantryCategory.PROTEIN
            "DAIRY" -> PantryCategory.DAIRY
            "DRY_GOODS" -> PantryCategory.DRY_GOODS
            "SPICE" -> PantryCategory.SPICE
            "OILS" -> PantryCategory.OILS
            "CONDIMENT" -> PantryCategory.CONDIMENT
            "FROZEN" -> PantryCategory.FROZEN
            else -> PantryCategory.OTHER
        }
    }

    /**
     * Parse polishedDisplayQuantity string (e.g., "2 cups", "250g", "1 bunch") into quantity and unit.
     * Falls back to raw quantity/unit if parsing fails.
     */
    private fun parseDisplayQuantity(displayQty: String?, rawQty: Double, rawUnit: String): Pair<Double, String> {
        if (displayQty.isNullOrBlank()) {
            return Pair(rawQty, rawUnit)
        }

        // Try to parse formats like "2 cups", "250g", "1 1/2 tbsp", "1 bunch"
        val trimmed = displayQty.trim()

        // Pattern 1: Number with unit attached (e.g., "250g", "500ml")
        val attachedPattern = Regex("""^(\d+(?:\.\d+)?)\s*([a-zA-Z]+)$""")
        attachedPattern.find(trimmed)?.let { match ->
            val qty = match.groupValues[1].toDoubleOrNull() ?: 1.0
            val unit = match.groupValues[2]
            return Pair(qty, unit)
        }

        // Pattern 2: Number space unit (e.g., "2 cups", "1 bunch")
        val spacedPattern = Regex("""^(\d+(?:\.\d+)?)\s+(.+)$""")
        spacedPattern.find(trimmed)?.let { match ->
            val qty = match.groupValues[1].toDoubleOrNull() ?: 1.0
            val unit = match.groupValues[2]
            return Pair(qty, unit)
        }

        // Pattern 3: Fraction with unit (e.g., "1/2 cup", "1 1/2 tbsp")
        val fractionPattern = Regex("""^(\d+)?\s*(\d+)/(\d+)\s+(.+)$""")
        fractionPattern.find(trimmed)?.let { match ->
            val whole = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val numerator = match.groupValues[2].toDoubleOrNull() ?: 0.0
            val denominator = match.groupValues[3].toDoubleOrNull() ?: 1.0
            val unit = match.groupValues[4]
            val qty = whole + (numerator / denominator)
            return Pair(qty, unit)
        }

        // Pattern 4: Just a number (e.g., "2")
        val numberPattern = Regex("""^(\d+(?:\.\d+)?)$""")
        numberPattern.find(trimmed)?.let { match ->
            val qty = match.groupValues[1].toDoubleOrNull() ?: 1.0
            return Pair(qty, "units")
        }

        // Fallback: treat as 1 unit
        return Pair(1.0, trimmed)
    }

    private fun mapUnitToPantry(unit: String): PantryUnit {
        return when (unit.lowercase()) {
            "g", "grams", "gram" -> PantryUnit.GRAMS
            "kg", "kilograms", "kilogram" -> PantryUnit.GRAMS // Will need to multiply by 1000
            "ml", "milliliters", "milliliter" -> PantryUnit.MILLILITERS
            "l", "liters", "liter", "litres", "litre" -> PantryUnit.MILLILITERS // Will need to multiply by 1000
            "bunch", "bunches" -> PantryUnit.BUNCH
            "cup", "cups" -> PantryUnit.UNITS // Treat cups as units for pantry
            "tbsp", "tablespoon", "tablespoons" -> PantryUnit.UNITS
            "tsp", "teaspoon", "teaspoons" -> PantryUnit.UNITS
            "oz", "ounce", "ounces" -> PantryUnit.UNITS
            "lb", "lbs", "pound", "pounds" -> PantryUnit.UNITS
            "clove", "cloves" -> PantryUnit.UNITS
            "slice", "slices" -> PantryUnit.UNITS
            "handful", "handfuls" -> PantryUnit.UNITS
            "stalk", "stalks" -> PantryUnit.UNITS
            "sprig", "sprigs" -> PantryUnit.UNITS
            "head", "heads" -> PantryUnit.UNITS
            "can", "cans" -> PantryUnit.UNITS
            "package", "packages", "pkg" -> PantryUnit.UNITS
            "unit", "units" -> PantryUnit.UNITS
            else -> PantryUnit.UNITS
        }
    }

    private fun mapCategoryToPantry(category: String): PantryCategory {
        return when (category) {
            ShoppingCategories.PRODUCE -> PantryCategory.PRODUCE
            ShoppingCategories.PROTEIN -> PantryCategory.PROTEIN
            ShoppingCategories.DAIRY -> PantryCategory.DAIRY
            ShoppingCategories.PANTRY -> PantryCategory.DRY_GOODS
            ShoppingCategories.FROZEN -> PantryCategory.FROZEN
            ShoppingCategories.CONDIMENTS -> PantryCategory.CONDIMENT
            ShoppingCategories.SPICES -> PantryCategory.SPICE
            else -> PantryCategory.OTHER
        }
    }

    private fun isPerishableCategory(category: String): Boolean {
        return category in listOf(
            ShoppingCategories.PRODUCE,
            ShoppingCategories.PROTEIN,
            ShoppingCategories.DAIRY,
            ShoppingCategories.BAKERY
        )
    }
}
