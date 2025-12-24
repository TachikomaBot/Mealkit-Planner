package com.mealplanner.presentation.screens.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealplanner.domain.model.ShoppingCategories
import com.mealplanner.domain.model.ShoppingItem
import com.mealplanner.domain.model.ShoppingList
import com.mealplanner.domain.usecase.ManageShoppingListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val shoppingListUseCase: ManageShoppingListUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShoppingUiState>(ShoppingUiState.Loading)
    val uiState: StateFlow<ShoppingUiState> = _uiState.asStateFlow()

    private val _isShoppingMode = MutableStateFlow(false)
    val isShoppingMode: StateFlow<Boolean> = _isShoppingMode.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _completionState = MutableStateFlow<CompletionState?>(null)
    val completionState: StateFlow<CompletionState?> = _completionState.asStateFlow()

    init {
        observeShoppingList()
    }

    private fun observeShoppingList() {
        viewModelScope.launch {
            shoppingListUseCase.observeCurrentShoppingList()
                .collect { shoppingList ->
                    _uiState.value = if (shoppingList == null || shoppingList.items.isEmpty()) {
                        ShoppingUiState.Empty
                    } else {
                        ShoppingUiState.Loaded(
                            shoppingList = shoppingList,
                            groupedItems = groupItemsByCategory(shoppingList.items)
                        )
                    }
                }
        }
    }

    fun toggleShoppingMode() {
        _isShoppingMode.value = !_isShoppingMode.value
    }

    fun toggleItemChecked(itemId: Long) {
        viewModelScope.launch {
            shoppingListUseCase.toggleItemChecked(itemId)
        }
    }

    fun toggleItemInCart(itemId: Long) {
        viewModelScope.launch {
            shoppingListUseCase.toggleItemInCart(itemId)
        }
    }

    fun resetAll() {
        val currentState = _uiState.value
        if (currentState is ShoppingUiState.Loaded) {
            viewModelScope.launch {
                shoppingListUseCase.resetAllItems(currentState.shoppingList.mealPlanId)
            }
        }
    }

    fun showAddDialog() {
        _showAddDialog.value = true
    }

    fun hideAddDialog() {
        _showAddDialog.value = false
    }

    fun addItem(name: String, quantity: Double, unit: String, category: String) {
        val currentState = _uiState.value
        if (currentState is ShoppingUiState.Loaded) {
            viewModelScope.launch {
                shoppingListUseCase.addItem(
                    mealPlanId = currentState.shoppingList.mealPlanId,
                    name = name,
                    quantity = quantity,
                    unit = unit,
                    category = category
                )
                hideAddDialog()
            }
        }
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            shoppingListUseCase.deleteItem(itemId)
        }
    }

    fun completeShoppingTrip() {
        val currentState = _uiState.value
        if (currentState is ShoppingUiState.Loaded) {
            viewModelScope.launch {
                val itemsAdded = shoppingListUseCase.completeShoppingTrip(
                    currentState.shoppingList.mealPlanId
                )
                _completionState.value = CompletionState(itemsAddedToPantry = itemsAdded)
                _isShoppingMode.value = false
            }
        }
    }

    fun dismissCompletion() {
        _completionState.value = null
    }

    private fun groupItemsByCategory(items: List<ShoppingItem>): Map<String, List<ShoppingItem>> {
        // Group by category and sort by the predefined order
        return items
            .groupBy { it.category }
            .toSortedMap(compareBy { category ->
                ShoppingCategories.orderedCategories.indexOf(category).takeIf { it >= 0 }
                    ?: Int.MAX_VALUE
            })
    }
}

sealed class ShoppingUiState {
    data object Loading : ShoppingUiState()
    data object Empty : ShoppingUiState()
    data class Loaded(
        val shoppingList: ShoppingList,
        val groupedItems: Map<String, List<ShoppingItem>>
    ) : ShoppingUiState()
}

data class CompletionState(
    val itemsAddedToPantry: Int
)
