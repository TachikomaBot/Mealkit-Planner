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
import com.mealplanner.domain.model.ShoppingList
import com.mealplanner.domain.repository.ShoppingRepository
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
class GroceryPolishService : Service() {

    @Inject
    lateinit var shoppingRepository: ShoppingRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_POLISH -> {
                val mealPlanId = intent.getLongExtra(EXTRA_MEAL_PLAN_ID, -1)
                if (mealPlanId != -1L) {
                    startForeground(NOTIFICATION_ID, createProgressNotification("Preparing..."))
                    startPolish(mealPlanId)
                } else {
                    stopSelf()
                }
            }
            ACTION_CANCEL_POLISH -> {
                cancelPolish()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Progress channel (low importance - silent)
            val progressChannel = NotificationChannel(
                CHANNEL_ID_PROGRESS,
                "Grocery List Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while optimizing your grocery list"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(progressChannel)

            // Completion channel (default importance - shows notification)
            val completionChannel = NotificationChannel(
                CHANNEL_ID_COMPLETION,
                "Grocery List Ready",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when your grocery list is ready"
            }
            notificationManager.createNotificationChannel(completionChannel)
        }
    }

    private fun createProgressNotification(message: String): Notification {
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
            Intent(this, GroceryPolishService::class.java).apply {
                action = ACTION_CANCEL_POLISH
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_PROGRESS)
            .setContentTitle("Optimizing Grocery List")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(0, 0, true) // Indeterminate progress
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

    private fun createCompletionNotification(success: Boolean, itemCount: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (success) "Grocery List Ready!" else "Optimization Failed"
        val text = if (success) {
            "$itemCount items organized and ready for shopping"
        } else {
            "Your unoptimized list is still available"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID_COMPLETION)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun startPolish(mealPlanId: Long) {
        _polishState.value = PolishState.Polishing(mealPlanId)

        serviceScope.launch {
            try {
                // First generate the raw shopping list
                updateNotification("Generating shopping list...")
                val generateResult = shoppingRepository.generateShoppingList(mealPlanId)

                if (generateResult.isFailure) {
                    handleError("Failed to generate shopping list")
                    return@launch
                }

                val rawList = generateResult.getOrThrow()
                android.util.Log.d("GroceryPolishService", "Generated ${rawList.items.size} raw items")

                // Now polish it
                updateNotification("Optimizing quantities and categories...")
                val polishResult = shoppingRepository.polishShoppingList(mealPlanId)

                polishResult.onSuccess { polishedList ->
                    android.util.Log.d("GroceryPolishService", "Polish complete: ${polishedList.items.size} items")
                    _polishState.value = PolishState.Success(polishedList)
                    showCompletionNotification(true, polishedList.items.size)
                    stopSelf()
                }.onFailure { error ->
                    android.util.Log.e("GroceryPolishService", "Polish failed: ${error.message}")
                    // Polish failed but raw list is available
                    _polishState.value = PolishState.Success(rawList)
                    showCompletionNotification(false, rawList.items.size)
                    stopSelf()
                }
            } catch (e: Exception) {
                android.util.Log.e("GroceryPolishService", "Error: ${e.message}", e)
                handleError(e.message ?: "Unknown error")
            }
        }
    }

    private fun updateNotification(message: String) {
        val notification = createProgressNotification(message)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(success: Boolean, itemCount: Int) {
        val notification = createCompletionNotification(success, itemCount)
        notificationManager.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    private fun handleError(message: String) {
        _polishState.value = PolishState.Error(message)
        showCompletionNotification(false, 0)
        stopSelf()
    }

    private fun cancelPolish() {
        serviceScope.cancel()
        _polishState.value = PolishState.Idle
        stopSelf()
    }

    companion object {
        const val CHANNEL_ID_PROGRESS = "grocery_polish_progress_channel"
        const val CHANNEL_ID_COMPLETION = "grocery_polish_completion_channel"
        const val NOTIFICATION_ID = 2001
        const val COMPLETION_NOTIFICATION_ID = 2002

        const val ACTION_START_POLISH = "com.mealplanner.action.START_POLISH"
        const val ACTION_CANCEL_POLISH = "com.mealplanner.action.CANCEL_POLISH"
        const val EXTRA_MEAL_PLAN_ID = "meal_plan_id"

        // Shared state for UI to observe
        private val _polishState = MutableStateFlow<PolishState>(PolishState.Idle)
        val polishState: StateFlow<PolishState> = _polishState.asStateFlow()

        fun startPolish(context: Context, mealPlanId: Long) {
            val intent = Intent(context, GroceryPolishService::class.java).apply {
                action = ACTION_START_POLISH
                putExtra(EXTRA_MEAL_PLAN_ID, mealPlanId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelPolish(context: Context) {
            val intent = Intent(context, GroceryPolishService::class.java).apply {
                action = ACTION_CANCEL_POLISH
            }
            context.startService(intent)
        }

        fun resetState() {
            _polishState.value = PolishState.Idle
        }
    }
}

sealed class PolishState {
    data object Idle : PolishState()
    data class Polishing(val mealPlanId: Long) : PolishState()
    data class Success(val shoppingList: ShoppingList) : PolishState()
    data class Error(val message: String) : PolishState()
}
