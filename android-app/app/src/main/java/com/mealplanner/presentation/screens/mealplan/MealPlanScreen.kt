package com.mealplanner.presentation.screens.mealplan

import com.mealplanner.presentation.theme.Pacific500
import com.mealplanner.presentation.theme.Pacific600
import com.mealplanner.presentation.theme.Pacific700
import androidx.compose.foundation.isSystemInDarkTheme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mealplanner.domain.model.MealPlan
import kotlinx.coroutines.launch
import com.mealplanner.domain.model.PlannedRecipe
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeSearchResult
import com.mealplanner.domain.model.ShoppingCategories
import com.mealplanner.domain.model.ShoppingItem
import com.mealplanner.domain.model.ShoppingList
import com.mealplanner.presentation.components.GenerationLoadingScreen
import androidx.compose.ui.text.style.TextDecoration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    onNavigateToShopping: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onRecipeClick: (Recipe) -> Unit = {},
    viewModel: MealPlanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val generationProgress by viewModel.generationProgress.collectAsState()
    val browseState by viewModel.browseState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val shoppingList by viewModel.shoppingList.collectAsState()
    val shoppingCompletionState by viewModel.shoppingCompletionState.collectAsState()

    // Shopping completion dialog
    shoppingCompletionState?.let { completion ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissShoppingCompletion() },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("Shopping Complete!") },
            text = {
                Text(
                    "${completion.itemsAddedToPantry} items have been added to your pantry.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissShoppingCompletion() }) {
                    Text("Done")
                }
            }
        )
    }

    // ActivePlan state has its own Scaffold with dynamic TopAppBar
    if (uiState is MealPlanUiState.ActivePlan) {
        val state = uiState as MealPlanUiState.ActivePlan
        ActivePlanContent(
            mealPlan = state.mealPlan,
            viewMode = state.viewMode,
            shoppingList = shoppingList,
            onRecipeClick = onRecipeClick,
            onToggleViewMode = { viewModel.toggleViewMode() },
            onToggleCooked = { id, cooked -> viewModel.toggleCooked(id, cooked) },
            onToggleItemChecked = { viewModel.toggleItemChecked(it) },
            onMarkShoppingComplete = { viewModel.markShoppingComplete() },
            onStartNewPlan = { viewModel.startNewPlan() }
        )
        return
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Meals") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Pacific600,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is MealPlanUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is MealPlanUiState.Empty -> {
                    EmptyMealPlanContent(
                        onGenerateAI = { viewModel.generateMealPlan() }
                    )
                }

                is MealPlanUiState.Generating -> {
                    GenerationLoadingScreen(
                        progress = generationProgress,
                        onCancel = { viewModel.cancelGeneration() }
                    )
                }

                is MealPlanUiState.Browsing -> {
                    BrowsingContent(
                        browseState = browseState,
                        categories = categories,
                        onCategorySelected = { viewModel.setCategory(it) },
                        onRecipeToggle = { viewModel.toggleRecipeInBrowse(it) },
                        onConfirm = { viewModel.confirmBrowseSelection() },
                        onCancel = { viewModel.cancelBrowsing() }
                    )
                }

                is MealPlanUiState.SelectingRecipes -> {
                    RecipeSelectionContent(
                        recipes = state.generatedPlan.recipes,
                        selectedIndices = state.selectedIndices,
                        onToggleSelection = { viewModel.toggleRecipeSelection(it) },
                        onSave = { viewModel.saveMealPlan() },
                        onRegenerate = { viewModel.reset() },
                        onRecipeClick = onRecipeClick
                    )
                }

                is MealPlanUiState.Saving -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Saving meal plan...")
                        }
                    }
                }

                is MealPlanUiState.PolishingGroceryList -> {
                    GroceryListLoadingScreen()
                }

                is MealPlanUiState.ActivePlan -> {
                    // Handled above
                }

                is MealPlanUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.dismissError() }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMealPlanContent(
    onGenerateAI: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "\uD83C\uDF7D\uFE0F",
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Ready to plan your week?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Generate personalized meal-kit style recipes tailored to your preferences",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onGenerateAI,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate Meal Plan")
        }
    }
}

@Composable
private fun BrowsingContent(
    browseState: BrowseState,
    categories: List<String>,
    onCategorySelected: (String?) -> Unit,
    onRecipeToggle: (RecipeSearchResult) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Category filter carousel
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    FilterChip(
                        selected = browseState.selectedCategory == null,
                        onClick = { onCategorySelected(null) },
                        label = { Text("All") }
                    )
                }
                items(categories) { category ->
                    FilterChip(
                        selected = browseState.selectedCategory == category,
                        onClick = { onCategorySelected(category) },
                        label = { Text(category) }
                    )
                }
            }

            // Selection info bar
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select up to 6 recipes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "${browseState.selectedRecipes.size}/6",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Recipe grid
            if (browseState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(browseState.recipes) { recipe ->
                        val isSelected = browseState.selectedRecipes.any { it.sourceId == recipe.sourceId }
                        BrowseRecipeCard(
                            recipe = recipe,
                            isSelected = isSelected,
                            onToggle = { onRecipeToggle(recipe) }
                        )
                    }
                }
            }
        }

        // Bottom action buttons
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = browseState.selectedRecipes.isNotEmpty()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm (${browseState.selectedRecipes.size})")
                }
            }
        }
    }
}

@Composable
private fun BrowseRecipeCard(
    recipe: RecipeSearchResult,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            Column {
                // Placeholder image area with gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = recipe.name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }

                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = recipe.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recipe.totalTimeMinutes?.let { time ->
                            Text(
                                text = "$time min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${recipe.servings} serv",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Selection indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// Pacific Blue from color schema
private val PacificBlue = Color(0xFF009FB7)

// Recipe categories for filtering
private enum class RecipeCategory(val displayName: String) {
    ALL("All"),
    VEGETARIAN("Vegetarian"),
    WEEKEND("Weekend"),
    SIMPLE("Simple"),
    ETHNIC("Ethnic"),
    SEASONAL("Seasonal")
}

private fun Recipe.matchesCategory(category: RecipeCategory): Boolean {
    return when (category) {
        RecipeCategory.ALL -> true
        RecipeCategory.VEGETARIAN -> tags.any {
            it.lowercase().contains("vegetarian") || it.lowercase().contains("vegan")
        }
        RecipeCategory.WEEKEND -> totalTimeMinutes >= 60
        RecipeCategory.SIMPLE -> totalTimeMinutes <= 30 || ingredients.size <= 8
        RecipeCategory.ETHNIC -> tags.any { tag ->
            listOf("asian", "mexican", "italian", "indian", "thai", "chinese", "japanese",
                   "mediterranean", "greek", "middle eastern", "korean", "vietnamese")
                .any { tag.lowercase().contains(it) }
        }
        RecipeCategory.SEASONAL -> tags.any { tag ->
            listOf("summer", "winter", "fall", "autumn", "spring", "holiday", "festive")
                .any { tag.lowercase().contains(it) }
        }
    }
}

@Composable
private fun RecipeSelectionContent(
    recipes: List<Recipe>,
    selectedIndices: Set<Int>,
    onToggleSelection: (Int) -> Unit,
    onSave: () -> Unit,
    onRegenerate: () -> Unit,
    onRecipeClick: (Recipe) -> Unit
) {
    val maxSelections = 6
    val selectedCount = selectedIndices.size
    var selectedCategory by remember { mutableStateOf(RecipeCategory.ALL) }

    // Sort recipes: selected first, then by original order
    // Also filter by category
    val sortedRecipes = remember(recipes, selectedIndices, selectedCategory) {
        recipes.mapIndexed { index, recipe -> index to recipe }
            .filter { (_, recipe) -> recipe.matchesCategory(selectedCategory) }
            .sortedByDescending { (index, _) -> selectedIndices.contains(index) }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Category carousel
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(RecipeCategory.entries.toList()) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PacificBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Recipe list - single column
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = sortedRecipes,
                    key = { (index, _) -> index }
                ) { (index, recipe) ->
                    SelectableRecipeCard(
                        recipe = recipe,
                        isSelected = selectedIndices.contains(index),
                        onToggle = { onToggleSelection(index) },
                        onClick = { onRecipeClick(recipe) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        // Circular long-press save button
        LongPressConfirmButton(
            selectedCount = selectedCount,
            maxCount = maxSelections,
            enabled = selectedIndices.isNotEmpty(),
            onConfirm = onSave,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun SelectableRecipeCard(
    recipe: Recipe,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,  // Tap card = view recipe details
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = PacificBlue,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            Column {
                // Recipe image or placeholder
                if (recipe.imageUrl != null) {
                    AsyncImage(
                        model = recipe.imageUrl,
                        contentDescription = recipe.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = recipe.name.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    }
                }

                // Recipe details
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = recipe.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = recipe.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${recipe.totalTimeMinutes} min",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${recipe.servings} servings",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Selection circle - always visible, tap to toggle
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) PacificBlue else Color.White.copy(alpha = 0.9f))
                    .border(2.dp, if (isSelected) PacificBlue else Color.Gray.copy(alpha = 0.5f), CircleShape)
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Circular button with long-press-to-confirm animation.
 * Shows selection count (X/6) or checkmark when all selected.
 * User must long-press for 1.5s to confirm - inner circle grows to fill the outer ring.
 */
@Composable
private fun LongPressConfirmButton(
    selectedCount: Int,
    maxCount: Int,
    enabled: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }

    // Sizes for the expanding circle effect
    val outerSize = 100.dp
    val innerSizeMin = 52.dp  // Smaller initial button
    val innerSizeMax = 96.dp  // Grows to nearly fill outer circle on long-press

    val allSelected = selectedCount >= maxCount
    val buttonColor = if (enabled) PacificBlue else PacificBlue.copy(alpha = 0.4f)

    // Calculate current inner size based on progress
    val currentInnerSize = innerSizeMin + (innerSizeMax - innerSizeMin) * progress.value

    Box(
        modifier = modifier
            .size(outerSize)
            .clip(CircleShape)
            .background(buttonColor.copy(alpha = 0.2f)) // Outer ring background
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        // Start animation
                        val animationJob = scope.launch {
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 1500)
                            )
                            // Animation completed - trigger confirm
                            onConfirm()
                        }
                        // Wait for release
                        tryAwaitRelease()
                        // Cancel if not complete
                        if (progress.value < 1f) {
                            animationJob.cancel()
                            scope.launch {
                                progress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 200)
                                )
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Inner filled circle that grows
        Box(
            modifier = Modifier
                .size(currentInnerSize)
                .clip(CircleShape)
                .background(buttonColor),
            contentAlignment = Alignment.Center
        ) {
            if (allSelected) {
                // Show checkmark when all 6 selected
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "All selected",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                // Show count X/6
                Text(
                    text = "$selectedCount/$maxCount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivePlanContent(
    mealPlan: MealPlan,
    viewMode: ViewMode,
    shoppingList: ShoppingList?,
    onRecipeClick: (Recipe) -> Unit,
    onToggleViewMode: () -> Unit,
    onToggleCooked: (Long, Boolean) -> Unit,
    onToggleItemChecked: (Long) -> Unit,
    onMarkShoppingComplete: () -> Unit,
    onStartNewPlan: () -> Unit
) {
    val shoppingComplete = mealPlan.shoppingComplete

    // Determine what to show based on shopping status and view mode
    // Pre-shopping: PRIMARY = Grocery List, SECONDARY = Weekly Meals
    // Post-shopping: PRIMARY = Weekly Meals, SECONDARY = Grocery List
    val showGroceryList = if (shoppingComplete) {
        viewMode == ViewMode.SECONDARY
    } else {
        viewMode == ViewMode.PRIMARY
    }

    val title = if (showGroceryList) "Grocery List" else "Weekly Meals"
    val secondaryTitle = if (showGroceryList) "Meals" else "Groceries"
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                scrollBehavior = scrollBehavior,
                actions = {
                    // Toggle button to switch views
                    TextButton(onClick = onToggleViewMode) {
                        Icon(
                            if (showGroceryList) Icons.Default.Restaurant else Icons.Default.ShoppingCart,
                            contentDescription = "Switch to $secondaryTitle",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(secondaryTitle)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Pacific600,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = androidx.compose.ui.graphics.Color.White,
                    actionIconContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showGroceryList) {
                EmbeddedGroceryList(
                    shoppingList = shoppingList,
                    shoppingComplete = shoppingComplete,
                    onItemCheckedChange = onToggleItemChecked,
                    onMarkShoppingComplete = onMarkShoppingComplete
                )
            } else {
                WeeklyMealsContent(
                    mealPlan = mealPlan,
                    onRecipeClick = onRecipeClick,
                    onToggleCooked = onToggleCooked,
                    onStartNewPlan = onStartNewPlan
                )
            }
        }
    }
}

@Composable
private fun EmbeddedGroceryList(
    shoppingList: ShoppingList?,
    shoppingComplete: Boolean,
    onItemCheckedChange: (Long) -> Unit,
    onMarkShoppingComplete: () -> Unit
) {
    if (shoppingList == null || shoppingList.items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No items in grocery list",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val groupedItems = remember(shoppingList) {
        ShoppingCategories.orderedCategories
            .associateWith { category -> shoppingList.items.filter { it.category == category } }
            .filterValues { it.isNotEmpty() }
    }

    // Dynamic subheader color: Darker Pacific in light mode, Lighter Pacific in dark mode
    val subheaderColor = if (isSystemInDarkTheme()) Pacific500 else Pacific700
    val onSubheaderColor = Color.White

    Column(modifier = Modifier.fillMaxSize()) {
        // Progress header
        Surface(
            color = subheaderColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Shopping Progress",
                            style = MaterialTheme.typography.titleMedium,
                            color = onSubheaderColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${shoppingList.checkedItems} of ${shoppingList.totalItems} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSubheaderColor.copy(alpha = 0.7f)
                        )
                    }
                    if (shoppingComplete) {
                        // Shopping complete badge
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = subheaderColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Complete",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = subheaderColor
                                )
                            }
                        }
                    } else if (shoppingList.checkedItems > 0) {
                        Button(
                            onClick = onMarkShoppingComplete,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = subheaderColor
                            )
                        ) {
                            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Done Shopping")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { shoppingList.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }

        // Grouped items by category
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            groupedItems.forEach { (category, items) ->
                item(key = "header_$category") {
                    EmbeddedCategoryHeader(
                        category = category,
                        itemCount = items.size,
                        checkedCount = items.count { it.checked }
                    )
                }

                items(
                    items = items,
                    key = { it.id }
                ) { item ->
                    EmbeddedShoppingItem(
                        item = item,
                        enabled = !shoppingComplete,
                        onCheckedChange = { onItemCheckedChange(item.id) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun EmbeddedCategoryHeader(
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
            color = Pacific600
        )
        Text(
            text = "$checkedCount/$itemCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmbeddedShoppingItem(
    item: ShoppingItem,
    enabled: Boolean = true,
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
                onCheckedChange = if (enabled) { { onCheckedChange() } } else null,
                enabled = enabled
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
private fun WeeklyMealsContent(
    mealPlan: MealPlan,
    onRecipeClick: (Recipe) -> Unit,
    onToggleCooked: (Long, Boolean) -> Unit,
    onStartNewPlan: () -> Unit
) {
    val cookedCount = mealPlan.cookedCount
    val totalRecipes = mealPlan.totalRecipes
    val progress = if (totalRecipes > 0) cookedCount.toFloat() / totalRecipes else 0f
    val allCooked = cookedCount == totalRecipes && totalRecipes > 0

    // Dynamic subheader color: Darker Pacific in light mode, Lighter Pacific in dark mode
    val subheaderColor = if (isSystemInDarkTheme()) Pacific500 else Pacific700
    val onSubheaderColor = Color.White

    Column(modifier = Modifier.fillMaxSize()) {
        // Progress header
        Surface(
            color = subheaderColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (allCooked) "Week Complete!" else "Cooking Progress",
                            style = MaterialTheme.typography.titleMedium,
                            color = onSubheaderColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$cookedCount of $totalRecipes meals cooked",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSubheaderColor.copy(alpha = 0.7f)
                        )
                    }
                    if (allCooked) {
                        Button(
                            onClick = onStartNewPlan,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = subheaderColor
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Plan")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }

        // Recipes list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(mealPlan.recipes) { plannedRecipe ->
                WeeklyMealCard(
                    plannedRecipe = plannedRecipe,
                    onClick = { onRecipeClick(plannedRecipe.recipe) },
                    onToggleCooked = { onToggleCooked(plannedRecipe.id, plannedRecipe.cooked) }
                )
            }
        }
    }
}

@Composable
private fun WeeklyMealCard(
    plannedRecipe: PlannedRecipe,
    onClick: () -> Unit,
    onToggleCooked: () -> Unit
) {
    val recipe = plannedRecipe.recipe

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (plannedRecipe.cooked) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (plannedRecipe.cooked) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cooked toggle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (plannedRecipe.cooked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .clickable { onToggleCooked() },
                contentAlignment = Alignment.Center
            ) {
                if (plannedRecipe.cooked) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Cooked",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = recipe.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (plannedRecipe.cooked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${recipe.totalTimeMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${recipe.servings} servings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "\u26A0\uFE0F",
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}

/**
 * Loading screen displayed while the grocery list is being polished by Gemini.
 * Shows a white background with a placeholder icon and loading indicator.
 */
@Composable
private fun GroceryListLoadingScreen() {
    // Pulsing animation for the icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Placeholder icon - shopping cart with groceries
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = PacificBlue.copy(alpha = alpha)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Loading text
            Text(
                text = "Building your grocery list...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Organizing items by aisle",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Simple progress indicator
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = PacificBlue,
                strokeWidth = 3.dp
            )
        }
    }
}
