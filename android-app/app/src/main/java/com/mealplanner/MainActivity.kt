package com.mealplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mealplanner.presentation.navigation.MainScreen
import com.mealplanner.presentation.theme.MealPlannerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        // Remove the translucent scrim on three-button navigation
        window.isNavigationBarContrastEnforced = false

        setContent {
            val viewModel = androidx.hilt.navigation.compose.hiltViewModel<com.mealplanner.presentation.MainViewModel>()
            val themeState by viewModel.themeState.collectAsState(initial = null)
            val isSystemDark = isSystemInDarkTheme()

            // Resolve theme: explicit preference > system default
            val darkTheme = themeState ?: isSystemDark

            MealPlannerTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}
