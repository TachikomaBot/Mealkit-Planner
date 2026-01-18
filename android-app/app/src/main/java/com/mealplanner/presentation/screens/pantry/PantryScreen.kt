package com.mealplanner.presentation.screens.pantry

import com.mealplanner.presentation.theme.Mustard600
import com.mealplanner.presentation.theme.Mustard700

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
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
                useSoonCount = uiState.useSoonCount,
                onFilterSelected = { viewModel.setFilter(it) }
            )

            // Content
            if (uiState.items.isEmpty()) {
                EmptyPantryContent(
                    isUseSoon = uiState.selectedFilter == PantryFilter.USE_SOON,
                    onAddItem = { viewModel.showAddDialog() }
                )
            } else {
                val isAdjusterOpen = uiState.expandedItemId != null

                Box(modifier = Modifier.fillMaxSize()) {
                    // Grid of pantry cards
                    PantryGrid(
                        items = uiState.items,
                        expandedItemId = uiState.expandedItemId,
                        onItemClick = { item ->
                            viewModel.expandItem(
                                if (uiState.expandedItemId == item.id) null else item.id
                            )
                        }
                    )

                    // Bottom overlay adjuster when an item is selected
                    uiState.expandedItemId?.let { expandedId ->
                        val expandedItem = uiState.items.find { it.id == expandedId }
                        if (expandedItem != null) {
                            AdjusterOverlay(
                                item = expandedItem,
                                onSaveQuantity = { qty -> viewModel.updateQuantity(expandedId, qty) },
                                onSaveStockLevel = { level -> viewModel.updateStockLevel(expandedId, level) },
                                onDelete = { viewModel.deleteItem(expandedId) },
                                onClose = { viewModel.expandItem(null) },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }

                    // FAB - positioned 25dp above adjuster card when open
                    // Adjuster height ~88dp (16dp margin + 72dp content), plus 25dp gap
                    FloatingActionButton(
                        onClick = { viewModel.showAddDialog() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .padding(bottom = if (isAdjusterOpen) 97.dp else 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add ingredient")
                    }
                }
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
    useSoonCount: Int,
    onFilterSelected: (PantryFilter) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(allFilters) { (filter, label) ->
            val isSelected = selectedFilter == filter
            val isUseSoon = filter == PantryFilter.USE_SOON

            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label)
                        if (isUseSoon && useSoonCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Badge(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            ) {
                                Text(
                                    useSoonCount.toString(),
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
    isUseSoon: Boolean,
    onAddItem: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isUseSoon) {
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
    onItemClick: (PantryItem) -> Unit
) {
    val listState = rememberLazyGridState()

    // Scroll to selected item when it changes
    LaunchedEffect(expandedItemId) {
        if (expandedItemId != null) {
            val index = items.indexOfFirst { it.id == expandedItemId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    LazyVerticalGrid(
        state = listState,
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 200.dp), // Extra bottom padding for overlay
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            count = items.size,
            key = { index -> items[index].id }
        ) { index ->
            val item = items[index]
            IngredientCard(
                item = item,
                isExpanded = item.id == expandedItemId,
                onClick = { onItemClick(item) }
            )
        }
    }
}

@Composable
private fun IngredientCard(
    item: PantryItem,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    // For visual fill, use effective stock level for STOCK_LEVEL items, percentRemaining for UNITS
    val fillPercent = when (item.trackingStyle) {
        TrackingStyle.STOCK_LEVEL -> when (item.effectiveStockLevel) {
            StockLevel.OUT_OF_STOCK -> 0f
            StockLevel.LOW -> 0.15f
            StockLevel.SOME -> 0.5f
            StockLevel.PLENTY -> 0.85f
        }
        TrackingStyle.UNITS -> item.percentRemaining
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
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Category emoji placeholder
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getCategoryEmoji(item.category),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }

                // Gradient overlay for text readability (blue when active, black otherwise)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = if (isExpanded) listOf(
                                    Color.Transparent,
                                    Color(0xFF22D3EE).copy(alpha = 0.8f)  // Cyan gradient when active
                                ) else listOf(
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

                // Expiry date indicator (top left)
                item.expiryDate?.let { expiry ->
                    val today = java.time.LocalDate.now()
                    val daysUntilExpiry = java.time.temporal.ChronoUnit.DAYS.between(today, expiry).toInt()
                    val isExpiringSoon = daysUntilExpiry <= 3
                    val isExpired = daysUntilExpiry < 0

                    val expiryText = when {
                        isExpired -> "Expired"
                        daysUntilExpiry == 0 -> "Today"
                        daysUntilExpiry == 1 -> "Tomorrow"
                        daysUntilExpiry <= 7 -> "${daysUntilExpiry}d"
                        else -> expiry.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isExpired -> MaterialTheme.colorScheme.error
                            isExpiringSoon -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        }
                    ) {
                        Text(
                            text = expiryText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isExpired -> MaterialTheme.colorScheme.onError
                                isExpiringSoon -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Low stock indicator (top right)
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

/**
 * Bottom overlay wrapper for the adjuster, with proper styling and padding.
 */
@Composable
private fun AdjusterOverlay(
    item: PantryItem,
    onSaveQuantity: (Double) -> Unit,
    onSaveStockLevel: (StockLevel) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        AdjusterRow(
            item = item,
            onSaveQuantity = onSaveQuantity,
            onSaveStockLevel = onSaveStockLevel,
            onDelete = onDelete,
            onClose = onClose
        )
    }
}

@Composable
private fun AdjusterRow(
    item: PantryItem,
    onSaveQuantity: (Double) -> Unit,
    onSaveStockLevel: (StockLevel) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    // Store original values when pane opens (for undo)
    val originalQuantity = remember { item.quantityRemaining }
    val originalStockLevel = remember { item.effectiveStockLevel }

    // Current editing state
    var currentStockLevel by remember(item.effectiveStockLevel) { mutableStateOf(item.effectiveStockLevel) }
    var currentQuantity by remember(item.quantityRemaining) { mutableDoubleStateOf(item.quantityRemaining) }

    // Check if values have changed from original
    val hasChanges = when (item.trackingStyle) {
        TrackingStyle.STOCK_LEVEL -> currentStockLevel != originalStockLevel
        TrackingStyle.UNITS -> currentQuantity != originalQuantity
    }

    // Debounce auto-save: wait 250ms after last change before saving
    val coroutineScope = rememberCoroutineScope()
    var saveJob by remember { mutableStateOf<Job?>(null) }

    fun debounceSaveQuantity(quantity: Double) {
        saveJob?.cancel()
        saveJob = coroutineScope.launch {
            delay(250L)
            onSaveQuantity(quantity)
        }
    }

    fun debounceSaveStockLevel(level: StockLevel) {
        saveJob?.cancel()
        saveJob = coroutineScope.launch {
            delay(250L)
            onSaveStockLevel(level)
        }
    }

    // All tracking types use single-row layout: Delete | content | Undo
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Delete button
        IconButton(
            onClick = onDelete,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete item"
            )
        }

        // Center content varies by tracking type (simplified to 2 types)
        when (item.trackingStyle) {
            TrackingStyle.STOCK_LEVEL -> {
                // Single row of stock level chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StockLevel.entries.forEach { level ->
                        StockLevelChip(
                            level = level,
                            isSelected = currentStockLevel == level,
                            onClick = {
                                currentStockLevel = level
                                debounceSaveStockLevel(level)
                            }
                        )
                    }
                }
            }
            TrackingStyle.UNITS -> {
                // Minus | value | Plus for discrete countable items
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RepeatingIconButton(
                        onClick = {
                            val newQty = (currentQuantity - getIncrement(currentQuantity, item.quantityInitial)).coerceAtLeast(0.0)
                            currentQuantity = newQty
                            debounceSaveQuantity(newQty)
                        },
                        enabled = currentQuantity > 0,
                        delayMillis = getRepeatDelay(currentQuantity)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.widthIn(min = 80.dp)
                    ) {
                        Text(
                            text = formatQuantity(currentQuantity),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = item.unit.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    RepeatingIconButton(
                        onClick = {
                            val newQty = currentQuantity + getIncrement(currentQuantity, item.quantityInitial)
                            currentQuantity = newQty
                            debounceSaveQuantity(newQty)
                        },
                        delayMillis = getRepeatDelay(currentQuantity)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }
            }
        }

        // Undo button
        IconButton(
            onClick = {
                if (item.trackingStyle == TrackingStyle.STOCK_LEVEL) {
                    currentStockLevel = originalStockLevel
                    debounceSaveStockLevel(originalStockLevel)
                } else {
                    currentQuantity = originalQuantity
                    debounceSaveQuantity(originalQuantity)
                }
            },
            enabled = hasChanges
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Undo changes",
                tint = if (hasChanges) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                }
            )
        }
    }
}

@Composable
private fun StockLevelChip(
    level: StockLevel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (containerColor, labelColor, borderColor) = when (level) {
        StockLevel.OUT_OF_STOCK -> Triple(
            if (isSelected) Color(0xFFFFCDD2) else Color.Transparent,
            if (isSelected) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface,
            Color(0xFFE57373)
        )
        StockLevel.LOW -> Triple(
            if (isSelected) Color(0xFFFFE0B2) else Color.Transparent,
            if (isSelected) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface,
            Color(0xFFFFB74D)
        )
        StockLevel.SOME -> Triple(
            if (isSelected) Color(0xFFFFF9C4) else Color.Transparent,
            if (isSelected) Color(0xFFF9A825) else MaterialTheme.colorScheme.onSurface,
            Color(0xFFFFD54F)
        )
        StockLevel.PLENTY -> Triple(
            if (isSelected) Color(0xFFC8E6C9) else Color.Transparent,
            if (isSelected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
            Color(0xFF81C784)
        )
    }

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = level.displayName,
                color = labelColor,
                style = MaterialTheme.typography.labelMedium
            )
        },
        modifier = Modifier.width(72.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = containerColor,
            containerColor = Color.Transparent
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = borderColor,
            selectedBorderColor = borderColor,
            borderWidth = 1.dp,
            selectedBorderWidth = 2.dp
        )
    )
}

/**
 * Format a quantity for display, showing decimals only when needed.
 */
private fun formatQuantity(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format("%.1f", value).trimEnd('0').trimEnd('.')
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
                AnimatedVisibility(
                    visible = isPerishable,
                    enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
                    exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(100))
                ) {
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

/**
 * Determine the increment step size based on the current quantity.
 * Single digits: 1, double digits: 5, triple digits: 10, larger: 25
 */
private fun getIncrement(currentQuantity: Double, initialQuantity: Double): Double {
    val referenceQty = maxOf(currentQuantity, initialQuantity)
    return when {
        referenceQty < 10 -> 1.0
        referenceQty < 100 -> 5.0
        referenceQty < 500 -> 10.0
        else -> 25.0
    }
}

/**
 * Determine the repeat delay based on the current quantity.
 * Smaller quantities get slower repeat (more precision needed),
 * larger quantities get faster repeat.
 */
private fun getRepeatDelay(currentQuantity: Double): Long {
    return when {
        currentQuantity < 10 -> 200L
        currentQuantity < 100 -> 150L
        currentQuantity < 500 -> 100L
        else -> 75L
    }
}

/**
 * An icon button that triggers repeatedly when held down.
 * First triggers immediately on press, then after an initial delay,
 * starts repeating at the specified interval.
 */
@Composable
private fun RepeatingIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    delayMillis: Long = 150L,
    initialDelayMillis: Long = 400L,
    content: @Composable () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }

    // Track press state via InteractionSource (works inside SwipeToDismissBox)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }

    // Handle repeat while pressed
    LaunchedEffect(pressed) {
        if (pressed && enabled) {
            currentOnClick()  // Immediate action on press
            delay(initialDelayMillis)
            while (pressed) {
                currentOnClick()
                delay(delayMillis)
            }
        }
    }

    FilledIconButton(
        onClick = { },  // Empty - we handle via InteractionSource
        enabled = enabled,
        interactionSource = interactionSource
    ) {
        content()
    }
}
