package com.mealplanner.presentation.navigation

import android.util.Base64
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mealplanner.domain.model.CookingStep
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeIngredient
import com.mealplanner.presentation.screens.home.HomeScreen
import com.mealplanner.presentation.screens.mealplan.MealPlanScreen
import com.mealplanner.presentation.screens.profile.ProfileScreen
import com.mealplanner.presentation.screens.recipe.RecipeDetailScreen
import com.mealplanner.presentation.screens.settings.SettingsScreen
import com.mealplanner.presentation.screens.shopping.ShoppingScreen
import com.mealplanner.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

// Navigation tabs
sealed class NavTab(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val color: Color
) {
    data object Home : NavTab(
        route = "tab_home",
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        color = Tomato600
    )
    data object Meals : NavTab(
        route = "tab_meals",
        title = "Meals",
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        color = Pacific600
    )
    data object Profile : NavTab(
        route = "tab_profile",
        title = "Profile",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        color = Sage600
    )
}

// Non-tab screens (full screen overlays)
sealed class AppScreen(val route: String) {
    data object RecipeDetail : AppScreen("recipe/{recipeJson}?selectionIndex={selectionIndex}") {
        fun createRoute(recipe: Recipe, json: Json, selectionIndex: Int? = null): String {
            val jsonString = json.encodeToString(recipe.toNavArg())
            val encoded = Base64.encodeToString(jsonString.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
            return if (selectionIndex != null) {
                "recipe/$encoded?selectionIndex=$selectionIndex"
            } else {
                "recipe/$encoded"
            }
        }
    }
    data object Shopping : AppScreen("shopping")
    data object Settings : AppScreen("settings")
}

val navTabs = listOf(
    NavTab.Home,
    NavTab.Meals,
    NavTab.Profile
)

// ViewModel to observe test mode state
@HiltViewModel
class MainScreenViewModel @Inject constructor(
    dataStore: DataStore<Preferences>
) : ViewModel() {
    companion object {
        private val TEST_MODE_KEY = booleanPreferencesKey("test_mode_enabled")
    }

    val isTestModeEnabled: StateFlow<Boolean> = dataStore.data
        .map { preferences -> preferences[TEST_MODE_KEY] ?: false }
        .stateIn(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val json = Json { ignoreUnknownKeys = true }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isTestModeEnabled by viewModel.isTestModeEnabled.collectAsState()
    val isDark = isSystemInDarkTheme()

    // Track whether MealPlanScreen is in ActivePlan state (it has its own header)
    var isMealPlanActive by remember { mutableStateOf(false) }

    // Reset when navigating away from Meals tab
    LaunchedEffect(currentDestination?.route) {
        if (currentDestination?.route != NavTab.Meals.route) {
            isMealPlanActive = false
        }
    }

    // Show shared header on tab routes, but not when MealPlan has its own
    val isOnTabRoute = currentDestination?.route?.let { route ->
        navTabs.any { it.route == route }
    } ?: true
    val isOnMealsTab = currentDestination?.route == NavTab.Meals.route
    val showSharedTopBar = isOnTabRoute && !(isOnMealsTab && isMealPlanActive)

    // Animated color based on current tab
    val currentTabColor = navTabs.find { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }?.color ?: Tomato600

    val animatedTopBarColor by animateColorAsState(
        targetValue = currentTabColor,
        animationSpec = tween(durationMillis = 300),
        label = "topBarColor"
    )

    val tabRowDarkerColor = when (currentTabColor) {
        Tomato600 -> if (isDark) Tomato500 else Tomato700
        Pacific600 -> if (isDark) Pacific500 else Pacific700
        Sage600 -> if (isDark) Sage500 else Sage700
        else -> currentTabColor
    }
    val animatedTabRowColor by animateColorAsState(
        targetValue = tabRowDarkerColor,
        animationSpec = tween(durationMillis = 300),
        label = "tabRowColor"
    )

    // Week display text
    val weekDisplayText = remember {
        val nextMonday = java.time.LocalDate.now()
            .with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY))
        "Week of ${nextMonday.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))}"
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Test Mode Banner
        if (isTestModeEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "TEST MODE - Data is temporary",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Shared Top App Bar
        if (showSharedTopBar) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Mise",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = weekDisplayText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(AppScreen.Settings.route)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = animatedTopBarColor,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }

        // Top Tab Row
        if (showSharedTopBar) {
            TopTabRow(
                navController = navController,
                containerColor = animatedTabRowColor
            )
        }

        // Main content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .consumeWindowInsets(WindowInsets.navigationBars)
        ) {
            NavHost(
                navController = navController,
                startDestination = NavTab.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                // Tab destinations
                composable(NavTab.Home.route) {
                    HomeScreen(
                        onNavigateToMeals = {
                            navController.navigate(NavTab.Meals.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToShopping = {
                            navController.navigate(AppScreen.Shopping.route)
                        },
                        onRecipeClick = { recipe ->
                            navController.navigate(AppScreen.RecipeDetail.createRoute(recipe, json))
                        }
                    )
                }

                composable(NavTab.Meals.route) {
                    MealPlanScreen(
                        onNavigateToShopping = {
                            navController.navigate(AppScreen.Shopping.route)
                        },
                        onNavigateToSettings = {
                            navController.navigate(AppScreen.Settings.route)
                        },
                        onRecipeClick = { recipe, selectionIndex ->
                            navController.navigate(AppScreen.RecipeDetail.createRoute(recipe, json, selectionIndex))
                        },
                        onActivePlanStateChanged = { isActive ->
                            isMealPlanActive = isActive
                        }
                    )
                }

                composable(NavTab.Profile.route) {
                    ProfileScreen()
                }

                // Full-screen destinations (overlay on tabs)
                composable(AppScreen.Shopping.route) {
                    ShoppingScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToMealPlan = {
                            navController.navigate(NavTab.Meals.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(AppScreen.Settings.route) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = AppScreen.RecipeDetail.route,
                    arguments = listOf(
                        navArgument("selectionIndex") {
                            type = NavType.IntType
                            defaultValue = -1  // -1 means not in selection mode
                        }
                    )
                ) { backStackEntry ->
                    val recipeJson = backStackEntry.arguments?.getString("recipeJson") ?: return@composable
                    val selectionIndex = backStackEntry.arguments?.getInt("selectionIndex") ?: -1
                    val decodedBytes = Base64.decode(recipeJson, Base64.URL_SAFE)
                    val decoded = String(decodedBytes, Charsets.UTF_8)
                    val recipeArg = json.decodeFromString(RecipeNavArg.serializer(), decoded)
                    RecipeDetailScreen(
                        recipe = recipeArg.toRecipe(),
                        selectionIndex = if (selectionIndex >= 0) selectionIndex else null,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopTabRow(
    navController: NavHostController,
    containerColor: Color
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val selectedIndex = navTabs.indexOfFirst { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }.coerceAtLeast(0)

    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = containerColor,
        contentColor = Color.White,
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = Color.White
                )
            }
        }
    ) {
        navTabs.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true

            Tab(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                text = { Text(tab.title) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.title
                    )
                },
                selectedContentColor = Color.White,
                unselectedContentColor = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

// Navigation argument types
@Serializable
data class RecipeNavArg(
    val id: String,
    val name: String,
    val description: String,
    val servings: Int,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val ingredients: List<IngredientNavArg>,
    val steps: List<StepNavArg>,
    val tags: List<String>,
    val imageUrl: String? = null
)

@Serializable
data class IngredientNavArg(
    val name: String,
    val quantity: Double,
    val unit: String,
    val preparation: String? = null
)

@Serializable
data class StepNavArg(
    val title: String,
    val substeps: List<String>
)

private fun Recipe.toNavArg() = RecipeNavArg(
    id = id,
    name = name,
    description = description,
    servings = servings,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    ingredients = ingredients.map { IngredientNavArg(it.name, it.quantity, it.unit, it.preparation) },
    steps = steps.map { StepNavArg(it.title, it.substeps) },
    tags = tags,
    imageUrl = imageUrl
)

private fun RecipeNavArg.toRecipe() = Recipe(
    id = id,
    name = name,
    description = description,
    servings = servings,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    ingredients = ingredients.map { RecipeIngredient(it.name, it.quantity, it.unit, it.preparation) },
    steps = steps.map { CookingStep(it.title, it.substeps) },
    tags = tags,
    imageUrl = imageUrl
)
