package com.mealplanner.presentation.screens.shopping

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mealplanner.domain.model.ShoppingCategories
import com.mealplanner.domain.model.ShoppingItem
import com.mealplanner.presentation.components.FullScreenLoading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    onBack: () -> Unit,
    onNavigateToMealPlan: () -> Unit,
    viewModel: ShoppingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isShoppingMode by viewModel.isShoppingMode.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val topBarColor by animateColorAsState(
        targetValue = if (isShoppingMode) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        label = "topBarColor"
    )

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isShoppingMode) "Shopping Mode" else "Shopping List")
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState is ShoppingUiState.Loaded) {
                        IconButton(onClick = { viewModel.toggleShoppingMode() }) {
                            Icon(
                                imageVector = if (isShoppingMode) Icons.Default.EditNote else Icons.Default.Store,
                                contentDescription = if (isShoppingMode) "Exit shopping mode" else "Enter shopping mode"
                            )
                        }
                        if (!isShoppingMode) {
                            IconButton(onClick = { viewModel.resetAll() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset all"
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = if (isShoppingMode) {
                        MaterialTheme.colorScheme.onTertiary
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    }
                )
            )
        },
        floatingActionButton = {
            if (uiState is ShoppingUiState.Loaded && !isShoppingMode) {
                FloatingActionButton(
                    onClick = { viewModel.showAddDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add item")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is ShoppingUiState.Loading -> {
                    FullScreenLoading()
                }

                is ShoppingUiState.Empty -> {
                    EmptyShoppingListContent(
                        onNavigateToMealPlan = onNavigateToMealPlan
                    )
                }

                is ShoppingUiState.Loaded -> {
                    if (isShoppingMode) {
                        ShoppingModeContent(
                            shoppingList = state.shoppingList,
                            groupedItems = state.groupedItems,
                            onItemCheckedChange = { itemId ->
                                viewModel.toggleItemChecked(itemId)
                            }
                        )
                    } else {
                        ShoppingListContent(
                            shoppingList = state.shoppingList,
                            groupedItems = state.groupedItems,
                            onItemCheckedChange = { itemId ->
                                viewModel.toggleItemChecked(itemId)
                            }
                        )
                    }
                }
            }
        }
    }

    // Add item dialog
    if (showAddDialog) {
        AddItemDialog(
            onDismiss = { viewModel.hideAddDialog() },
            onAdd = { name, quantity, unit, category ->
                viewModel.addItem(name, quantity, unit, category)
            }
        )
    }

}

@Composable
private fun EmptyShoppingListContent(
    onNavigateToMealPlan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No Shopping List",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Generate a meal plan first to create your shopping list",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onNavigateToMealPlan) {
            Text("Create Meal Plan")
        }
    }
}

@Composable
private fun ShoppingListContent(
    shoppingList: com.mealplanner.domain.model.ShoppingList,
    groupedItems: Map<String, List<ShoppingItem>>,
    onItemCheckedChange: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Progress header
        item {
            ShoppingProgressHeader(
                totalItems = shoppingList.totalItems,
                checkedItems = shoppingList.checkedItems
            )
        }

        // Grouped items by category
        groupedItems.forEach { (category, items) ->
            item(key = "header_$category") {
                ShoppingCategoryHeader(
                    category = category,
                    itemCount = items.size,
                    checkedCount = items.count { it.checked }
                )
            }

            items(
                items = items,
                key = { it.id }
            ) { item ->
                NormalShoppingItem(
                    item = item,
                    onCheckedChange = { onItemCheckedChange(item.id) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Bottom padding for FAB
        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun ShoppingModeContent(
    shoppingList: com.mealplanner.domain.model.ShoppingList,
    groupedItems: Map<String, List<ShoppingItem>>,
    onItemCheckedChange: (Long) -> Unit
) {
    val uncheckedItems = shoppingList.items.filter { !it.checked }
    val checkedItems = shoppingList.items.filter { it.checked }

    Column(modifier = Modifier.fillMaxSize()) {
        // Shopping mode progress banner
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${shoppingList.checkedItems} of ${shoppingList.totalItems}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "items collected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { shoppingList.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }
        }

        // Items list - unchecked first
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (uncheckedItems.isEmpty() && checkedItems.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "All items collected!",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "You're all set for the week!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            items(
                items = uncheckedItems,
                key = { "unchecked_${it.id}" }
            ) { item ->
                ShoppingModeItem(
                    item = item,
                    onClick = { onItemCheckedChange(item.id) }
                )
            }

            if (checkedItems.isNotEmpty() && uncheckedItems.isNotEmpty()) {
                item {
                    Text(
                        text = "In Cart (${checkedItems.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }
            }

            items(
                items = checkedItems,
                key = { "checked_${it.id}" }
            ) { item ->
                ShoppingModeItem(
                    item = item,
                    onClick = { onItemCheckedChange(item.id) }
                )
            }
        }

    }
}

@Composable
private fun ShoppingModeItem(
    item: ShoppingItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.checked) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (item.checked) 0.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (item.checked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.checked) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                color = if (item.checked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = item.displayQuantity,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NormalShoppingItem(
    item: ShoppingItem,
    onCheckedChange: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.checked,
                onCheckedChange = { onCheckedChange() }
            )

            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = item.displayQuantity,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun ShoppingProgressHeader(
    totalItems: Int,
    checkedItems: Int
) {
    val progress = if (totalItems > 0) checkedItems.toFloat() / totalItems else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Shopping Progress",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "$checkedItems of $totalItems items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun ShoppingCategoryHeader(
    category: String,
    itemCount: Int,
    checkedCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "$checkedCount/$itemCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, quantity: Double, unit: String, category: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var selectedUnit by remember { mutableStateOf("units") }
    var selectedCategory by remember { mutableStateOf(ShoppingCategories.OTHER) }

    val units = listOf("units", "g", "ml", "oz", "lbs", "cups", "tbsp", "tsp", "bunch")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Item") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Qty") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )

                    var unitExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = unitExpanded,
                        onExpandedChange = { unitExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = unitExpanded,
                            onDismissRequest = { unitExpanded = false }
                        ) {
                            units.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit) },
                                    onClick = {
                                        selectedUnit = unit
                                        unitExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                var categoryExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        ShoppingCategories.orderedCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val qty = quantity.toDoubleOrNull() ?: 1.0
                    if (name.isNotBlank()) {
                        onAdd(name, qty, selectedUnit, selectedCategory)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
