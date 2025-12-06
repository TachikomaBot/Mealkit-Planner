import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { getGeminiKey, setGeminiKey } from '../api/gemini';
import {
  getPlanStartDay,
  setPlanStartDay,
  getImageCacheExpirationDays,
  setImageCacheExpirationDays,
  getScheduleSettings,
  setScheduleSettings,
  formatScheduleDescription,
  getNextScheduledTime,
  getTargetServings,
  setTargetServings,
  getUnitSystem,
  setUnitSystem,
  type ScheduleSettings,
  type UnitSystem,
} from '../utils/settings';
import { cleanupExpiredImages } from '../db';
import { scheduleRecipeGeneration, cancelScheduledGeneration, isSchedulingSupported, scheduleCheckStockReminder } from '../utils/scheduling';

// Re-export for backwards compatibility
export { getPlanStartDay, setPlanStartDay } from '../utils/settings';

// Cache expiration options
const CACHE_EXPIRATION_OPTIONS = [
  { value: 7, label: '1 week' },
  { value: 14, label: '2 weeks' },
  { value: 21, label: '3 weeks' },
  { value: 30, label: '1 month' },
  { value: 60, label: '2 months' },
];

// Days of the week for meal plan start
const DAYS = [
  { value: 0, label: 'Sunday' },
  { value: 1, label: 'Monday' },
  { value: 2, label: 'Tuesday' },
  { value: 3, label: 'Wednesday' },
  { value: 4, label: 'Thursday' },
  { value: 5, label: 'Friday' },
  { value: 6, label: 'Saturday' },
];

// Hours for scheduling (12-hour format display)
const HOURS = Array.from({ length: 24 }, (_, i) => ({
  value: i,
  label: `${i % 12 || 12}:00 ${i < 12 ? 'AM' : 'PM'}`,
}));

export default function Settings() {
  const navigate = useNavigate();
  const [geminiKey, setGeminiKeyState] = useState('');
  const [startDay, setStartDay] = useState(getPlanStartDay());
  const [servings, setServings] = useState(getTargetServings());
  const [unitSystem, setUnitSystemState] = useState<UnitSystem>(getUnitSystem());
  const [cacheExpiration, setCacheExpiration] = useState(getImageCacheExpirationDays());
  const [schedule, setSchedule] = useState<ScheduleSettings>(getScheduleSettings());
  const [schedulingSupported, setSchedulingSupported] = useState(false);

  // Track if initial load is complete to avoid saving on mount
  const isInitialized = useRef(false);

  // Load existing Gemini key and check scheduling support on mount
  useEffect(() => {
    const existingGeminiKey = getGeminiKey();
    if (existingGeminiKey) setGeminiKeyState(existingGeminiKey);

    // Check if native scheduling is available
    isSchedulingSupported().then(setSchedulingSupported);

    // Mark as initialized after a tick to allow state to settle
    setTimeout(() => { isInitialized.current = true; }, 0);
  }, []);

  // Auto-save Gemini key (debounced)
  useEffect(() => {
    if (!isInitialized.current) return;
    const timer = setTimeout(() => {
      setGeminiKey(geminiKey.trim());
    }, 500);
    return () => clearTimeout(timer);
  }, [geminiKey]);

  // Auto-save start day
  useEffect(() => {
    if (!isInitialized.current) return;
    setPlanStartDay(startDay);
  }, [startDay]);

  // Auto-save servings
  useEffect(() => {
    if (!isInitialized.current) return;
    setTargetServings(servings);
  }, [servings]);

  // Auto-save unit system
  useEffect(() => {
    if (!isInitialized.current) return;
    setUnitSystem(unitSystem);
  }, [unitSystem]);

  // Auto-save cache expiration and run cleanup
  useEffect(() => {
    if (!isInitialized.current) return;
    setImageCacheExpirationDays(cacheExpiration);
    cleanupExpiredImages(cacheExpiration);
  }, [cacheExpiration]);

  // Auto-save schedule settings and update native scheduling
  useEffect(() => {
    if (!isInitialized.current) return;
    setScheduleSettings(schedule);

    // Update scheduled task if scheduling is supported
    if (schedulingSupported) {
      if (schedule.enabled) {
        scheduleRecipeGeneration(schedule);
        scheduleCheckStockReminder(schedule);
      } else {
        cancelScheduledGeneration();
      }
    }
  }, [schedule, schedulingSupported]);

  const updateSchedule = (updates: Partial<ScheduleSettings>) => {
    setSchedule(prev => ({ ...prev, ...updates }));
  };

  const handleClearData = async () => {
    if (confirm('This will clear all your data including pantry items, recipes, and meal plans. Are you sure?')) {
      // Clear IndexedDB
      const databases = await indexedDB.databases();
      for (const db of databases) {
        if (db.name) {
          indexedDB.deleteDatabase(db.name);
        }
      }
      // Clear localStorage except Gemini API key
      const geminiKeyBackup = localStorage.getItem('gemini_api_key');
      localStorage.clear();
      if (geminiKeyBackup) localStorage.setItem('gemini_api_key', geminiKeyBackup);

      // Reload the app
      window.location.reload();
    }
  };

  return (
    <div className="p-4 max-w-md mx-auto">
      <header className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate(-1)}
          className="text-gray-600 hover:text-gray-900"
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
      </header>

      <div className="space-y-6">
        {/* API Keys Section */}
        <section className="card">
          <h2 className="font-semibold text-gray-900 mb-4">API Key</h2>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Gemini API Key
            </label>
            <input
              type="password"
              value={geminiKey}
              onChange={(e) => setGeminiKeyState(e.target.value)}
              className="input"
              placeholder="AI..."
            />
            <p className="text-xs text-gray-500 mt-1">
              Powers recipe images and shopping list consolidation. Get one free at{' '}
              <a href="https://aistudio.google.com/apikey" className="text-primary-600 underline">
                aistudio.google.com
              </a>
            </p>
          </div>
        </section>

        {/* Meal Planning Section */}
        <section className="card">
          <h2 className="font-semibold text-gray-900 mb-4">Meal Planning</h2>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Week Starts On
              </label>
              <select
                value={startDay}
                onChange={(e) => setStartDay(parseInt(e.target.value, 10))}
                className="input"
              >
                {DAYS.map(({ value, label }) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
              <p className="text-xs text-gray-500 mt-1">
                Your meal plan will be generated for the week starting on this day.
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Servings Per Meal
              </label>
              <div className="flex items-center gap-3">
                <button
                  onClick={() => setServings(Math.max(1, servings - 1))}
                  className="w-10 h-10 rounded-full border border-gray-300 flex items-center justify-center text-xl font-medium text-gray-600 hover:bg-gray-50"
                >
                  -
                </button>
                <span className="text-2xl font-semibold text-gray-900 w-8 text-center">
                  {servings}
                </span>
                <button
                  onClick={() => setServings(Math.min(8, servings + 1))}
                  className="w-10 h-10 rounded-full border border-gray-300 flex items-center justify-center text-xl font-medium text-gray-600 hover:bg-gray-50"
                >
                  +
                </button>
              </div>
              <p className="text-xs text-gray-500 mt-1">
                Recipes will be scaled to this serving size. Each dinner yields 2 meals (dinner + leftover lunch).
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Unit System
              </label>
              <div className="flex gap-2">
                <button
                  onClick={() => setUnitSystemState('metric')}
                  className={`flex-1 px-4 py-2 rounded-lg border text-sm font-medium transition-colors ${
                    unitSystem === 'metric'
                      ? 'bg-primary-600 text-white border-primary-600'
                      : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
                  }`}
                >
                  Metric (g, ml)
                </button>
                <button
                  onClick={() => setUnitSystemState('imperial')}
                  className={`flex-1 px-4 py-2 rounded-lg border text-sm font-medium transition-colors ${
                    unitSystem === 'imperial'
                      ? 'bg-primary-600 text-white border-primary-600'
                      : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
                  }`}
                >
                  Imperial (lb, cups)
                </button>
              </div>
              <p className="text-xs text-gray-500 mt-1">
                Controls how weights are displayed in recipes and shopping lists.
              </p>
            </div>
          </div>
        </section>

        {/* Automatic Generation Section */}
        <section className="card">
          <h2 className="font-semibold text-gray-900 mb-4">Automatic Generation</h2>

          <div className="space-y-4">
            {/* Enable toggle */}
            <div className="flex items-center justify-between">
              <div>
                <label className="text-sm font-medium text-gray-700">
                  Schedule Automatic Generation
                </label>
                <p className="text-xs text-gray-500">
                  Generate recipe suggestions automatically each week
                </p>
              </div>
              <button
                onClick={() => updateSchedule({ enabled: !schedule.enabled })}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                  schedule.enabled ? 'bg-primary-600' : 'bg-gray-300'
                }`}
              >
                <span
                  className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                    schedule.enabled ? 'translate-x-6' : 'translate-x-1'
                  }`}
                />
              </button>
            </div>

            {/* Schedule options (shown when enabled) */}
            {schedule.enabled && (
              <>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Day
                    </label>
                    <select
                      value={schedule.dayOfWeek}
                      onChange={(e) => updateSchedule({ dayOfWeek: parseInt(e.target.value, 10) })}
                      className="input"
                    >
                      {DAYS.map(({ value, label }) => (
                        <option key={value} value={value}>
                          {label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Time
                    </label>
                    <select
                      value={schedule.hour}
                      onChange={(e) => updateSchedule({ hour: parseInt(e.target.value, 10) })}
                      className="input"
                    >
                      {HOURS.map(({ value, label }) => (
                        <option key={value} value={value}>
                          {label}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                {/* Next run preview */}
                <div className="bg-gray-50 rounded-lg p-3">
                  <p className="text-sm text-gray-600">
                    <span className="font-medium">Next generation:</span>{' '}
                    {getNextScheduledTime(schedule).toLocaleDateString('en-US', {
                      weekday: 'long',
                      month: 'short',
                      day: 'numeric',
                      hour: 'numeric',
                      minute: '2-digit',
                    })}
                  </p>
                </div>

                {/* Platform notice */}
                {!schedulingSupported && (
                  <div className="bg-amber-50 border border-amber-200 rounded-lg p-3">
                    <p className="text-sm text-amber-700">
                      Background scheduling requires the Android app. On web, you'll need to open the app manually.
                    </p>
                  </div>
                )}
              </>
            )}

            <p className="text-xs text-gray-500">
              Recipes will be generated based on your pantry contents and preferences.
              {schedule.enabled && ` Scheduled for ${formatScheduleDescription(schedule)}.`}
            </p>
          </div>
        </section>

        {/* Image Cache Section */}
        <section className="card">
          <h2 className="font-semibold text-gray-900 mb-4">Image Cache</h2>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Keep Images For
            </label>
            <select
              value={cacheExpiration}
              onChange={(e) => setCacheExpiration(parseInt(e.target.value, 10))}
              className="input"
            >
              {CACHE_EXPIRATION_OPTIONS.map(({ value, label }) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
            <p className="text-xs text-gray-500 mt-1">
              AI-generated recipe and step images older than this will be automatically deleted to save storage.
            </p>
          </div>
        </section>

        {/* Danger Zone */}
        <section className="card border-red-200">
          <h2 className="font-semibold text-red-600 mb-4">Danger Zone</h2>
          <button
            onClick={handleClearData}
            className="w-full btn bg-red-100 text-red-600 hover:bg-red-200"
          >
            Clear All Data
          </button>
          <p className="text-xs text-gray-500 mt-2">
            This will delete all your pantry items, recipes, and meal plans.
            API keys will be preserved.
          </p>
        </section>
      </div>
    </div>
  );
}
