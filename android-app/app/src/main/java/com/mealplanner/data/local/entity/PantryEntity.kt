package com.mealplanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mealplanner.domain.model.PantryCategory
import com.mealplanner.domain.model.PantryItem
import com.mealplanner.domain.model.PantryUnit
import com.mealplanner.domain.model.StockLevel
import com.mealplanner.domain.model.TrackingStyle

@Entity(tableName = "pantry_items")
data class PantryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val quantityInitial: Double,
    val quantityRemaining: Double,
    val unit: String, // Store as string for simplicity
    val category: String, // Store as string for simplicity
    val trackingStyle: String = TrackingStyle.UNITS.name,
    val stockLevel: String = StockLevel.PLENTY.name,
    val perishable: Boolean = false,
    val expiryDate: Long? = null, // Epoch millis
    val dateAdded: Long, // Epoch millis
    val lastUpdated: Long, // Epoch millis
    val lastStockCheck: Long? = null, // Epoch millis
    val imageUrl: String? = null
) {
    fun toDomain(): PantryItem {
        val parsedCategory = PantryCategory.fromString(category)

        // Handle legacy tracking styles during migration to simplified 2-type system
        val parsedTrackingStyle = when (trackingStyle) {
            "UNITS" -> TrackingStyle.UNITS
            "COUNT" -> TrackingStyle.UNITS  // Legacy COUNT → UNITS (direct mapping)
            "STOCK_LEVEL" -> TrackingStyle.STOCK_LEVEL
            "PRECISE" -> PantryItem.smartTrackingStyle(name, parsedCategory)  // Legacy PRECISE → determine from name/category
            else -> PantryItem.smartTrackingStyle(name, parsedCategory)  // Unknown → compute smart default
        }

        return PantryItem(
            id = id,
            name = name,
            brand = brand,
            quantityInitial = quantityInitial,
            quantityRemaining = quantityRemaining,
            unit = PantryUnit.fromString(unit),
            category = parsedCategory,
            trackingStyle = parsedTrackingStyle,
            stockLevel = StockLevel.fromString(stockLevel),
            perishable = perishable,
            expiryDate = expiryDate?.let {
                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            },
            dateAdded = java.time.Instant.ofEpochMilli(dateAdded)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
            lastUpdated = java.time.Instant.ofEpochMilli(lastUpdated)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
            lastStockCheck = lastStockCheck?.let {
                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            },
            imageUrl = imageUrl
        )
    }

    companion object {
        fun fromDomain(item: PantryItem): PantryEntity = PantryEntity(
            id = item.id,
            name = item.name,
            brand = item.brand,
            quantityInitial = item.quantityInitial,
            quantityRemaining = item.quantityRemaining,
            unit = item.unit.name,
            category = item.category.name,
            trackingStyle = item.trackingStyle.name,
            stockLevel = item.stockLevel.name,
            perishable = item.perishable,
            expiryDate = item.expiryDate?.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
            dateAdded = item.dateAdded.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            lastUpdated = item.lastUpdated.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            lastStockCheck = item.lastStockCheck?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
            imageUrl = item.imageUrl
        )
    }
}
