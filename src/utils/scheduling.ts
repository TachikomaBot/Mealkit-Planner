// Background scheduling utilities for Android
// Uses Capacitor plugins for native background task scheduling

import type { ScheduleSettings } from './settings';
import { getScheduleSettings, getNextScheduledTime } from './settings';

// Notification IDs
const CHECK_STOCK_NOTIFICATION_ID = 1001;

// Check if we're running in a Capacitor native environment
export async function isSchedulingSupported(): Promise<boolean> {
  // Check if Capacitor is available and we're on a native platform
  if (typeof window === 'undefined') return false;

  try {
    const { Capacitor } = await import('@capacitor/core');
    const platform = Capacitor.getPlatform();
    return platform === 'android' || platform === 'ios';
  } catch {
    return false;
  }
}

// Task identifier for the background job
const TASK_ID = 'recipe-generation-task';
// Track if generation is currently running
let isGenerationRunning = false;
let backgroundListenerRegistered = false;

// Initialize background scheduling listeners
export async function initializeScheduling(): Promise<void> {
  const supported = await isSchedulingSupported();
  if (!supported || backgroundListenerRegistered) return;

  try {
    const { BackgroundTask } = await import('@capawesome/capacitor-background-task');

    // Register a background task that checks schedule or waits for running generation
    await BackgroundTask.beforeExit(async () => {
      console.log('App backgrounding...');

      // 1. If generation is already running (e.g. triggered by checkMissedSchedule), wait for it
      if (isGenerationRunning) {
        console.log('Waiting for active recipe generation to complete...');
        while (isGenerationRunning) {
          await new Promise(resolve => setTimeout(resolve, 1000));
        }
        console.log('Active generation completed.');
      }

      // 2. Check if we should start a NEW generation based on schedule
      const config = localStorage.getItem('scheduled_task_config');
      if (config) {
        const { scheduledFor } = JSON.parse(config);
        const scheduledTime = new Date(scheduledFor);
        const now = new Date();

        // If we're within 1 hour of the scheduled time, start generation
        const diffMs = Math.abs(now.getTime() - scheduledTime.getTime());
        if (diffMs < 60 * 60 * 1000) {
          // This will set isGenerationRunning = true
          await runScheduledGeneration();
        }
      }

      // Signal task completion
      await BackgroundTask.finish({ taskId: TASK_ID });
    });

    backgroundListenerRegistered = true;
    console.log('Background scheduling initialized');
  } catch (error) {
    console.error('Failed to initialize scheduling:', error);
  }
}

// Schedule the recipe generation task
export async function scheduleRecipeGeneration(settings: ScheduleSettings): Promise<boolean> {
  const supported = await isSchedulingSupported();
  if (!supported) return false;

  try {
    // Ensure listener is registered
    await initializeScheduling();

    // Calculate delay until next scheduled time
    const nextRun = getNextScheduledTime(settings);

    // Store the task configuration
    localStorage.setItem('scheduled_task_config', JSON.stringify({
      settings,
      scheduledFor: nextRun.toISOString(),
    }));

    console.log(`Recipe generation scheduled for ${nextRun.toISOString()}`);
    return true;
  } catch (error) {
    console.error('Failed to schedule recipe generation:', error);
    return false;
  }
}

// Cancel scheduled generation
export async function cancelScheduledGeneration(): Promise<void> {
  localStorage.removeItem('scheduled_task_config');
  // The task simply won't run if the config is removed
}

// Run the scheduled generation task
export async function runScheduledGeneration(): Promise<void> {
  if (isGenerationRunning) {
    console.log('Generation already in progress');
    return;
  }

  console.log('Running scheduled recipe generation...');
  isGenerationRunning = true;


  try {
    // Import the generation functions
    const { generateRecipePoolPhased, getApiKey } = await import('../api/claude');
    const { db } = await import('../db');

    // Check if API key is configured
    const apiKey = getApiKey();
    if (!apiKey) {
      console.log('No API key configured, skipping generation');
      return;
    }

    // Get current pantry state
    const pantry = await db.ingredients.toArray();
    if (pantry.length === 0) {
      console.log('Pantry is empty, skipping generation');
      return;
    }

    // Get recent recipes to avoid repetition
    const recentHistory = await db.recipeHistory
      .orderBy('dateCooked')
      .reverse()
      .limit(20)
      .toArray();
    const recentRecipeNames = recentHistory.map(h => h.recipeName);

    // Get user preferences (may not exist)
    const preferences = await db.userPreferences.toCollection().first() ?? null;

    // Generate recipes (no progress callback for background)
    const result = await generateRecipePoolPhased(
      pantry,
      recentRecipeNames,
      preferences,
      // Helper to update progress notification
      async (progress: any) => {
        try {
          const { LocalNotifications } = await import('@capacitor/local-notifications');
          const perm = await LocalNotifications.checkPermissions();
          if (perm.display !== 'granted') {
            const req = await LocalNotifications.requestPermissions();
            if (req.display !== 'granted') return;
          }

          const percent = Math.round(
            progress.phase === 'outlines' ? 10 :
              progress.phase === 'normalizing' ? 95 :
                10 + (progress.current / progress.total) * 85
          );

          await LocalNotifications.schedule({
            notifications: [{
              id: 999, // Reserved ID for progress
              title: 'Generating Weekly Plan',
              body: `Selecting recipes (${percent}%)`,
              schedule: { at: new Date(Date.now() + 100) },
              channelId: 'progress',
              ongoing: true,
              autoCancel: false,
              extra: { type: 'progress' }
            }]
          });
        } catch (e) { }
      },
      new Date()
    );

    // Store the generated recipes for later use
    localStorage.setItem('pre_generated_recipes', JSON.stringify({
      recipes: result.recipes,
      generatedAt: new Date().toISOString(),
    }));

    // Clear progress notification
    try {
      const { LocalNotifications } = await import('@capacitor/local-notifications');
      await LocalNotifications.cancel({ notifications: [{ id: 999 }] });
    } catch (e) { }

    console.log(`Generated ${result.recipes.length} recipes in background`);

    // Notify user that plan is ready
    try {
      const { LocalNotifications } = await import('@capacitor/local-notifications');
      await LocalNotifications.schedule({
        notifications: [{
          id: 1002,
          title: 'Meal Plan Ready 🍽️',
          body: `We've generated ${result.recipes.length} meal options for your week. Tap to choose your favorites!`,
          schedule: { at: new Date(Date.now() + 1000) }, // Now
          extra: { route: '/plan' }
        }]
      });
    } catch (e) {
      console.warn('Failed to send completion notification:', e);
    }

    // Reschedule for next week
    const settings = getScheduleSettings();
    if (settings.enabled) {
      await scheduleRecipeGeneration(settings);
    }
  } catch (error) {
    console.error('Background generation failed:', error);
    // Ensure progress notification is cleared on error
    try {
      const { LocalNotifications } = await import('@capacitor/local-notifications');
      await LocalNotifications.cancel({ notifications: [{ id: 999 }] });
    } catch (e) { }
  } finally {
    isGenerationRunning = false;
  }
}

// Check on app start if we have pre-generated recipes
export function getPreGeneratedRecipes(): { recipes: any[]; generatedAt: string } | null {
  const stored = localStorage.getItem('pre_generated_recipes');
  if (!stored) return null;

  try {
    const data = JSON.parse(stored);
    // Check if the recipes are less than 7 days old
    const generatedAt = new Date(data.generatedAt);
    const now = new Date();
    const ageMs = now.getTime() - generatedAt.getTime();
    const ageDays = ageMs / (1000 * 60 * 60 * 24);

    if (ageDays < 7) {
      return data;
    } else {
      // Recipes are too old, clear them
      localStorage.removeItem('pre_generated_recipes');
      return null;
    }
  } catch {
    return null;
  }
}

// Clear pre-generated recipes (after they've been used)
export function clearPreGeneratedRecipes(): void {
  localStorage.removeItem('pre_generated_recipes');
}

// Check if it's time to generate and we missed the scheduled time
export async function checkMissedSchedule(): Promise<boolean> {
  const config = localStorage.getItem('scheduled_task_config');
  if (!config) return false;

  try {
    const { scheduledFor, settings } = JSON.parse(config);
    const scheduledTime = new Date(scheduledFor);
    const now = new Date();

    // If we're past the scheduled time but within 24 hours, run it
    if (now > scheduledTime) {
      const diffMs = now.getTime() - scheduledTime.getTime();
      const diffHours = diffMs / (1000 * 60 * 60);

      if (diffHours < 24) {
        // We missed the schedule, run now
        await runScheduledGeneration();
        return true;
      } else {
        // Too late, just reschedule for next week
        await scheduleRecipeGeneration(settings);
      }
    }
  } catch (error) {
    console.error('Error checking missed schedule:', error);
  }

  return false;
}

// ============================================================================
// Check Stock Reminder Notifications
// ============================================================================

// Get count of perishable items that need stock verification
export async function getCheckStockCount(): Promise<number> {
  try {
    const { db } = await import('../db');
    const all = await db.ingredients.toArray();

    const now = new Date();
    const threeDaysAgo = new Date(now.getTime() - 3 * 24 * 60 * 60 * 1000);
    const threeDaysFromNow = new Date(now.getTime() + 3 * 24 * 60 * 60 * 1000);

    return all.filter(ingredient => {
      // Only check perishables (protein, dairy, produce)
      if (!['protein', 'dairy', 'produce'].includes(ingredient.category)) {
        return false;
      }
      // Expiring within 3 days
      if (ingredient.expiryDate && ingredient.expiryDate <= threeDaysFromNow) {
        return true;
      }
      // Added more than 3 days ago
      if (ingredient.dateAdded <= threeDaysAgo) {
        return true;
      }
      // Partially consumed
      if (ingredient.quantityRemaining < ingredient.quantityInitial) {
        return true;
      }
      return false;
    }).length;
  } catch (error) {
    console.error('Error getting check stock count:', error);
    return 0;
  }
}

// Schedule "Check Stock" reminder notification for the evening before meal generation
export async function scheduleCheckStockReminder(settings: ScheduleSettings): Promise<boolean> {
  const supported = await isSchedulingSupported();
  if (!supported || !settings.enabled) {
    return false;
  }

  try {
    const { LocalNotifications } = await import('@capacitor/local-notifications');

    // Request permission if needed
    const permResult = await LocalNotifications.requestPermissions();
    if (permResult.display !== 'granted') {
      console.log('Notification permission not granted');
      return false;
    }

    // Cancel any existing check stock notification
    await LocalNotifications.cancel({ notifications: [{ id: CHECK_STOCK_NOTIFICATION_ID }] });

    // Calculate when to show the reminder: 6pm the evening before scheduled generation
    const nextGeneration = getNextScheduledTime(settings);
    const reminderTime = new Date(nextGeneration);
    reminderTime.setDate(reminderTime.getDate() - 1); // Day before
    reminderTime.setHours(18, 0, 0, 0); // 6 PM

    // If reminder time is in the past, don't schedule
    if (reminderTime <= new Date()) {
      console.log('Check stock reminder time already passed');
      return false;
    }

    // Get count of items needing check
    const checkCount = await getCheckStockCount();

    // Schedule the notification
    await LocalNotifications.schedule({
      notifications: [
        {
          id: CHECK_STOCK_NOTIFICATION_ID,
          title: 'Time to Check Your Pantry',
          body: checkCount > 0
            ? `${checkCount} perishable item${checkCount > 1 ? 's' : ''} may need verification before tomorrow's meal plan.`
            : 'Review your perishable items before tomorrow\'s meal plan generation.',
          schedule: { at: reminderTime },
          extra: {
            route: '/pantry',
            filter: 'check-stock',
          },
        },
      ],
    });

    console.log(`Check stock reminder scheduled for ${reminderTime.toISOString()}`);
    return true;
  } catch (error) {
    console.error('Failed to schedule check stock reminder:', error);
    return false;
  }
}

// Handle notification action (when user taps on notification)
export async function setupNotificationListeners(): Promise<void> {
  const supported = await isSchedulingSupported();
  if (!supported) return;

  try {
    const { LocalNotifications } = await import('@capacitor/local-notifications');

    LocalNotifications.addListener('localNotificationActionPerformed', (notification) => {
      const extra = notification.notification.extra;
      if (extra?.route) {
        // Navigate to the specified route with state
        // This will be handled by the app's router
        window.location.href = extra.route;
        if (extra.filter) {
          // Store the filter to be picked up by the Pantry page
          localStorage.setItem('pantry_initial_filter', extra.filter);
        }
      }
    });

    console.log('Notification listeners set up');
  } catch (error) {
    console.error('Failed to set up notification listeners:', error);
  }
}
