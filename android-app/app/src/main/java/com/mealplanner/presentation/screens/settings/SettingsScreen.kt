package com.mealplanner.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val sampleDataState by viewModel.sampleDataState.collectAsState()
    val pantryStaplesState by viewModel.pantryStaplesState.collectAsState()

    // Show snackbar for sample data results
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sampleDataState) {
        when (val state = sampleDataState) {
            is SampleDataState.Success -> {
                snackbarHostState.showSnackbar(
                    message = "Loaded ${state.result.recipesAdded} recipes and ${state.result.pantryItemsAdded} pantry items",
                    duration = SnackbarDuration.Short
                )
                viewModel.dismissSampleDataState()
            }
            is SampleDataState.Error -> {
                snackbarHostState.showSnackbar(
                    message = "Error: ${state.message}",
                    duration = SnackbarDuration.Short
                )
                viewModel.dismissSampleDataState()
            }
            else -> {}
        }
    }

    LaunchedEffect(pantryStaplesState) {
        when (val state = pantryStaplesState) {
            is PantryStaplesState.Success -> {
                snackbarHostState.showSnackbar(
                    message = "Loaded ${state.result.itemsAdded} pantry staples",
                    duration = SnackbarDuration.Short
                )
                viewModel.dismissPantryStaplesState()
            }
            is PantryStaplesState.Error -> {
                snackbarHostState.showSnackbar(
                    message = "Error: ${state.message}",
                    duration = SnackbarDuration.Short
                )
                viewModel.dismissPantryStaplesState()
            }
            else -> {}
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Developer Tools Section
            item {
                DeveloperToolsSection(
                    isSampleDataLoading = sampleDataState is SampleDataState.Loading,
                    isPantryStaplesLoading = pantryStaplesState is PantryStaplesState.Loading,
                    onLoadSampleData = { viewModel.loadSampleData() },
                    onLoadPantryStaples = { viewModel.loadPantryStaples() }
                )
            }

            // About section
            item {
                AboutSection()
            }
        }
    }
}

@Composable
private fun DeveloperToolsSection(
    isSampleDataLoading: Boolean,
    isPantryStaplesLoading: Boolean,
    onLoadSampleData: () -> Unit,
    onLoadPantryStaples: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Developer Tools",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Test the app with sample data for quick exploration",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onLoadSampleData,
                enabled = !isSampleDataLoading && !isPantryStaplesLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                if (isSampleDataLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onTertiary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading...")
                } else {
                    Icon(
                        imageVector = Icons.Default.DataArray,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Load Sample Data")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Creates sample meal plan, pantry items, and shopping list",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onLoadPantryStaples,
                enabled = !isSampleDataLoading && !isPantryStaplesLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                if (isPantryStaplesLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading...")
                } else {
                    Icon(
                        imageVector = Icons.Default.Kitchen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Load Pantry Staples")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Adds common pantry staples (oils, spices, flour, rice, potatoes, onions, etc.)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
