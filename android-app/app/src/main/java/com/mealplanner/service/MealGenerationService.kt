package com.mealplanner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mealplanner.MainActivity
import com.mealplanner.R
import com.mealplanner.domain.model.GeneratedMealPlan
import com.mealplanner.domain.model.GenerationPhase
import com.mealplanner.domain.model.GenerationProgress
import com.mealplanner.domain.repository.GenerationResult
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.PantryRepository
import com.mealplanner.domain.repository.RecipeRepository
import com.mealplanner.domain.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MealGenerationService : Service() {

    @Inject
    lateinit var recipeRepository: RecipeRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var mealPlanRepository: MealPlanRepository

    @Inject
    lateinit var pantryRepository: PantryRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_GENERATION -> {
                startForeground(NOTIFICATION_ID, createInitialNotification())
                startGeneration()
            }
            ACTION_CANCEL_GENERATION -> {
                cancelGeneration()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Meal Plan Generation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while generating your meal plan"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createInitialNotification(): Notification {
        return createProgressNotification(
            GenerationProgress(
                phase = GenerationPhase.CONNECTING,
                current = 0,
                total = 1,
                message = "Starting meal plan generation..."
            )
        )
    }

    private fun createProgressNotification(progress: GenerationProgress): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MealGenerationService::class.java).apply {
                action = ACTION_CANCEL_GENERATION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val phaseText = when (progress.phase) {
            GenerationPhase.CONNECTING -> "Connecting..."
            GenerationPhase.PLANNING -> "Planning recipes..."
            GenerationPhase.BUILDING -> "Building recipes (${progress.current}/${progress.total})"
            GenerationPhase.GENERATING_IMAGES -> "Generating images..."
            GenerationPhase.COMPLETE -> "Complete!"
            GenerationPhase.ERROR -> "Error"
        }

        val progressPercent = if (progress.total > 0) {
            (progress.current * 100) / progress.total
        } else 0

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Generating Meal Plan")
            .setContentText(progress.message ?: phaseText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, progressPercent, progress.phase == GenerationPhase.CONNECTING)
            .setContentIntent(contentIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Cancel",
                cancelIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createCompletionNotification(success: Boolean, message: String?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (success) "Meal Plan Ready!" else "Generation Failed")
            .setContentText(message ?: if (success) "Your new meal plan is ready to view" else "Something went wrong")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun startGeneration() {
        serviceScope.launch {
            _generationState.value = GenerationState.Generating(null)

            // Get user preferences (API key is on backend, not needed here)
            val preferences = userRepository.getPreferences()

            // Get pantry items to consider for meal planning
            val pantryItems = pantryRepository.getAllItems()

            // Get recent recipe hashes to avoid repetition
            val recentHashes = mealPlanRepository.getRecentRecipeHashes(weeksBack = 3)

            // Start generation with SSE streaming
            recipeRepository.generateMealPlan(preferences, pantryItems, recentHashes)
                .collect { result ->
                    when (result) {
                        is GenerationResult.Progress -> {
                            _generationState.value = GenerationState.Generating(result.progress)
                            updateNotification(result.progress)
                        }
                        is GenerationResult.Success -> {
                            _generationState.value = GenerationState.Success(result.mealPlan)
                            showCompletionNotification(true, "Tap to view your new recipes")
                            stopSelf()
                        }
                        is GenerationResult.Error -> {
                            handleError(result.message)
                        }
                    }
                }
        }
    }

    private fun updateNotification(progress: GenerationProgress) {
        val notification = createProgressNotification(progress)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(success: Boolean, message: String?) {
        val notification = createCompletionNotification(success, message)
        notificationManager.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    private fun handleError(message: String) {
        _generationState.value = GenerationState.Error(message)
        showCompletionNotification(false, message)
        stopSelf()
    }

    private fun cancelGeneration() {
        serviceScope.cancel()
        _generationState.value = GenerationState.Idle
        stopSelf()
    }

    companion object {
        const val CHANNEL_ID = "meal_generation_channel"
        const val NOTIFICATION_ID = 1001
        const val COMPLETION_NOTIFICATION_ID = 1002

        const val ACTION_START_GENERATION = "com.mealplanner.action.START_GENERATION"
        const val ACTION_CANCEL_GENERATION = "com.mealplanner.action.CANCEL_GENERATION"

        // Shared state for UI to observe
        private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
        val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

        fun startGeneration(context: Context) {
            val intent = Intent(context, MealGenerationService::class.java).apply {
                action = ACTION_START_GENERATION
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelGeneration(context: Context) {
            val intent = Intent(context, MealGenerationService::class.java).apply {
                action = ACTION_CANCEL_GENERATION
            }
            context.startService(intent)
        }

        fun resetState() {
            _generationState.value = GenerationState.Idle
        }
    }
}

sealed class GenerationState {
    data object Idle : GenerationState()
    data class Generating(val progress: GenerationProgress?) : GenerationState()
    data class Success(val mealPlan: GeneratedMealPlan) : GenerationState()
    data class Error(val message: String) : GenerationState()
}
