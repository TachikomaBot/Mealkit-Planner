package com.mealplanner.presentation.navigation

import android.util.Base64
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mealplanner.domain.model.CookingStep
import com.mealplanner.domain.model.Recipe
import com.mealplanner.domain.model.RecipeIngredient
import com.mealplanner.presentation.screens.home.HomeScreen
import com.mealplanner.presentation.screens.mealplan.MealPlanScreen
import com.mealplanner.presentation.screens.pantry.PantryScreen
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

// Bottom navigation tabs
sealed class BottomNavTab(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val color: Color
) {
    data object Home : BottomNavTab(
        route = "tab_home",
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        color = Tomato600
    )
    data object Pantry : BottomNavTab(
        route = "tab_pantry",
        title = "Pantry",
        selectedIcon = Icons.Filled.Kitchen,
        unselectedIcon = Icons.Outlined.Kitchen,
        color = Mustard600
    )
    data object Meals : BottomNavTab(
        route = "tab_meals",
        title = "Meals",
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        color = Pacific600
    )
    data object Profile : BottomNavTab(
        route = "tab_profile",
        title = "Profile",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        color = Sage600
    )
}

// Non-tab screens (full screen overlays)
sealed class AppScreen(val route: String) {
    data object RecipeDetail : AppScreen("recipe/{recipeJson}") {
        fun createRoute(recipe: Recipe, json: Json): String {
            val jsonString = json.encodeToString(recipe.toNavArg())
            val encoded = Base64.encodeToString(jsonString.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
            return "recipe/$encoded"
        }
    }
    data object Shopping : AppScreen("shopping")
    data object Settings : AppScreen("settings")
}

val bottomNavTabs = listOf(
    BottomNavTab.Home,
    BottomNavTab.Pantry,
    BottomNavTab.Meals,
    BottomNavTab.Profile
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

@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val json = Json { ignoreUnknownKeys = true }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isTestModeEnabled by viewModel.isTestModeEnabled.collectAsState()

    // Alternative layout: simpler Column instead of Scaffold
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

        // Main content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .consumeWindowInsets(WindowInsets.navigationBars)
        ) {
            NavHost(
                navController = navController,
                startDestination = BottomNavTab.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                // Tab destinations
                composable(BottomNavTab.Home.route) {
                    HomeScreen(
                        onNavigateToMeals = {
                            navController.navigate(BottomNavTab.Meals.route) {
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
                        onNavigateToSettings = {
                            navController.navigate(AppScreen.Settings.route)
                        },
                        onRecipeClick = { recipe ->
                            navController.navigate(AppScreen.RecipeDetail.createRoute(recipe, json))
                        }
                    )
                }

                composable(BottomNavTab.Pantry.route) {
                    PantryScreen()
                }

                composable(BottomNavTab.Meals.route) {
                    MealPlanScreen(
                        onNavigateToShopping = {
                            navController.navigate(AppScreen.Shopping.route)
                        },
                        onNavigateToSettings = {
                            navController.navigate(AppScreen.Settings.route)
                        },
                        onRecipeClick = { recipe ->
                            navController.navigate(AppScreen.RecipeDetail.createRoute(recipe, json))
                        }
                    )
                }

                composable(BottomNavTab.Profile.route) {
                    ProfileScreen()
                }

                // Full-screen destinations (overlay on tabs)
                composable(AppScreen.Shopping.route) {
                    ShoppingScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToMealPlan = {
                            navController.navigate(BottomNavTab.Meals.route) {
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

                composable(AppScreen.RecipeDetail.route) { backStackEntry ->
                    val recipeJson = backStackEntry.arguments?.getString("recipeJson") ?: return@composable
                    val decodedBytes = Base64.decode(recipeJson, Base64.URL_SAFE)
                    val decoded = String(decodedBytes, Charsets.UTF_8)
                    val recipeArg = json.decodeFromString(RecipeNavArg.serializer(), decoded)
                    RecipeDetailScreen(
                        recipe = recipeArg.toRecipe(),
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        // Bottom Navigation Bar
        BottomNavigationBar(navController = navController)
    }
}

@Composable
private fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Hide bottom bar on full-screen destinations
    val showBottomBar = currentDestination?.route?.let { route ->
        bottomNavTabs.any { it.route == route }
    } ?: true

    if (showBottomBar) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp
        ) {
            bottomNavTabs.forEach { tab ->
                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true

                NavigationBarItem(
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
                    icon = {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.title
                        )
                    },
                    label = { Text(tab.title) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = tab.color,
                        selectedTextColor = tab.color,
                        indicatorColor = tab.color.copy(alpha = 0.1f) // Subtle indicator
                    )
                )
            }
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
