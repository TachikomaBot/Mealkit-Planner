package com.mealplanner.presentation.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mealplanner.domain.model.MealPlan
import com.mealplanner.domain.model.RecipeHistory
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferences.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val mealPlanHistory by viewModel.mealPlanHistory.collectAsState()
    val recipeHistory by viewModel.recipeHistory.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                ProfileTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = when (tab) {
                                    ProfileTab.PREFERENCES -> "Preferences"
                                    ProfileTab.HISTORY -> "History"
                                    ProfileTab.STATS -> "Stats"
                                }
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    ProfileTab.PREFERENCES -> Icons.Default.Settings
                                    ProfileTab.HISTORY -> Icons.Default.History
                                    ProfileTab.STATS -> Icons.AutoMirrored.Filled.TrendingUp
                                },
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            // Tab Content
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    ProfileTab.PREFERENCES -> PreferencesTabContent(
                        servings = preferences.targetServings,
                        likes = preferences.likes,
                        dislikes = preferences.dislikes,
                        onServingsChange = { viewModel.updateTargetServings(it) },
                        onAddLike = { viewModel.addLike(it) },
                        onRemoveLike = { viewModel.removeLike(it) },
                        onAddDislike = { viewModel.addDislike(it) },
                        onRemoveDislike = { viewModel.removeDislike(it) }
                    )
                    ProfileTab.HISTORY -> HistoryTabContent(
                        mealPlanHistory = mealPlanHistory,
                        recipeHistory = recipeHistory
                    )
                    ProfileTab.STATS -> StatsTabContent(stats = stats)
                }

                // Save status snackbar
                if (saveStatus == SaveStatus.Saved) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saved")
                        }
                    }
                }
            }
        }
    }
}

// ==================== PREFERENCES TAB ====================

@Composable
private fun PreferencesTabContent(
    servings: Int,
    likes: List<String>,
    dislikes: List<String>,
    onServingsChange: (Int) -> Unit,
    onAddLike: (String) -> Unit,
    onRemoveLike: (String) -> Unit,
    onAddDislike: (String) -> Unit,
    onRemoveDislike: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ServingsSection(
                servings = servings,
                onServingsChange = onServingsChange
            )
        }

        item {
            PreferenceChipsSection(
                title = "Ingredients I Like",
                icon = Icons.Default.ThumbUp,
                items = likes,
                onAddItem = onAddLike,
                onRemoveItem = onRemoveLike,
                chipColor = MaterialTheme.colorScheme.primaryContainer
            )
        }

        item {
            PreferenceChipsSection(
                title = "Ingredients I Dislike",
                icon = Icons.Default.ThumbDown,
                items = dislikes,
                onAddItem = onAddDislike,
                onRemoveItem = onRemoveDislike,
                chipColor = MaterialTheme.colorScheme.errorContainer
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            AboutSection()
        }
    }
}

@Composable
private fun ServingsSection(
    servings: Int,
    onServingsChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Target Servings",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "How many people are you cooking for?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (servings > 1) onServingsChange(servings - 1) },
                    enabled = servings > 1
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }

                Text(
                    text = servings.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                IconButton(
                    onClick = { if (servings < 12) onServingsChange(servings + 1) },
                    enabled = servings < 12
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }
        }
    }
}

@Composable
private fun PreferenceChipsSection(
    title: String,
    icon: ImageVector,
    items: List<String>,
    onAddItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    chipColor: androidx.compose.ui.graphics.Color
) {
    var newItem by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
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
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = newItem,
                onValueChange = { newItem = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add ingredient...") },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (newItem.isNotBlank()) {
                                onAddItem(newItem)
                                newItem = ""
                                focusManager.clearFocus()
                            }
                        },
                        enabled = newItem.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (newItem.isNotBlank()) {
                            onAddItem(newItem)
                            newItem = ""
                        }
                        focusManager.clearFocus()
                    }
                )
            )

            if (items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items) { item ->
                        InputChip(
                            selected = false,
                            onClick = { onRemoveItem(item) },
                            label = { Text(item) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = chipColor
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Mise",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your personal meal planning assistant",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== HISTORY TAB ====================

@Composable
private fun HistoryTabContent(
    mealPlanHistory: List<MealPlan>,
    recipeHistory: List<RecipeHistory>
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    if (mealPlanHistory.isEmpty() && recipeHistory.isEmpty()) {
        EmptyHistoryState()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Meal Plans Section
            if (mealPlanHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "Meal Plans",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(mealPlanHistory) { mealPlan ->
                    MealPlanHistoryCard(
                        mealPlan = mealPlan,
                        dateFormatter = dateFormatter
                    )
                }
            }

            // Recipe History Section
            if (recipeHistory.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Recipes Cooked",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(recipeHistory) { history ->
                    RecipeHistoryCard(history = history)
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No History Yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Your meal plans and cooked recipes will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun MealPlanHistoryCard(
    mealPlan: MealPlan,
    dateFormatter: DateTimeFormatter
) {
    val createdDate = remember(mealPlan.createdAt) {
        java.time.Instant.ofEpochMilli(mealPlan.createdAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Created ${createdDate.format(dateFormatter)}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${mealPlan.recipes.size} recipes planned",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecipeHistoryCard(history: RecipeHistory) {
    val dateTimeFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = history.recipeName,
                    style = MaterialTheme.typography.titleSmall
                )

                val cookedDate = java.time.Instant.ofEpochMilli(history.cookedAt)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime()
                Text(
                    text = "Cooked ${cookedDate.format(dateTimeFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Rating if available
                history.rating?.let { rating ->
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(5) { index ->
                            Icon(
                                imageVector = if (index < rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (index < rating) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                }
                            )
                        }

                        history.wouldMakeAgain?.let { wouldMake ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (wouldMake) Icons.Default.Repeat else Icons.Default.Close,
                                contentDescription = if (wouldMake) "Would make again" else "Wouldn't make again",
                                modifier = Modifier.size(16.dp),
                                tint = if (wouldMake) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== STATS TAB ====================

@Composable
private fun StatsTabContent(stats: ProfileStats) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Stats Overview
        item {
            StatsOverviewSection(stats = stats)
        }

        // Milestones
        item {
            MilestonesSection(stats = stats)
        }

        // Achievements (future expansion)
        item {
            AchievementsSection(stats = stats)
        }
    }
}

@Composable
private fun StatsOverviewSection(stats: ProfileStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.CalendarMonth,
                    value = stats.totalMealPlans.toString(),
                    label = "Meal Plans",
                    color = MaterialTheme.colorScheme.primary
                )
                StatItem(
                    icon = Icons.Default.Restaurant,
                    value = stats.totalRecipesCooked.toString(),
                    label = "Recipes Cooked",
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.Star,
                    value = stats.averageRating?.let { "%.1f".format(it) } ?: "-",
                    label = "Avg Rating",
                    color = MaterialTheme.colorScheme.tertiary
                )
                StatItem(
                    icon = Icons.Default.Kitchen,
                    value = stats.pantryItems.toString(),
                    label = "Pantry Items",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MilestonesSection(stats: ProfileStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Milestones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            MilestoneItem(
                title = "First Meal Plan",
                description = "Create your first meal plan",
                achieved = stats.totalMealPlans >= 1,
                icon = Icons.Default.EmojiEvents
            )

            MilestoneItem(
                title = "Home Cook",
                description = "Cook 10 recipes",
                achieved = stats.totalRecipesCooked >= 10,
                progress = (stats.totalRecipesCooked.coerceAtMost(10)) / 10f,
                icon = Icons.Default.LocalFireDepartment
            )

            MilestoneItem(
                title = "Meal Prep Pro",
                description = "Create 4 meal plans",
                achieved = stats.totalMealPlans >= 4,
                progress = (stats.totalMealPlans.coerceAtMost(4)) / 4f,
                icon = Icons.Default.WorkspacePremium
            )

            MilestoneItem(
                title = "Stocked Pantry",
                description = "Have 20 items in your pantry",
                achieved = stats.pantryItems >= 20,
                progress = (stats.pantryItems.coerceAtMost(20)) / 20f,
                icon = Icons.Default.Inventory
            )
        }
    }
}

@Composable
private fun MilestoneItem(
    title: String,
    description: String,
    achieved: Boolean,
    progress: Float = if (achieved) 1f else 0f,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (achieved) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (achieved) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (achieved) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!achieved && progress > 0f) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }

        if (achieved) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Achieved",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun AchievementsSection(stats: ProfileStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Keep Cooking!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "More achievements unlock as you use Mise",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
