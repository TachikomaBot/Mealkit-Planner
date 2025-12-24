package com.mealplanner.presentation.screens.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealplanner.domain.model.PantryCategory
import com.mealplanner.domain.model.PantryItem
import com.mealplanner.domain.model.PantryUnit
import com.mealplanner.domain.model.StockLevel
import com.mealplanner.domain.model.TrackingStyle
import com.mealplanner.domain.repository.PantryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class PantryViewModel @Inject constructor(
    private val pantryRepository: PantryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PantryUiState())
    val uiState: StateFlow<PantryUiState> = _uiState.asStateFlow()

    private val _allItems = MutableStateFlow<List<PantryItem>>(emptyList())

    init {
        observeItems()
    }

    private fun observeItems() {
        viewModelScope.launch {
            pantryRepository.observeAllItems()
                .collect { items ->
                    _allItems.value = items
                    updateFilteredItems()
                }
        }
    }

    private fun updateFilteredItems() {
        val items = _allItems.value
        val filter = _uiState.value.selectedFilter

        val filtered = when (filter) {
            PantryFilter.ALL -> items
            PantryFilter.CHECK_STOCK -> items.filter { it.needsStockCheck }
            is PantryFilter.Category -> items.filter { it.category == filter.category }
        }

        val checkStockCount = items.count { it.needsStockCheck }

        _uiState.update {
            it.copy(
                items = filtered.sortedBy { item -> item.name.lowercase() },
                checkStockCount = checkStockCount
            )
        }
    }

    fun setFilter(filter: PantryFilter) {
        _uiState.update { it.copy(selectedFilter = filter, expandedItemId = null) }
        updateFilteredItems()
    }

    fun expandItem(itemId: Long?) {
        _uiState.update { it.copy(expandedItemId = itemId) }
    }

    fun updateQuantity(itemId: Long, newQuantity: Double, markAsChecked: Boolean = true) {
        viewModelScope.launch {
            pantryRepository.updateQuantity(itemId, newQuantity, markAsChecked)
            _uiState.update { it.copy(expandedItemId = null) }
        }
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            pantryRepository.deleteItem(itemId)
            _uiState.update { it.copy(expandedItemId = null) }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun addItem(
        name: String,
        quantity: Double,
        unit: PantryUnit,
        category: PantryCategory,
        expiryDays: Int?,
        stockLevel: StockLevel = StockLevel.PLENTY
    ) {
        viewModelScope.launch {
            val expiryDate = expiryDays?.let {
                LocalDate.now().plusDays(it.toLong())
            }

            // Use smart default tracking style based on name and category
            val trackingStyle = PantryItem.smartTrackingStyle(name, category)

            val item = PantryItem(
                name = name,
                quantityInitial = quantity,
                quantityRemaining = quantity,
                unit = unit,
                category = category,
                trackingStyle = trackingStyle,
                stockLevel = stockLevel,
                perishable = category.isPerishable,
                expiryDate = expiryDate,
                dateAdded = LocalDateTime.now(),
                lastUpdated = LocalDateTime.now()
            )

            pantryRepository.addItem(item)
            hideAddDialog()
        }
    }

    fun updateStockLevel(itemId: Long, stockLevel: StockLevel) {
        viewModelScope.launch {
            val item = _allItems.value.find { it.id == itemId } ?: return@launch
            val updatedItem = item.copy(
                stockLevel = stockLevel,
                lastUpdated = LocalDateTime.now()
            )
            pantryRepository.updateItem(updatedItem)
            _uiState.update { it.copy(expandedItemId = null) }
        }
    }
}

data class PantryUiState(
    val items: List<PantryItem> = emptyList(),
    val selectedFilter: PantryFilter = PantryFilter.ALL,
    val expandedItemId: Long? = null,
    val checkStockCount: Int = 0,
    val showAddDialog: Boolean = false
)

sealed class PantryFilter {
    data object ALL : PantryFilter()
    data object CHECK_STOCK : PantryFilter()
    data class Category(val category: PantryCategory) : PantryFilter()
}

val allFilters: List<Pair<PantryFilter, String>> = listOf(
    PantryFilter.ALL to "All",
    PantryFilter.CHECK_STOCK to "Check Stock",
    PantryFilter.Category(PantryCategory.PRODUCE) to "Produce",
    PantryFilter.Category(PantryCategory.PROTEIN) to "Protein",
    PantryFilter.Category(PantryCategory.DAIRY) to "Dairy",
    PantryFilter.Category(PantryCategory.DRY_GOODS) to "Dry Goods",
    PantryFilter.Category(PantryCategory.SPICE) to "Spices",
    PantryFilter.Category(PantryCategory.OILS) to "Oils",
    PantryFilter.Category(PantryCategory.CONDIMENT) to "Condiments",
    PantryFilter.Category(PantryCategory.FROZEN) to "Frozen",
    PantryFilter.Category(PantryCategory.OTHER) to "Other"
)
