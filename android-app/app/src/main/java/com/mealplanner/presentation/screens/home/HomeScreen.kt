package com.mealplanner.presentation.screens.home

import com.mealplanner.presentation.theme.Tomato600
import com.mealplanner.presentation.theme.Tomato700

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mealplanner.domain.model.PlannedRecipe
import com.mealplanner.domain.model.Recipe

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToMeals: () -> Unit,
    onNavigateToShopping: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onRecipeClick: (Recipe) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Mise",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = uiState.weekDisplayText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Tomato600, // Explicitly Tomato
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = androidx.compose.ui.graphics.Color.White, // Always White on Tomato
                    actionIconContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // This Week's Meals Section
            item {
                ThisWeekSection(
                    uiState = uiState,
                    onRecipeClick = onRecipeClick,
                    onNavigateToMeals = onNavigateToMeals,
                    onToggleCooked = { recipe ->
                        if (recipe.cooked) {
                            viewModel.unmarkRecipeCooked(recipe)
                        } else {
                            viewModel.markRecipeCooked(recipe)
                        }
                    }
                )
            }

            // Quick Stats Grid
            item {
                QuickStatsSection(
                    pantryCount = uiState.pantryItemCount,
                    recipeCount = uiState.savedRecipeCount
                )
            }

            // Quick Actions
            item {
                QuickActionsSection(
                    hasPlannedMeals = uiState.totalPlanned > 0,
                    onNavigateToMeals = onNavigateToMeals,
                    onNavigateToShopping = onNavigateToShopping
                )
            }
        }
    }
}

@Composable
private fun ThisWeekSection(
    uiState: HomeUiState,
    onRecipeClick: (Recipe) -> Unit,
    onNavigateToMeals: () -> Unit,
    onToggleCooked: (PlannedRecipe) -> Unit
) {
    Column {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "This Week",
                style = MaterialTheme.typography.titleMedium
            )
            if (uiState.totalPlanned > 0) {
                Text(
                    text = "${uiState.cookedCount}/${uiState.totalPlanned} cooked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.totalPlanned > 0) {
            // Progress bar
            LinearProgressIndicator(
                progress = { uiState.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Recipe list
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.plannedRecipes.forEach { plannedRecipe ->
                    PlannedRecipeItem(
                        plannedRecipe = plannedRecipe,
                        onClick = { onRecipeClick(plannedRecipe.recipe) },
                        onToggleCooked = { onToggleCooked(plannedRecipe) }
                    )
                }
            }
        } else {
            // Empty state
            EmptyMealPlanCard(onNavigateToMeals = onNavigateToMeals)
        }
    }
}

@Composable
private fun PlannedRecipeItem(
    plannedRecipe: PlannedRecipe,
    onClick: () -> Unit,
    onToggleCooked: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (plannedRecipe.cooked) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (plannedRecipe.cooked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    )
                    .clickable { onToggleCooked() },
                contentAlignment = Alignment.Center
            ) {
                if (plannedRecipe.cooked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Cooked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = "Not cooked",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Recipe info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plannedRecipe.recipe.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (plannedRecipe.cooked) TextDecoration.LineThrough else null,
                    color = if (plannedRecipe.cooked) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = "${plannedRecipe.recipe.totalTimeMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyMealPlanCard(onNavigateToMeals: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No meal plan for this week",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onNavigateToMeals) {
                Text("Create Plan")
            }
        }
    }
}

@Composable
private fun QuickStatsSection(
    pantryCount: Int,
    recipeCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard(
            count = pantryCount,
            label = "Pantry items",
            icon = Icons.Default.Kitchen,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            count = recipeCount,
            label = "Saved recipes",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    count: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActionsSection(
    hasPlannedMeals: Boolean,
    onNavigateToMeals: () -> Unit,
    onNavigateToShopping: () -> Unit
) {
    Column {
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionButton(
                text = if (hasPlannedMeals) "View Meals" else "Plan This Week's Meals",
                icon = Icons.Default.CalendarMonth,
                isPrimary = true,
                onClick = onNavigateToMeals
            )

            QuickActionButton(
                text = "View Shopping List",
                icon = Icons.Default.ShoppingCart,
                isPrimary = false,
                onClick = onNavigateToShopping
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    if (isPrimary) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text)
            Spacer(modifier = Modifier.weight(1f))
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text)
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
