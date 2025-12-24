package com.mealplanner.presentation.screens.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mealplanner.domain.model.CookingStep
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeIngredient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipe: Recipe,
    onBack: () -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel()
) {
    val rating by viewModel.rating.collectAsState()
    val recipeHistory by viewModel.recipeHistory.collectAsState()
    val plannedRecipe by viewModel.plannedRecipe.collectAsState()

    // Load recipe data on launch
    LaunchedEffect(recipe.name) {
        viewModel.loadRecipeData(recipe.name)
    }

    // Determine if we should show the "I Made This" button
    val isInMealPlan = plannedRecipe != null
    val isCooked = plannedRecipe?.cooked == true || recipeHistory != null

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 0.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
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
                    status = viewModel.getIngredientStatus(ingredient.name),
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
                        onMarkCooked = { viewModel.markAsCooked(recipe) }
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
    status: IngredientStatus,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when (status) {
                        IngredientStatus.IN_STOCK -> MaterialTheme.colorScheme.primary
                        IngredientStatus.LOW_STOCK -> MaterialTheme.colorScheme.tertiary
                        IngredientStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.error
                        IngredientStatus.NOT_IN_PANTRY -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    }
                )
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
                                    text = substep,
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
