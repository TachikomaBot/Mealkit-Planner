package com.mealplanner.presentation.screens.pantry

import com.mealplanner.presentation.theme.Mustard600
import com.mealplanner.presentation.theme.Mustard700

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mealplanner.domain.model.PantryCategory
import com.mealplanner.domain.model.PantryItem
import com.mealplanner.domain.model.PantryUnit
import com.mealplanner.domain.model.StockLevel
import com.mealplanner.domain.model.TrackingStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(
    viewModel: PantryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Pantry") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Mustard600,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = androidx.compose.ui.graphics.Color.Black // Mustard500 is light/bright, Black content? Or White? 500 is strong yellow. Usually Black on Yellow.
                    // Wait, Mustard500 (0xFFEAB308) is fairly dark yellow/gold. 
                    // Let's check consistency. Tomato500 (Red) takes White. Pacific500 (Teal) takes White.
                    // Mustard500 is Gold. White *might* be low contrast. 
                    // Let's try White first for consistency, or Black if it's too light.
                    // Actually, Mustard500 is quite saturated. White text on 0xFFEAB308 is questionable.
                    // Let's use Color.Black for Mustard for readability, or White if bold.
                    // I'll stick to White for now to match others, but it might need tweaking.
                    // Actually, let's use White. 
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add ingredient")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter carousel
            FilterCarousel(
                selectedFilter = uiState.selectedFilter,
                checkStockCount = uiState.checkStockCount,
                onFilterSelected = { viewModel.setFilter(it) }
            )

            // Content
            if (uiState.items.isEmpty()) {
                EmptyPantryContent(
                    isCheckStock = uiState.selectedFilter == PantryFilter.CHECK_STOCK,
                    onAddItem = { viewModel.showAddDialog() }
                )
            } else {
                PantryGrid(
                    items = uiState.items,
                    expandedItemId = uiState.expandedItemId,
                    onItemClick = { item ->
                        viewModel.expandItem(
                            if (uiState.expandedItemId == item.id) null else item.id
                        )
                    },
                    onUpdateQuantity = { id, qty -> viewModel.updateQuantity(id, qty) },
                    onUpdateStockLevel = { id, level -> viewModel.updateStockLevel(id, level) },
                    onDelete = { viewModel.deleteItem(it) },
                    onClose = { viewModel.expandItem(null) }
                )
            }
        }

        // Add ingredient dialog
        if (uiState.showAddDialog) {
            AddIngredientDialog(
                defaultCategory = when (val filter = uiState.selectedFilter) {
                    is PantryFilter.Category -> filter.category
                    else -> PantryCategory.OTHER
                },
                onDismiss = { viewModel.hideAddDialog() },
                onAdd = { name, qty, unit, category, expiry, stockLevel ->
                    viewModel.addItem(name, qty, unit, category, expiry, stockLevel)
                }
            )
        }
    }
}

@Composable
private fun FilterCarousel(
    selectedFilter: PantryFilter,
    checkStockCount: Int,
    onFilterSelected: (PantryFilter) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(allFilters) { (filter, label) ->
            val isSelected = selectedFilter == filter
            val isCheckStock = filter == PantryFilter.CHECK_STOCK

            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label)
                        if (isCheckStock && checkStockCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Badge(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            ) {
                                Text(
                                    checkStockCount.toString(),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onError
                                    }
                                )
                            }
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun EmptyPantryContent(
    isCheckStock: Boolean,
    onAddItem: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isCheckStock) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "All stock verified!",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No perishables need checking right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Icon(
                imageVector = Icons.Default.Kitchen,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Your pantry is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add ingredients to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAddItem) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Ingredient")
            }
        }
    }
}

@Composable
private fun PantryGrid(
    items: List<PantryItem>,
    expandedItemId: Long?,
    onItemClick: (PantryItem) -> Unit,
    onUpdateQuantity: (Long, Double) -> Unit,
    onUpdateStockLevel: (Long, StockLevel) -> Unit,
    onDelete: (Long) -> Unit,
    onClose: () -> Unit
) {
    val expandedItem = items.find { it.id == expandedItemId }
    val expandedIndex = items.indexOfFirst { it.id == expandedItemId }
    val expandedRow = if (expandedIndex >= 0) expandedIndex / 2 else -1
    val listState = rememberLazyGridState()

    LaunchedEffect(expandedItemId) {
        if (expandedIndex >= 0) {
            // Scroll to the row containing the item (plus a bit of offset if needed, or just the item itself)
            // Adjuster is inserted *after* this row. Scrolling to the item ensures it's visible.
            // We might want to scroll specifically so the adjuster is visible.
            // Let's scroll to the item index.
            listState.animateScrollToItem(expandedIndex)
        }
    }

    LazyVerticalGrid(
        state = listState,
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEachIndexed { index, item ->
            val currentRow = index / 2
            val isInExpandedRow = expandedRow >= 0 && currentRow == expandedRow

            item(key = item.id) {
                IngredientCard(
                    item = item,
                    isExpanded = item.id == expandedItemId,
                    isInactiveInPair = isInExpandedRow && item.id != expandedItemId,
                    onClick = { onItemClick(item) }
                )
            }

            // Insert adjuster row after the pair containing the expanded item
            if (currentRow == expandedRow && index % 2 == 1) {
                item(span = { GridItemSpan(2) }, key = "adjuster") {
                    expandedItem?.let { expanded ->
                        AdjusterRow(
                            item = expanded,
                            onSaveQuantity = { qty -> onUpdateQuantity(expanded.id, qty) },
                            onSaveStockLevel = { level -> onUpdateStockLevel(expanded.id, level) },
                            onDelete = { onDelete(expanded.id) },
                            onClose = onClose
                        )
                    }
                }
            } else if (currentRow == expandedRow && index == items.lastIndex && index % 2 == 0) {
                item(span = { GridItemSpan(2) }, key = "adjuster") {
                    expandedItem?.let { expanded ->
                        AdjusterRow(
                            item = expanded,
                            onSaveQuantity = { qty -> onUpdateQuantity(expanded.id, qty) },
                            onSaveStockLevel = { level -> onUpdateStockLevel(expanded.id, level) },
                            onDelete = { onDelete(expanded.id) },
                            onClose = onClose
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IngredientCard(
    item: PantryItem,
    isExpanded: Boolean,
    isInactiveInPair: Boolean,
    onClick: () -> Unit
) {
    // For visual fill, use effective stock level for STOCK_LEVEL items
    val fillPercent = when (item.trackingStyle) {
        TrackingStyle.STOCK_LEVEL -> when (item.effectiveStockLevel) {
            StockLevel.OUT_OF_STOCK -> 0f
            StockLevel.LOW -> 0.15f
            StockLevel.SOME -> 0.5f
            StockLevel.PLENTY -> 0.85f
        }
        else -> item.percentRemaining
    }

    val isLow = item.effectiveStockLevel == StockLevel.LOW ||
            item.effectiveStockLevel == StockLevel.OUT_OF_STOCK

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isExpanded) {
            CardDefaults.outlinedCardBorder().copy(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary
                    )
                )
            )
        } else null,
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Water fill from bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fillPercent)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF22D3EE),
                                Color(0xFF009FB7)
                            )
                        )
                    )
            )

            // Card content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        MaterialTheme.colorScheme.surface.copy(
                            alpha = if (isInactiveInPair) 0.7f else 1f
                        )
                    )
            ) {
                // Category emoji placeholder
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getCategoryEmoji(item.category),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (isInactiveInPair) 0.3f else 0.4f
                        )
                    )
                }

                // Gradient overlay for text readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                )

                // Name and availability
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.availabilityDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                // Low stock indicator
                if (isLow) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = if (item.effectiveStockLevel == StockLevel.OUT_OF_STOCK) "Out" else "Low",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjusterRow(
    item: PantryItem,
    onSaveQuantity: (Double) -> Unit,
    onSaveStockLevel: (StockLevel) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    // Hoisted state
    var currentStockLevel by remember(item.effectiveStockLevel) { mutableStateOf(item.effectiveStockLevel) }
    var currentQuantity by remember(item.quantityRemaining) { mutableDoubleStateOf(item.quantityRemaining) }

    val swipeState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.75f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Save
                    if (item.trackingStyle == TrackingStyle.STOCK_LEVEL) {
                        onSaveStockLevel(currentStockLevel)
                    } else {
                        onSaveQuantity(currentQuantity)
                    }
                    onClose() // Close after save as per user request
                    true
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Delete
                    onDelete()
                    true
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            val color = when (swipeState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50) // Green for Save
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error // Red for Delete
                else -> Color.Transparent
            }
            val alignment = when (swipeState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (swipeState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Check
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                else -> Icons.Default.Info
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp)) // Match card shape
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        },
        content = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Different UI based on tracking style
                    when (item.trackingStyle) {
                        TrackingStyle.STOCK_LEVEL -> {
                            StockLevelAdjuster(
                                currentLevel = currentStockLevel,
                                onLevelChange = { currentStockLevel = it }
                            )
                        }
                        TrackingStyle.COUNT -> {
                            CountAdjuster(
                                item = item,
                                currentCount = currentQuantity,
                                onCountChange = { currentQuantity = it }
                            )
                        }
                        TrackingStyle.PRECISE -> {
                            PreciseAdjuster(
                                item = item,
                                currentQuantity = currentQuantity,
                                onQuantityChange = { currentQuantity = it }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Helper text for swipe
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Swipe right to save",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                         Text(
                            text = "Swipe left to delete",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun StockLevelAdjuster(
    currentLevel: StockLevel,
    onLevelChange: (StockLevel) -> Unit
) {
    // Stateless: selectedLevel is passed in as currentLevel


    Column {
        Text(
            text = "Stock Level",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Stock level buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StockLevel.entries.forEach { level ->
                FilterChip(
                    selected = currentLevel == level,
                    onClick = { onLevelChange(level) },
                    label = { Text(level.displayName) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (level) {
                            StockLevel.OUT_OF_STOCK -> MaterialTheme.colorScheme.errorContainer
                            StockLevel.LOW -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                            StockLevel.SOME -> MaterialTheme.colorScheme.primaryContainer
                            StockLevel.PLENTY -> MaterialTheme.colorScheme.primary
                        }
                    )
                )
            }
        }

    }
}

@Composable
private fun CountAdjuster(
    item: PantryItem,
    currentCount: Double,
    onCountChange: (Double) -> Unit
) {
    Column {
        // Count display with +/- buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = { if (currentCount > 0) onCountChange(currentCount - 1) },
                enabled = currentCount > 0
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }

            Spacer(modifier = Modifier.width(24.dp))

            Text(
                text = currentCount.toInt().toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.unit.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(24.dp))

            FilledIconButton(
                onClick = { onCountChange(currentCount + 1) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }

    }
}

@Composable
private fun PreciseAdjuster(
    item: PantryItem,
    currentQuantity: Double,
    onQuantityChange: (Double) -> Unit
) {
    val percentRemaining = (currentQuantity.toFloat() / item.quantityInitial.toFloat()).coerceIn(0f, 1f)

    Column {
        // Quantity display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = currentQuantity.toInt().toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = item.unit.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(${(percentRemaining * 100).toInt()}% remaining)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Slider
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = currentQuantity.toFloat(),
                onValueChange = { onQuantityChange(it.toDouble()) },
                valueRange = 0f..item.quantityInitial.toFloat(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Text(
                text = item.quantityInitial.toInt().toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddIngredientDialog(
    defaultCategory: PantryCategory,
    onDismiss: () -> Unit,
    onAdd: (String, Double, PantryUnit, PantryCategory, Int?, StockLevel) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf(PantryUnit.GRAMS) }
    var selectedCategory by remember { mutableStateOf(defaultCategory) }
    var expiryDays by remember { mutableStateOf("") }
    var selectedStockLevel by remember { mutableStateOf(StockLevel.PLENTY) }

    val isPerishable = selectedCategory.isPerishable

    // Compute tracking style based on name and category
    val computedTrackingStyle = if (name.isNotBlank()) {
        PantryItem.smartTrackingStyle(name, selectedCategory)
    } else {
        // When no name yet, use category-based default
        PantryItem.smartTrackingStyle("", selectedCategory)
    }

    val isStockLevelTracking = computedTrackingStyle == TrackingStyle.STOCK_LEVEL

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Ingredient") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g., All-purpose flour") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Category dropdown
                var categoryExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.displayName,
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
                        PantryCategory.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.displayName) },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Show different input based on computed tracking style
                if (isStockLevelTracking) {
                    // Stock level selector for shelf-stable items
                    Column {
                        Text(
                            text = "Stock Level",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StockLevel.entries.filter { it != StockLevel.OUT_OF_STOCK }.forEach { level ->
                                FilterChip(
                                    selected = selectedStockLevel == level,
                                    onClick = { selectedStockLevel = level },
                                    label = { Text(level.displayName) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                } else {
                    // Quantity and unit for count/precise items
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Quantity") },
                            placeholder = { Text("500") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        // Unit dropdown
                        var unitExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = unitExpanded,
                            onExpandedChange = { unitExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = selectedUnit.displayName,
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
                                PantryUnit.entries.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit.displayName) },
                                        onClick = {
                                            selectedUnit = unit
                                            unitExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Expiry days (only for perishables)
                AnimatedVisibility(visible = isPerishable) {
                    OutlinedTextField(
                        value = expiryDays,
                        onValueChange = { expiryDays = it.filter { c -> c.isDigit() } },
                        label = { Text("Expires in (days)") },
                        placeholder = { Text("7") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        supportingText = { Text("Leave empty for no specific expiry") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = if (isStockLevelTracking) {
                        // For stock level tracking, use a nominal value (we only care about stock level)
                        1.0
                    } else {
                        quantity.toDoubleOrNull() ?: return@Button
                    }
                    val expiry = expiryDays.toIntOrNull()
                    onAdd(name, qty, selectedUnit, selectedCategory, expiry, selectedStockLevel)
                },
                enabled = name.isNotBlank() && (isStockLevelTracking || quantity.toDoubleOrNull() != null)
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

private fun getCategoryEmoji(category: PantryCategory): String {
    return when (category) {
        PantryCategory.PRODUCE -> "\uD83E\uDD6C"
        PantryCategory.PROTEIN -> "\uD83E\uDD69"
        PantryCategory.DAIRY -> "\uD83E\uDDC0"
        PantryCategory.DRY_GOODS -> "\uD83C\uDF3E"
        PantryCategory.SPICE -> "\uD83C\uDF36\uFE0F"
        PantryCategory.OILS -> "\uD83E\uDED2"
        PantryCategory.CONDIMENT -> "\uD83E\uDED9"
        PantryCategory.FROZEN -> "\uD83E\uDDCA"
        PantryCategory.OTHER -> "\uD83D\uDCE6"
    }
}
