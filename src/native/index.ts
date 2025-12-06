/**
 * Native platform utilities using Capacitor
 * These functions work in both web and native contexts
 */

import { Capacitor } from '@capacitor/core';
import { KeepAwake } from '@capacitor-community/keep-awake';
import { LocalNotifications } from '@capacitor/local-notifications';
import { App } from '@capacitor/app';
import { StatusBar, Style } from '@capacitor/status-bar';

// Check if running as native app
export const isNative = Capacitor.isNativePlatform();
export const platform = Capacitor.getPlatform(); // 'web', 'ios', or 'android'

/**
 * Keep the screen awake during long operations (like recipe generation)
 */
export async function keepScreenAwake(): Promise<void> {
  if (!isNative) return;
  try {
    await KeepAwake.keepAwake();
  } catch (e) {
    console.warn('Failed to keep screen awake:', e);
  }
}

export async function allowScreenSleep(): Promise<void> {
  if (!isNative) return;
  try {
    await KeepAwake.allowSleep();
  } catch (e) {
    console.warn('Failed to allow screen sleep:', e);
  }
}

// Request notification permissions
export async function requestNotificationPermissions(): Promise<boolean> {
  if (!isNative) return false;

  try {
    const permStatus = await LocalNotifications.checkPermissions();
    if (permStatus.display === 'granted') {
      return true;
    }

    const request = await LocalNotifications.requestPermissions();
    return request.display === 'granted';
  } catch (e) {
    console.warn('Failed to request notification permissions:', e);
    return false;
  }
}

/**
 * Schedule a local notification
 */
export async function scheduleNotification(
  title: string,
  body: string,
  scheduledAt: Date,
  id: number = 1
): Promise<void> {
  if (!isNative) return;

  try {
    // Request permission first
    const hasPermission = await requestNotificationPermissions();
    if (!hasPermission) {
      console.warn('Notification permission denied');
      return;
    }

    await LocalNotifications.schedule({
      notifications: [
        {
          id,
          title,
          body,
          schedule: { at: scheduledAt },
          sound: 'default',
          smallIcon: 'ic_stat_icon',
        },
      ],
    });
  } catch (e) {
    console.warn('Failed to schedule notification:', e);
  }
}

/**
 * Schedule weekly meal planning reminder (Friday evening)
 */
export async function scheduleMealPlanReminder(): Promise<void> {
  const now = new Date();
  const friday = new Date(now);

  // Find next Friday at 6pm
  const daysUntilFriday = (5 - now.getDay() + 7) % 7 || 7;
  friday.setDate(now.getDate() + daysUntilFriday);
  friday.setHours(18, 0, 0, 0);

  // If it's already past 6pm Friday, schedule for next week
  if (friday <= now) {
    friday.setDate(friday.getDate() + 7);
  }

  await scheduleNotification(
    'Time to plan your meals!',
    'Open Meal Planner to generate recipes for next week.',
    friday,
    100 // Fixed ID for weekly reminder
  );
}

/**
 * Cancel a scheduled notification
 */
export async function cancelNotification(id: number): Promise<void> {
  if (!isNative) return;
  try {
    await LocalNotifications.cancel({ notifications: [{ id }] });
  } catch (e) {
    console.warn('Failed to cancel notification:', e);
  }
}

/**
 * Set status bar style
 */
export async function setStatusBarStyle(dark: boolean = false): Promise<void> {
  if (!isNative) return;
  try {
    // Don't overlay content - push content below status bar
    await StatusBar.setOverlaysWebView({ overlay: false });
    await StatusBar.setStyle({ style: dark ? Style.Dark : Style.Light });
    if (platform === 'android') {
      await StatusBar.setBackgroundColor({ color: '#4f46e5' });
    }
  } catch (e) {
    console.warn('Failed to set status bar style:', e);
  }
}

// Create notification channel for progress updates
export async function createProgressChannel(): Promise<void> {
  if (!isNative) return;

  try {
    await LocalNotifications.createChannel({
      id: 'progress',
      name: 'Generation Progress',
      importance: 2, // LOW (no sound/vibration, but visible)
      visibility: 1, // PUBLIC
      vibration: false,
      sound: undefined,
    });
  } catch (e) {
    console.warn('Failed to create progress channel:', e);
  }
}

/**
 * Listen for app state changes (foreground/background)
 */
export function onAppStateChange(callback: (isActive: boolean) => void): () => void {
  if (!isNative) return () => { };

  const listener = App.addListener('appStateChange', ({ isActive }) => {
    callback(isActive);
  });

  return () => {
    listener.then(l => l.remove());
  };
}

/**
 * Initialize native features on app start
 */
export async function initializeNative(): Promise<void> {
  if (!isNative) return;

  try {
    await setStatusBarStyle(false);

    // Check/request permissions on startup
    await requestNotificationPermissions();

    // Create channels
    await createProgressChannel();

    // Schedule weekly reminder
    await scheduleMealPlanReminder();
  } catch (e) {
    console.warn('Failed to initialize native features:', e);
  }
}
