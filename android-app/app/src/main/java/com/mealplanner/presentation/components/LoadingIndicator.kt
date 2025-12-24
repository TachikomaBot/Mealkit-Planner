package com.mealplanner.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mealplanner.domain.model.GenerationPhase
import com.mealplanner.domain.model.GenerationProgress

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    message: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        if (message != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun FullScreenLoading(
    message: String? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(message = message)
    }
}

@Composable
fun GenerationProgressIndicator(
    progress: GenerationProgress,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Phase indicator
            Text(
                text = progress.phase.displayName(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            val progressValue = if (progress.total > 0) {
                progress.current.toFloat() / progress.total
            } else {
                0f
            }

            if (progress.phase == GenerationPhase.CONNECTING || progress.phase == GenerationPhase.PLANNING) {
                // Indeterminate progress for phases without clear progress
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            } else {
                LinearProgressIndicator(
                    progress = { progressValue },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress text
            if (progress.total > 0 && progress.phase != GenerationPhase.CONNECTING) {
                Text(
                    text = "${progress.current} of ${progress.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Custom message
            progress.message?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun GenerationLoadingScreen(
    progress: GenerationProgress?,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated cooking icon
            val infiniteTransition = rememberInfiniteTransition(label = "cooking")
            val rotation by infiniteTransition.animateFloat(
                initialValue = -10f,
                targetValue = 10f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rotation"
            )

            Text(
                text = "\uD83C\uDF73",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.rotate(rotation)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Cooking up your meal plan...",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (progress != null) {
                GenerationProgressIndicator(progress = progress)
            } else {
                CircularProgressIndicator()
            }

            // Cancel button
            if (onCancel != null) {
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}

private fun GenerationPhase.displayName(): String = when (this) {
    GenerationPhase.CONNECTING -> "Connecting..."
    GenerationPhase.PLANNING -> "Planning your week..."
    GenerationPhase.BUILDING -> "Building recipes..."
    GenerationPhase.GENERATING_IMAGES -> "Creating images..."
    GenerationPhase.COMPLETE -> "Complete!"
    GenerationPhase.ERROR -> "Error"
}
