package com.mealplanner.presentation.screens.home

import com.mealplanner.presentation.theme.Tomato500
import com.mealplanner.presentation.theme.Tomato600
import com.mealplanner.presentation.theme.Tomato700

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

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

@Composable
fun HomeScreen(
    onNavigateToMeals: () -> Unit,
    onNavigateToShopping: () -> Unit,
    onRecipeClick: (Recipe) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Dynamic subheader color: Darker Tomato in light mode, Lighter Tomato in dark mode
    val subheaderColor = if (isSystemInDarkTheme()) Tomato500 else Tomato700
    val onSubheaderColor = Color.White

    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed Subheader
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
                    Text(
                        text = "This Week",
                        style = MaterialTheme.typography.titleMedium,
                        color = onSubheaderColor
                    )
                    if (uiState.totalPlanned > 0) {
                        Text(
                            text = "${uiState.cookedCount}/${uiState.totalPlanned} cooked",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSubheaderColor.copy(alpha = 0.7f)
                        )
                    }
                }

                if (uiState.totalPlanned > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    // Progress bar
                    LinearProgressIndicator(
                        progress = { uiState.progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // This Week's Meals List
            if (uiState.totalPlanned > 0) {
                // Recipe list
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.plannedRecipes.forEach { plannedRecipe ->
                            PlannedRecipeItem(
                                plannedRecipe = plannedRecipe,
                                onClick = { onRecipeClick(plannedRecipe.recipe) },
                                onToggleCooked = {
                                    if (plannedRecipe.cooked) {
                                        viewModel.unmarkRecipeCooked(plannedRecipe)
                                    } else {
                                        viewModel.markRecipeCooked(plannedRecipe)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // Empty state
                item {
                    EmptyMealPlanCard(onNavigateToMeals = onNavigateToMeals)
                }
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
