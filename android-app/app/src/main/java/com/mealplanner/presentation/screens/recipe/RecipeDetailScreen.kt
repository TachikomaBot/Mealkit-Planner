package com.mealplanner.presentation.screens.recipe

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mealplanner.domain.model.CookingStep
import com.mealplanner.domain.model.ModifiedIngredient
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeCustomizationResult
import com.mealplanner.domain.model.RecipeIngredient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipe: Recipe,
    selectionIndex: Int? = null,  // Non-null = recipe is from selection stage
    onBack: () -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel()
) {
    val rating by viewModel.rating.collectAsState()
    val recipeHistory by viewModel.recipeHistory.collectAsState()
    val plannedRecipe by viewModel.plannedRecipe.collectAsState()
    val shoppingComplete by viewModel.shoppingComplete.collectAsState()
    val customizationState by viewModel.customizationState.collectAsState()
    val selectionModeRecipeState by viewModel.selectionModeRecipe.collectAsState()

    // Use the appropriate recipe version:
    // 1. In selection mode: use the selection mode recipe (may have been customized)
    // 2. With planned recipe: use the planned recipe's version (may have been customized)
    // 3. Fallback: use the original recipe passed in
    val displayRecipe = selectionModeRecipeState ?: plannedRecipe?.recipe ?: recipe

    // Load recipe data on launch
    LaunchedEffect(recipe.name, selectionIndex) {
        viewModel.loadRecipeData(recipe.name, selectionIndex)
        // Set recipe reference for selection mode customization
        if (selectionIndex != null) {
            viewModel.setSelectionModeRecipe(recipe)
        }
    }

    RecipeDetailContent(
        recipe = displayRecipe,
        rating = rating,
        recipeHistory = recipeHistory,
        plannedRecipe = plannedRecipe,
        shoppingComplete = shoppingComplete,
        isSelectionMode = selectionIndex != null,
        viewModel = viewModel,
        onBack = onBack,
        onIMadeThis = { viewModel.markAsCooked(displayRecipe) },
        onCustomize = { viewModel.showCustomizeDialog(displayRecipe) }
    )

    // Customization bottom sheet overlay
    val showCustomizationSheet = customizationState !is CustomizationState.Idle &&
            customizationState !is CustomizationState.Error

    if (showCustomizationSheet) {
        val currentRecipe = when (val state = customizationState) {
            is CustomizationState.InputDialog -> state.recipe
            is CustomizationState.Loading -> displayRecipe  // Keep using current recipe during loading
            is CustomizationState.Preview -> state.recipe
            is CustomizationState.Applying -> displayRecipe
            else -> displayRecipe
        }

        CustomizationBottomSheet(
            state = customizationState,
            onSubmit = { request -> viewModel.submitCustomization(currentRecipe, request) },
            onApply = { viewModel.applyCustomization() },
            onRefine = { viewModel.refineCustomization() },
            onCancel = { viewModel.cancelCustomization() }
        )
    }

    // Error dialog (separate from bottom sheet)
    if (customizationState is CustomizationState.Error) {
        CustomizationErrorDialog(
            message = (customizationState as CustomizationState.Error).message,
            onDismiss = { viewModel.dismissCustomizationError() },
            onRetry = { viewModel.showCustomizeDialog((customizationState as CustomizationState.Error).recipe) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeDetailContent(
    recipe: Recipe,
    rating: Int?,
    recipeHistory: com.mealplanner.domain.model.RecipeHistory?,
    plannedRecipe: com.mealplanner.domain.model.PlannedRecipe?,
    shoppingComplete: Boolean,
    isSelectionMode: Boolean,
    viewModel: RecipeDetailViewModel,
    onBack: () -> Unit,
    onIMadeThis: () -> Unit,
    onCustomize: () -> Unit
) {
    // Determine if we should show the "I Made This" button
    val isInMealPlan = plannedRecipe != null
    val isCooked = plannedRecipe?.cooked == true || recipeHistory != null

    // Scroll state for FAB alpha calculation
    val scrollState = rememberLazyListState()

    // Calculate FAB alpha based on scroll position
    // Fully opaque at top, fades to 0.4 as user scrolls into content
    val fabAlpha by remember {
        derivedStateOf {
            val firstVisibleIndex = scrollState.firstVisibleItemIndex
            val offset = scrollState.firstVisibleItemScrollOffset
            when {
                firstVisibleIndex == 0 && offset < 200 -> 1f  // At top, fully opaque
                firstVisibleIndex == 0 -> 1f - ((offset - 200) / 400f).coerceIn(0f, 0.6f)  // Fading
                else -> 0.4f  // Scrolled into content, minimum alpha
            }
        }
    }

    // Show customize FAB if:
    // 1. In selection mode (recipe being chosen for the week), OR
    // 2. Recipe is in meal plan and shopping is not complete
    val showCustomizeFab = isSelectionMode || (isInMealPlan && !shoppingComplete)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(4.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 0.dp),
                contentPadding = PaddingValues(bottom = 80.dp)  // Extra padding for FAB
            ) {
                // Hero image with gradient overlay
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    if (recipe.imageUrl != null) {
                        AsyncImage(
                            model = recipe.imageUrl,
                            contentDescription = recipe.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Placeholder gradient background
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restaurant,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // Gradient overlay at bottom for text readability
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                    )

                    // Recipe title overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = recipe.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Spacing between hero and content
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Recipe description and meta info
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = recipe.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Meta info cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetaInfoCard(
                            icon = Icons.Default.Schedule,
                            value = "${recipe.prepTimeMinutes}",
                            label = "Prep",
                            unit = "min"
                        )
                        MetaInfoCard(
                            icon = Icons.Default.LocalFireDepartment,
                            value = "${recipe.cookTimeMinutes}",
                            label = "Cook",
                            unit = "min"
                        )
                        MetaInfoCard(
                            icon = Icons.Default.People,
                            value = "${recipe.servings}",
                            label = "Serves",
                            unit = ""
                        )
                        MetaInfoCard(
                            icon = Icons.Default.AccessTime,
                            value = "${recipe.totalTimeMinutes}",
                            label = "Total",
                            unit = "min"
                        )
                    }

                    // Tags
                    if (recipe.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recipe.tags.take(6)) { tag ->
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }

            // Divider
            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            // Ingredients section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ingredients",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${recipe.ingredients.size} items",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(recipe.ingredients) { _, ingredient ->
                IngredientItem(
                    ingredient = ingredient,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Divider
            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            // Instructions section
            item {
                Text(
                    text = "Instructions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            itemsIndexed(recipe.steps) { index, step ->
                CookingStepItem(
                    stepNumber = index + 1,
                    step = step,
                    isLast = index == recipe.steps.lastIndex,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Completion and Rating section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Show "I Made This!" button if in meal plan but not cooked yet
            if (isInMealPlan && !isCooked) {
                item {
                    IMadeThisButton(
                        onMarkCooked = onIMadeThis
                    )
                }
            }

            // Show rating section if cooked (either now or previously)
            if (isCooked) {
                item {
                    RatingSection(
                        isNewlyCooked = plannedRecipe?.cooked == true && recipeHistory != null,
                        rating = rating,
                        onRatingChange = { viewModel.setRating(it) }
                    )
                }
            }
        }

            // Customize recipe FAB - hidden when shopping is complete
            AnimatedVisibility(
                visible = showCustomizeFab,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 32.dp)
            ) {
                FloatingActionButton(
                    onClick = onCustomize,
                    modifier = Modifier.graphicsLayer { alpha = fabAlpha },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = fabAlpha),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "Customize recipe"
                    )
                }
            }
        }
    }
}

@Composable
private fun IMadeThisButton(
    onMarkCooked: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ready to cook?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Mark this recipe as complete and rate it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onMarkCooked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("I Made This!")
            }
        }
    }
}

@Composable
private fun MetaInfoCard(
    icon: ImageVector,
    value: String,
    label: String,
    unit: String
) {
    Card(
        modifier = Modifier.width(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (unit.isNotEmpty()) "$value $unit" else value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RatingSection(
    isNewlyCooked: Boolean,
    rating: Int?,
    onRatingChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNewlyCooked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isNewlyCooked) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Nice work!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = if (isNewlyCooked) "How was it?" else "Your rating",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Star rating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                (1..5).forEach { star ->
                    IconButton(
                        onClick = { onRatingChange(star) }
                    ) {
                        Icon(
                            imageVector = if ((rating ?: 0) >= star) {
                                Icons.Default.Star
                            } else {
                                Icons.Default.StarBorder
                            },
                            contentDescription = "Rate $star stars",
                            tint = if ((rating ?: 0) >= star) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IngredientItem(
    ingredient: RecipeIngredient,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bullet
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    if (ingredient.quantity > 0) {
                        append(formatQuantity(ingredient.quantity))
                        append(" ")
                    }
                    if (ingredient.unit.isNotBlank()) {
                        append(ingredient.unit)
                        append(" ")
                    }
                    append(ingredient.name)
                },
                style = MaterialTheme.typography.bodyLarge
            )
            ingredient.preparation?.let { prep ->
                Text(
                    text = prep,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CookingStepItem(
    stepNumber: Int,
    step: CookingStep,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Step number with vertical line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight()
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stepNumber.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Step content - align with the center of the circle
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 8.dp)
        ) {
            // Title row - vertically centered with the step number
            Box(
                modifier = Modifier.height(32.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (step.substeps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        step.substeps.forEach { substep ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = parseMarkdownBold(substep),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Format a quantity for display, converting decimals to fractions where appropriate.
 * Examples: 0.5 → "1/2", 1.5 → "1 1/2", 0.25 → "1/4", 2.0 → "2"
 */
private fun formatQuantity(quantity: Double): String {
    if (quantity <= 0) return ""

    val wholePart = quantity.toLong()
    val fractionalPart = quantity - wholePart

    // If it's a whole number, just return it
    if (fractionalPart < 0.01) {
        return wholePart.toString()
    }

    // Convert fractional part to a fraction string
    val fractionStr = when {
        fractionalPart in 0.12..0.13 -> "1/8"
        fractionalPart in 0.24..0.26 -> "1/4"
        fractionalPart in 0.32..0.34 -> "1/3"
        fractionalPart in 0.37..0.38 -> "3/8"
        fractionalPart in 0.49..0.51 -> "1/2"
        fractionalPart in 0.62..0.63 -> "5/8"
        fractionalPart in 0.66..0.68 -> "2/3"
        fractionalPart in 0.74..0.76 -> "3/4"
        fractionalPart in 0.87..0.88 -> "7/8"
        else -> null // Can't convert to a nice fraction
    }

    return when {
        fractionStr != null && wholePart > 0 -> "$wholePart $fractionStr"
        fractionStr != null -> fractionStr
        wholePart > 0 -> {
            // Has a fractional part we can't convert nicely - show decimal
            String.format("%.1f", quantity)
        }
        else -> String.format("%.1f", quantity)
    }
}

/**
 * Parse markdown bold syntax (**text**) and return an AnnotatedString with bold spans.
 */
private fun parseMarkdownBold(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val boldPattern = Regex("\\*\\*(.+?)\\*\\*")

        boldPattern.findAll(text).forEach { match ->
            // Append text before the match
            if (match.range.first > currentIndex) {
                append(text.substring(currentIndex, match.range.first))
            }
            // Append the bold text
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            currentIndex = match.range.last + 1
        }
        // Append any remaining text
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}

// ========== Recipe Customization UI ==========

/**
 * Bottom-anchored customization sheet.
 * Shows a text input card at the bottom. When AI responds, a response card
 * slides up from behind showing changes, and input is replaced with action buttons.
 */
@Composable
private fun CustomizationBottomSheet(
    state: CustomizationState,
    onSubmit: (String) -> Unit,
    onApply: () -> Unit,
    onRefine: () -> Unit,
    onCancel: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    // Reset input when returning to input state (e.g., after refine)
    LaunchedEffect(state) {
        if (state is CustomizationState.InputDialog) {
            inputText = ""
        }
    }

    // Use Dialog for proper keyboard handling
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Full screen box with scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Floating card container - stops click propagation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 72.dp)
                    .imePadding()
                    .clickable(enabled = false) { /* Consume clicks */ }
            ) {
                // Response card (slides up when preview is ready)
                AnimatedVisibility(
                    visible = state is CustomizationState.Preview,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    if (state is CustomizationState.Preview) {
                        CustomizationResponseCard(
                            customization = state.customization,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // Base input card (always visible, content changes based on state)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                when (state) {
                    is CustomizationState.InputDialog -> {
                        // Text input mode
                        Text(
                            text = "Customize Recipe",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = "e.g., \"use jarred pesto\", \"make it vegetarian\"...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            minLines = 2,
                            maxLines = 4
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCancel,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = { onSubmit(inputText) },
                                modifier = Modifier.weight(1f),
                                enabled = inputText.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send")
                            }
                        }
                    }

                    is CustomizationState.Loading -> {
                        // Loading mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Customizing recipe...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is CustomizationState.Preview -> {
                        // Action buttons mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCancel,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            OutlinedButton(
                                onClick = onRefine,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Refine")
                            }
                            Button(
                                onClick = onApply,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Apply")
                            }
                        }
                    }

                        is CustomizationState.Applying -> {
                            // Applying mode
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Applying changes...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        else -> { /* Idle or Error - handled elsewhere */ }
                    }
                }
            }
        }
        }
    }
}

/**
 * Response card showing AI's proposed changes.
 * Slides up from behind the input card.
 */
@Composable
private fun CustomizationResponseCard(
    customization: RecipeCustomizationResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Changes summary
            Text(
                text = customization.changesSummary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Recipe name change
            if (customization.updatedRecipeName.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "→ ${customization.updatedRecipeName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Ingredient changes in a compact list
            val hasChanges = customization.ingredientsToAdd.isNotEmpty() ||
                    customization.ingredientsToRemove.isNotEmpty() ||
                    customization.ingredientsToModify.isNotEmpty()

            if (hasChanges) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Ingredients to add (green)
                customization.ingredientsToAdd.forEach { ingredient ->
                    IngredientChangeRow(
                        icon = Icons.Default.Add,
                        iconColor = Color(0xFF2E7D32),
                        text = formatIngredientForDisplay(ingredient),
                        textColor = Color(0xFF2E7D32)
                    )
                }

                // Ingredients to remove (red)
                customization.ingredientsToRemove.forEach { name ->
                    IngredientChangeRow(
                        icon = Icons.Default.Remove,
                        iconColor = MaterialTheme.colorScheme.error,
                        text = name,
                        textColor = MaterialTheme.colorScheme.error
                    )
                }

                // Ingredients to modify (orange)
                customization.ingredientsToModify.forEach { modified ->
                    val changeText = buildString {
                        append(modified.originalName)
                        modified.newName?.let { append(" → $it") }
                        if (modified.newQuantity != null || modified.newUnit != null) {
                            append(" (")
                            modified.newQuantity?.let { append(formatQuantity(it)) }
                            modified.newUnit?.let { append(" $it") }
                            append(")")
                        }
                    }
                    IngredientChangeRow(
                        icon = Icons.Default.Edit,
                        iconColor = Color(0xFFE65100),
                        text = changeText,
                        textColor = Color(0xFFE65100)
                    )
                }
            }

            // Notes from AI
            customization.notes?.let { notes ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun IngredientChangeRow(
    icon: ImageVector,
    iconColor: Color,
    text: String,
    textColor: Color
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )
    }
}

private fun formatIngredientForDisplay(ingredient: RecipeIngredient): String {
    return buildString {
        if (ingredient.quantity > 0) {
            append(formatQuantity(ingredient.quantity))
            append(" ")
        }
        if (ingredient.unit.isNotBlank()) {
            append(ingredient.unit)
            append(" ")
        }
        append(ingredient.name)
    }.trim()
}

/**
 * Error dialog for customization failures.
 */
@Composable
private fun CustomizationErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("Customization Failed")
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
