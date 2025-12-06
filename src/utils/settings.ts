// User settings utilities

export function getPlanStartDay(): number {
  const stored = localStorage.getItem('plan_start_day');
  return stored ? parseInt(stored, 10) : 1; // Default to Monday
}

export function setPlanStartDay(day: number) {
  localStorage.setItem('plan_start_day', day.toString());
}

// Image cache expiration setting (in days)
export function getImageCacheExpirationDays(): number {
  const stored = localStorage.getItem('image_cache_expiration_days');
  return stored ? parseInt(stored, 10) : 21; // Default to 21 days
}

export function setImageCacheExpirationDays(days: number) {
  localStorage.setItem('image_cache_expiration_days', days.toString());
}

// Calculate the start of the week based on the preferred start day
export function getWeekStart(date: Date, startDay?: number): Date {
  const targetDay = startDay ?? getPlanStartDay();
  const result = new Date(date);
  const currentDay = result.getDay();

  // Calculate days to go back to reach the start day
  let daysBack = currentDay - targetDay;
  if (daysBack < 0) {
    daysBack += 7; // Go back to previous week's start day
  }

  result.setDate(result.getDate() - daysBack);
  result.setHours(0, 0, 0, 0);
  return result;
}

// Get the NEXT occurrence of the start day (for forward-focused planning)
export function getNextWeekStart(date: Date, startDay?: number): Date {
  const targetDay = startDay ?? getPlanStartDay();
  const currentWeekStart = getWeekStart(date, targetDay);
  const today = new Date(date);
  today.setHours(0, 0, 0, 0);

  // If we're on or past the current week start, show next week
  // This makes the plan forward-focused
  if (today >= currentWeekStart) {
    const nextWeek = new Date(currentWeekStart);
    nextWeek.setDate(nextWeek.getDate() + 7);
    return nextWeek;
  }
  return currentWeekStart;
}

// Scheduled generation settings
export interface ScheduleSettings {
  enabled: boolean;
  dayOfWeek: number; // 0-6 (Sunday-Saturday)
  hour: number; // 0-23
  minute: number; // 0-59
}

const DEFAULT_SCHEDULE: ScheduleSettings = {
  enabled: false,
  dayOfWeek: 6, // Saturday
  hour: 1, // 1 AM
  minute: 0,
};

export function getScheduleSettings(): ScheduleSettings {
  const stored = localStorage.getItem('schedule_settings');
  if (!stored) return DEFAULT_SCHEDULE;
  try {
    return { ...DEFAULT_SCHEDULE, ...JSON.parse(stored) };
  } catch {
    return DEFAULT_SCHEDULE;
  }
}

export function setScheduleSettings(settings: ScheduleSettings) {
  localStorage.setItem('schedule_settings', JSON.stringify(settings));
}

// Calculate the next scheduled time based on settings
export function getNextScheduledTime(settings: ScheduleSettings): Date {
  const now = new Date();
  const next = new Date();

  // Set the target time
  next.setHours(settings.hour, settings.minute, 0, 0);

  // Calculate days until target day
  const currentDay = now.getDay();
  let daysUntil = settings.dayOfWeek - currentDay;

  // If we're past the time on the target day, schedule for next week
  if (daysUntil < 0 || (daysUntil === 0 && now >= next)) {
    daysUntil += 7;
  }

  next.setDate(now.getDate() + daysUntil);
  return next;
}

// Format a date as a human-readable schedule description
export function formatScheduleDescription(settings: ScheduleSettings): string {
  const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  const day = days[settings.dayOfWeek];
  const hour = settings.hour % 12 || 12;
  const ampm = settings.hour < 12 ? 'AM' : 'PM';
  const minute = settings.minute.toString().padStart(2, '0');
  return `${day} at ${hour}:${minute} ${ampm}`;
}

// ============================================================================
// Meal Planning Settings
// ============================================================================

// Image generation toggle (disabled for cost savings during testing)
export function isImageGenerationEnabled(): boolean {
  const stored = localStorage.getItem('image_generation_enabled');
  return stored === 'true'; // Default to false (disabled)
}

export function setImageGenerationEnabled(enabled: boolean) {
  localStorage.setItem('image_generation_enabled', enabled.toString());
}

// Target servings for meal planning (default 2)
export function getTargetServings(): number {
  const stored = localStorage.getItem('target_servings');
  return stored ? parseInt(stored, 10) : 2;
}

export function setTargetServings(servings: number) {
  localStorage.setItem('target_servings', Math.max(1, Math.min(8, servings)).toString());
}

// Unit system preference (metric or imperial)
export type UnitSystem = 'metric' | 'imperial';

export function getUnitSystem(): UnitSystem {
  const stored = localStorage.getItem('unit_system');
  return (stored === 'imperial') ? 'imperial' : 'metric'; // Default to metric
}

export function setUnitSystem(system: UnitSystem) {
  localStorage.setItem('unit_system', system);
}

// Get unit system description for prompts
export function getUnitSystemDescription(): string {
  const system = getUnitSystem();
  if (system === 'imperial') {
    return 'Use imperial units: pounds (lb) for proteins/produce weight, cups for liquids, Fahrenheit for temperatures.';
  }
  return 'Use metric units: grams (g) for proteins/produce weight, milliliters (ml) for liquids, Celsius for temperatures.';
}

// Get current season based on date (Northern Hemisphere)
export function getCurrentSeason(): 'spring' | 'summer' | 'fall' | 'winter' {
  const month = new Date().getMonth(); // 0-11
  if (month >= 2 && month <= 4) return 'spring';
  if (month >= 5 && month <= 7) return 'summer';
  if (month >= 8 && month <= 10) return 'fall';
  return 'winter';
}

// Seasonal ingredients for reference
export const SEASONAL_INGREDIENTS: Record<string, string[]> = {
  spring: ['asparagus', 'peas', 'artichoke', 'spinach', 'radish', 'leek', 'rhubarb', 'lamb'],
  summer: ['tomato', 'zucchini', 'corn', 'bell pepper', 'cucumber', 'eggplant', 'berries', 'peach', 'melon'],
  fall: ['squash', 'pumpkin', 'apple', 'pear', 'brussels sprouts', 'sweet potato', 'cranberry', 'mushroom'],
  winter: ['citrus', 'cabbage', 'root vegetables', 'kale', 'parsnip', 'turnip', 'pomegranate', 'chestnuts']
};

// ============================================================================
// Quantity Formatting
// ============================================================================

/**
 * Convert a decimal number to a fraction string
 * e.g., 0.5 → "1/2", 1.333 → "1 1/3", 2.75 → "2 3/4"
 */
export function formatQuantity(num: number | null | undefined): string {
  if (num === null || num === undefined) return '';
  if (num === 0) return '0';

  // Common fractions to recognize (tolerance for floating point)
  const fractions: Array<{ decimal: number; display: string }> = [
    { decimal: 0.125, display: '1/8' },
    { decimal: 0.167, display: '1/6' },
    { decimal: 0.25, display: '1/4' },
    { decimal: 0.333, display: '1/3' },
    { decimal: 0.375, display: '3/8' },
    { decimal: 0.5, display: '1/2' },
    { decimal: 0.625, display: '5/8' },
    { decimal: 0.667, display: '2/3' },
    { decimal: 0.75, display: '3/4' },
    { decimal: 0.833, display: '5/6' },
    { decimal: 0.875, display: '7/8' },
  ];

  const whole = Math.floor(num);
  const fractional = num - whole;

  // If it's a whole number
  if (fractional < 0.01) {
    return whole.toString();
  }

  // Find matching fraction
  let fractionStr = '';
  for (const f of fractions) {
    if (Math.abs(fractional - f.decimal) < 0.02) {
      fractionStr = f.display;
      break;
    }
  }

  // If no match found, round to 1 decimal
  if (!fractionStr) {
    if (whole === 0) {
      return num.toFixed(1).replace(/\.0$/, '');
    }
    return num.toFixed(1).replace(/\.0$/, '');
  }

  // Combine whole and fraction
  if (whole === 0) {
    return fractionStr;
  }
  return `${whole} ${fractionStr}`;
}
