import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, addRecipe, createMealPlan, generateShoppingList, getRecentRecipes, type Recipe } from '../db';
import { type GeneratedRecipe, type GenerationProgress } from '../api/claude';
import { generateRecipeImages, hasGeminiKey } from '../api/gemini';
import { generateRecipePoolFromDataset } from '../services/recipeData';
import { generateMealsWithArchitect, isMealArchitectAvailable, type ComposedMeal } from '../services/mealArchitect';
import { keepScreenAwake, allowScreenSleep } from '../native';
import { getNextWeekStart, isImageGenerationEnabled } from '../utils/settings';
import { getPreGeneratedRecipes, clearPreGeneratedRecipes } from '../utils/scheduling';

// Filter options for recipe pool
const FILTERS = [
  { id: 'all', label: 'All' },
  { id: 'use-up', label: '🥬 Use Up', match: (r: GeneratedRecipe) => 'usesExpiringIngredients' in r && (r as ComposedMeal).usesExpiringIngredients },
  { id: 'quick', label: 'Quick', match: (r: GeneratedRecipe) => r.prepTimeMinutes + r.cookTimeMinutes <= 30 },
  { id: 'weekend', label: 'Weekend', match: (r: GeneratedRecipe) => r.prepTimeMinutes + r.cookTimeMinutes > 30 },
  { id: 'vegetarian', label: 'Vegetarian', match: (r: GeneratedRecipe) => r.tags.some(t => t.toLowerCase().includes('vegetarian')) },
  { id: 'one-pot', label: 'One-Pot', match: (r: GeneratedRecipe) => r.tags.some(t => t.toLowerCase().includes('one-pot') || t.toLowerCase().includes('sheet-pan')) },
  { id: 'asian', label: 'Asian', match: (r: GeneratedRecipe) => r.tags.some(t => ['asian', 'thai', 'chinese', 'japanese', 'korean', 'vietnamese'].includes(t.toLowerCase())) },
  { id: 'italian', label: 'Italian', match: (r: GeneratedRecipe) => r.tags.some(t => t.toLowerCase().includes('italian')) },
  { id: 'mexican', label: 'Mexican', match: (r: GeneratedRecipe) => r.tags.some(t => t.toLowerCase().includes('mexican')) },
] as const;

// Store last progress for re-posting notification when app returns to foreground
let lastProgressPercent = 0;
let lastProgressBody = '';

// Helper to update progress notification
const updateProgress = async (progress: GenerationProgress) => {
  try {
    const { Capacitor } = await import('@capacitor/core');
    if (!Capacitor.isNativePlatform()) return;

    const { LocalNotifications } = await import('@capacitor/local-notifications');

    // Check permissions - don't block if not granted
    const perm = await LocalNotifications.checkPermissions();
    if (perm.display !== 'granted') {
      console.log('Progress notification skipped - no permission');
      return;
    }

    const percent = Math.round(
      progress.phase === 'outlines' ? 10 :
        progress.phase === 'normalizing' ? 95 :
          10 + (progress.current / progress.total) * 85
    );

    const body = progress.phase === 'outlines' ? 'Loading recipes...' :
      progress.phase === 'normalizing' ? 'Finalizing selection...' :
        `Selecting recipes (${percent}%)`;

    // Store for re-posting when app returns
    lastProgressPercent = percent;
    lastProgressBody = body;

    await LocalNotifications.schedule({
      notifications: [{
        id: 999, // Reserved ID for progress
        title: 'Generating Meal Plan',
        body,
        channelId: 'progress',
        ongoing: true,
        autoCancel: false,
        // Android progress bar support
        extra: {
          progressBar: true,
          progressMax: 100,
          progressCurrent: percent,
          progressIndeterminate: false,
        },
      }]
    });
  } catch (e) {
    console.error('Failed to send progress notification:', e);
  }
};

// Helper to re-post the notification (called when app returns to foreground)
const repostProgressNotification = async () => {
  try {
    const { Capacitor } = await import('@capacitor/core');
    if (!Capacitor.isNativePlatform()) return;

    const { LocalNotifications } = await import('@capacitor/local-notifications');

    await LocalNotifications.schedule({
      notifications: [{
        id: 999,
        title: 'Generating Meal Plan',
        body: lastProgressBody || 'In progress...',
        channelId: 'progress',
        ongoing: true,
        autoCancel: false,
        extra: {
          progressBar: true,
          progressMax: 100,
          progressCurrent: lastProgressPercent,
          progressIndeterminate: false,
        },
      }]
    });
  } catch (e) {
    console.error('Failed to repost progress notification:', e);
  }
};

// Helper to clear progress notification
const clearProgress = async () => {
  try {
    const { LocalNotifications } = await import('@capacitor/local-notifications');
    await LocalNotifications.cancel({ notifications: [{ id: 999 }] });
  } catch (e) { }
};

export default function Plan() {
  const navigate = useNavigate();
  const [isGenerating, setIsGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [recipePool, setRecipePool] = useState<GeneratedRecipe[] | null>(null);
  const [selectedIndices, setSelectedIndices] = useState<Set<number>>(new Set());
  const [activeFilter, setActiveFilter] = useState<string>('all');
  const [recipeImages, setRecipeImages] = useState<Map<string, string>>(new Map());
  const [loadingImages, setLoadingImages] = useState(false);
  const [imageProgress, setImageProgress] = useState<{ completed: number; total: number } | null>(null);
  const [wasInterrupted, setWasInterrupted] = useState(false);
  const [generationProgress, setGenerationProgress] = useState<GenerationProgress | null>(null);
  const [longPressProgress, setLongPressProgress] = useState(0);
  const [isLongPressing, setIsLongPressing] = useState(false);
  const hasLoadedSession = useRef(false);
  const isGeneratingRef = useRef(false);
  const longPressTimer = useRef<number | null>(null);
  const longPressStartTime = useRef<number>(0);

  const ingredients = useLiveQuery(() => db.ingredients.toArray());

  // Navigate to recipe detail page
  const openRecipeDetail = (recipe: GeneratedRecipe, imageUrl?: string) => {
    // Check if recipe has expiring ingredients info (ComposedMeal type)
    const expiringIngredientsUsed = 'expiringIngredientsUsed' in recipe
      ? (recipe as ComposedMeal).expiringIngredientsUsed
      : undefined;
    navigate(`/recipe/${encodeURIComponent(recipe.name)}`, {
      state: { recipe, imageUrl, expiringIngredientsUsed }
    });
  };

  // Load persisted session state on mount
  useEffect(() => {
    if (hasLoadedSession.current) return;
    hasLoadedSession.current = true;

    db.planSessionState.toArray().then((sessions) => {
      if (sessions.length > 0) {
        const latest = sessions[sessions.length - 1];
        try {
          // Check if generation was interrupted
          if (latest.generationStartedAt && !latest.recipePool) {
            // Auto-reset if the generation started more than 1 hour ago
            const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
            if (latest.generationStartedAt < oneHourAgo) {
              console.log('Clearing stale generation marker (older than 1 hour)');
              db.planSessionState.clear();
              return;
            }
            setWasInterrupted(true);
            return;
          }

          if (latest.recipePool) {
            const pool = JSON.parse(latest.recipePool) as GeneratedRecipe[];
            setRecipePool(pool);
            setSelectedIndices(new Set(latest.selectedIndices));
            return;
          }
        } catch (e) {
          console.error('Failed to restore session:', e);
        }
      }

      // Check for pre-generated recipes from background scheduling
      const preGenerated = getPreGeneratedRecipes();
      if (preGenerated) {
        setRecipePool(preGenerated.recipes);
        // Auto-select first 6 recipes as default
        setSelectedIndices(new Set([0, 1, 2, 3, 4, 5].slice(0, Math.min(6, preGenerated.recipes.length))));
        // Clear the pre-generated cache so it's not reused
        clearPreGeneratedRecipes();
      }
    });
  }, []);

  // Sync isGenerating state to Ref for background task access
  useEffect(() => {
    isGeneratingRef.current = isGenerating;
  }, [isGenerating]);

  // Re-post notification when app returns to foreground while generating
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && isGeneratingRef.current) {
        // Small delay to ensure app is fully in foreground
        setTimeout(() => {
          if (isGeneratingRef.current) {
            repostProgressNotification();
          }
        }, 300);
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, []);

  // Handle Detection of App Backgrounding to keep task alive
  useEffect(() => {
    // Import dynamically to avoid issues in non-native envs if package missing
    import('@capawesome/capacitor-background-task').then(({ BackgroundTask }) => {
      // Register a listener that runs when the app goes to background
      BackgroundTask.beforeExit(async () => {
        const taskId = 'manual-generation-task';
        // If we are generating, keep the app alive by waiting
        if (isGeneratingRef.current) {
          console.log('App backgrounded while generating - keeping alive...');

          // Poll every 1s to see if generation finished
          while (isGeneratingRef.current) {
            await new Promise(resolve => setTimeout(resolve, 1000));
          }

          console.log('Generation finished in background, ending task.');
        }
        await BackgroundTask.finish({ taskId });
      });
    }).catch(() => {
      // Plugin not available or web
    });
  }, []);



  // Persist session state when recipePool or selectedIndices change
  useEffect(() => {
    if (!hasLoadedSession.current) return;

    // Clear session if pool is null (user confirmed or cancelled)
    if (!recipePool) {
      db.planSessionState.clear();
      return;
    }

    // Save current state
    db.planSessionState.clear().then(() => {
      db.planSessionState.add({
        recipePool: JSON.stringify(recipePool),
        selectedIndices: Array.from(selectedIndices),
        generationStartedAt: null,
        savedAt: new Date(),
      });
    });
  }, [recipePool, selectedIndices]);

  const mealPlan = useLiveQuery(() => {
    const weekStart = getNextWeekStart(new Date());
    return db.mealPlans.where('weekOf').equals(weekStart).first();
  });

  const plannedRecipeDetails = useLiveQuery(async () => {
    if (!mealPlan?.recipes) return [];
    const details: Recipe[] = [];
    for (const planned of mealPlan.recipes) {
      const recipe = await db.recipes.get(planned.recipeId);
      if (recipe) details.push(recipe);
    }
    return details;
  }, [mealPlan]);

  // Background image generation - starts automatically after recipe pool is generated
  // Prioritizes selected recipes first, then continues with the rest
  useEffect(() => {
    if (!recipePool || !hasGeminiKey()) return;

    // Build ordered list: selected first, then rest
    const selectedSet = new Set(selectedIndices);
    const selectedRecipes = Array.from(selectedIndices).map(i => ({
      name: recipePool[i].name,
      tags: recipePool[i].tags,
      ingredients: recipePool[i].ingredients.map(ing => ing.ingredientName),
    }));
    const otherRecipes = recipePool
      .map((r, i) => ({ name: r.name, tags: r.tags, ingredients: r.ingredients.map(ing => ing.ingredientName), index: i }))
      .filter(r => !selectedSet.has(r.index))
      .map(({ name, tags, ingredients }) => ({ name, tags, ingredients }));

    const allRecipes = [...selectedRecipes, ...otherRecipes];

    // Skip image generation if disabled (cost savings during testing)
    if (!isImageGenerationEnabled()) return;

    // Filter out recipes we already have images for
    const missingImages = allRecipes.filter(r => !recipeImages.has(r.name));
    if (missingImages.length === 0) return;

    setLoadingImages(true);
    setImageProgress({ completed: 0, total: missingImages.length });

    // Generate all images in background
    generateRecipeImages(
      missingImages,
      (completed, total, images) => {
        setImageProgress({ completed, total });
        setRecipeImages(prev => new Map([...prev, ...images]));
      }
    )
      .then(images => {
        setRecipeImages(prev => new Map([...prev, ...images]));
        setLoadingImages(false);
        setImageProgress(null);
      })
      .catch(err => {
        console.error('Failed to generate images:', err);
        setLoadingImages(false);
        setImageProgress(null);
      });
    // Only run when recipePool changes (not on every selection change)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recipePool]);

  // Filter and sort recipes - selected items at top, updates instantly on toggle
  const filteredRecipes = recipePool
    ? (() => {
      const mapped = recipePool.map((r, i) => ({ recipe: r, originalIndex: i }));
      const filtered = activeFilter === 'all'
        ? mapped
        : mapped.filter(({ recipe }) => {
          const filter = FILTERS.find(f => f.id === activeFilter);
          return filter && 'match' in filter ? filter.match(recipe) : true;
        });
      // Sort: selected recipes first, then unselected
      return filtered.sort((a, b) => {
        const aSelected = selectedIndices.has(a.originalIndex) ? 0 : 1;
        const bSelected = selectedIndices.has(b.originalIndex) ? 0 : 1;
        return aSelected - bSelected;
      });
    })()
    : [];

  const handleGeneratePlan = async () => {
    setIsGenerating(true);
    setError(null);
    setWasInterrupted(false);
    setGenerationProgress(null);


    // Keep screen awake during generation (native only)
    await keepScreenAwake();

    // Ensure we have notification permissions for the progress tracker
    import('../native').then(({ requestNotificationPermissions }) => {
      requestNotificationPermissions();
    });

    // Save generation start time in case we get interrupted
    await db.planSessionState.clear();
    await db.planSessionState.add({
      recipePool: '',
      selectedIndices: [],
      generationStartedAt: new Date(),
      savedAt: new Date(),
    });

    try {
      const recentHistory = await getRecentRecipes(3);
      const recentHashes = recentHistory.map(r => r.recipeHash);

      // Use Claude Meal Architect if API key available, otherwise dataset-based
      if (isMealArchitectAvailable()) {
        console.log('Using Claude Meal Architect for intelligent meal composition');
        const result = await generateMealsWithArchitect(
          ingredients ?? [],
          (progress) => {
            setGenerationProgress(progress);
            updateProgress(progress); // Always update notification
          }
        );

        // Clear the generation-in-progress marker
        await db.planSessionState.clear();

        // Store the full composed meals (includes expiring ingredient flags)
        setRecipePool(result.recipes);
        setSelectedIndices(new Set(result.defaultSelections));
      } else {
        // Fallback to dataset-based generation (no API key required)
        console.log('Using dataset-based recipe selection (no Claude API key)');
        const result = await generateRecipePoolFromDataset(
          ingredients ?? [],
          recentHashes,
          null, // preferences - not implemented yet
          (progress) => {
            setGenerationProgress(progress);
            updateProgress(progress); // Always update notification
          }
        );

        // Clear the generation-in-progress marker
        await db.planSessionState.clear();

        setRecipePool(result.recipes);
        setSelectedIndices(new Set(result.defaultSelections));
      }
      setGenerationProgress(null);
    } catch (err) {
      console.error('Failed to generate recipes:', err);
      setError(err instanceof Error ? err.message : 'Failed to generate recipes.');
      // Clear the generation marker on error too
      await db.planSessionState.clear();
    } finally {
      setIsGenerating(false);
      setGenerationProgress(null);
      await clearProgress();
      // Allow screen to sleep again
      await allowScreenSleep();
    }
  };

  const toggleRecipe = (index: number) => {
    const newSelected = new Set(selectedIndices);
    if (newSelected.has(index)) {
      newSelected.delete(index);
    } else if (newSelected.size < 6) {
      newSelected.add(index);
    }
    setSelectedIndices(newSelected);
  };

  // Long press handlers for confirm button
  const LONG_PRESS_DURATION = 2000; // 2 seconds

  const startLongPress = () => {
    if (selectedIndices.size !== 6 || isGenerating) return;

    setIsLongPressing(true);
    longPressStartTime.current = Date.now();

    // Animate progress
    const animateProgress = () => {
      const elapsed = Date.now() - longPressStartTime.current;
      const progress = Math.min(elapsed / LONG_PRESS_DURATION, 1);
      setLongPressProgress(progress);

      if (progress < 1) {
        longPressTimer.current = requestAnimationFrame(animateProgress);
      } else {
        // Long press completed - confirm the plan
        handleConfirmPlan();
        cancelLongPress();
      }
    };

    longPressTimer.current = requestAnimationFrame(animateProgress);
  };

  const cancelLongPress = () => {
    if (longPressTimer.current) {
      cancelAnimationFrame(longPressTimer.current);
      longPressTimer.current = null;
    }
    setIsLongPressing(false);
    setLongPressProgress(0);
  };

  const handleConfirmPlan = async () => {
    if (!recipePool || selectedIndices.size === 0) return;

    setIsGenerating(true);
    try {
      // Save selected recipes to database
      const recipeIds: number[] = [];
      for (const index of selectedIndices) {
        const generated = recipePool[index];
        const id = await addRecipe({
          name: generated.name,
          description: generated.description,
          servings: generated.servings,
          prepTimeMinutes: generated.prepTimeMinutes,
          cookTimeMinutes: generated.cookTimeMinutes,
          ingredients: generated.ingredients,
          steps: generated.steps,
          tags: generated.tags,
          source: 'generated',
        });
        recipeIds.push(id);
      }

      // Create meal plan
      const mealPlanId = await createMealPlan(recipeIds.map(recipeId => ({ recipeId, plannedDate: null })));

      // Clear the pool view
      setRecipePool(null);
      setSelectedIndices(new Set());

      // Set flag so Shop page shows loading state immediately
      localStorage.setItem('shopping_list_generating', 'true');

      // Navigate immediately - don't wait for shopping list generation
      navigate('/shop');

      // Generate shopping list in background (Shop page will poll for completion)
      generateShoppingList(mealPlanId)
        .catch(err => console.error('Failed to generate shopping list:', err))
        .finally(() => localStorage.removeItem('shopping_list_generating'));
    } catch (err) {
      console.error('Failed to save meal plan:', err);
      setError('Failed to save meal plan');
    } finally {
      setIsGenerating(false);
    }
  };

  return (
    <div className="p-4 max-w-md mx-auto">
      <header className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Meal Plan</h1>
        <p className="text-gray-600 mt-1">Week of {formatWeekOf(new Date())}</p>
      </header>

      {/* Error display */}
      {error && (
        <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg">
          <p className="text-sm text-red-800">{error}</p>
        </div>
      )}

      {/* Recipe Pool Selection View */}
      {recipePool && !mealPlan ? (
        <div className="relative">
          {/* Floating selection counter / confirm button */}
          <button
            className="fixed bottom-24 left-1/2 -translate-x-1/2 z-20 w-36 h-36 flex items-center justify-center select-none"
            onMouseDown={startLongPress}
            onMouseUp={cancelLongPress}
            onMouseLeave={cancelLongPress}
            onTouchStart={startLongPress}
            onTouchEnd={cancelLongPress}
            onTouchCancel={cancelLongPress}
            onContextMenu={(e) => e.preventDefault()}
            disabled={isGenerating}
          >
            {/* White target ring - appears on press */}
            <div
              className={`absolute w-full h-full rounded-full border-4 border-white shadow-lg transition-opacity duration-150 ${
                isLongPressing ? 'opacity-100' : 'opacity-0'
              }`}
            />

            {/* Growing colored circle */}
            <div
              className={`rounded-full flex items-center justify-center shadow-lg transition-all ${
                isLongPressing ? 'duration-0' : 'duration-200'
              } ${
                selectedIndices.size === 6 ? 'bg-green-500' : 'bg-primary-600'
              } ${isGenerating ? 'opacity-50' : ''}`}
              style={{
                // Default: 96px, Pressing: starts at 48px and grows to 132px (leaving room for white border)
                width: isLongPressing
                  ? `${48 + longPressProgress * 84}px`
                  : '96px',
                height: isLongPressing
                  ? `${48 + longPressProgress * 84}px`
                  : '96px',
              }}
            >
              <span className={`text-white font-bold pointer-events-none transition-all ${
                isLongPressing ? 'text-lg' : 'text-2xl'
              }`}>
                {isGenerating ? <Spinner /> : `${selectedIndices.size}/6`}
              </span>
            </div>
          </button>

          <div className="flex justify-between items-center mb-4">
            <h2 className="font-semibold text-gray-900">
              Select Your Meals
            </h2>
            {/* Show Cancel only while images are still generating */}
            {loadingImages && (
              <button
                onClick={() => {
                  setRecipePool(null);
                  setSelectedIndices(new Set());
                  setActiveFilter('all');
                }}
                className="text-sm text-gray-500"
              >
                Cancel
              </button>
            )}
          </div>

          {/* Filter Carousel */}
          <div className="flex gap-2 overflow-x-auto pb-3 mb-4 -mx-4 px-4 scrollbar-hide">
            {FILTERS.map((filter) => (
              <button
                key={filter.id}
                onClick={() => setActiveFilter(filter.id)}
                className={`px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-colors ${activeFilter === filter.id
                  ? 'bg-primary-600 text-white'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
              >
                {filter.label}
              </button>
            ))}
          </div>

          {/* Generating images indicator */}
          {loadingImages && imageProgress && (
            <div className="mb-4">
              <div className="flex justify-between text-sm text-gray-500 mb-1">
                <span>Generating images with AI...</span>
                <span>{imageProgress.completed}/{imageProgress.total}</span>
              </div>
              <div className="w-full bg-gray-200 rounded-full h-2">
                <div
                  className="bg-primary-600 h-2 rounded-full transition-all duration-300"
                  style={{ width: `${(imageProgress.completed / imageProgress.total) * 100}%` }}
                />
              </div>
            </div>
          )}

          <div className="space-y-4 mb-6">
            {filteredRecipes.map(({ recipe, originalIndex }) => {
              const imageUrl = recipeImages.get(recipe.name);
              const isSelected = selectedIndices.has(originalIndex);

              return (
                <div
                  key={originalIndex}
                  className={`bg-white rounded-xl overflow-hidden shadow-sm border transition-all cursor-pointer ${isSelected
                    ? 'ring-2 ring-primary-500'
                    : 'hover:shadow-md'
                    }`}
                  onClick={() => openRecipeDetail(recipe, imageUrl)}
                >
                  {/* Image */}
                  <div className="relative h-56 bg-gray-100">
                    {imageUrl ? (
                      <img
                        src={imageUrl}
                        alt={recipe.name}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-4xl">
                        🍽️
                      </div>
                    )}
                    {/* Selection indicator - click to toggle, with larger hit area */}
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        toggleRecipe(originalIndex);
                      }}
                      className="absolute top-2 right-2 p-2"
                    >
                      <div
                        className={`w-7 h-7 rounded-full border-2 flex items-center justify-center ${isSelected
                          ? 'bg-primary-600 border-primary-600 text-white'
                          : 'bg-white/80 border-gray-300'
                          }`}
                      >
                        {isSelected && <span className="text-sm">✓</span>}
                      </div>
                    </button>
                    {/* Time badge */}
                    <div className="absolute bottom-3 left-3 bg-black/70 text-white text-xs px-2 py-1 rounded">
                      {recipe.prepTimeMinutes + recipe.cookTimeMinutes} min
                    </div>
                    {/* Uses expiring ingredients badge */}
                    {'usesExpiringIngredients' in recipe && (recipe as ComposedMeal).usesExpiringIngredients && (
                      <div className="absolute bottom-3 right-3 bg-amber-500 text-white text-xs px-2 py-1 rounded flex items-center gap-1">
                        <span>🥬</span> Use it up
                      </div>
                    )}
                  </div>

                  {/* Content */}
                  <div className="p-3">
                    <div className="flex justify-between items-start">
                      <div className="flex-1">
                        <h3 className="font-semibold text-gray-900">{recipe.name}</h3>
                        <p className="text-sm text-gray-500 mt-1 line-clamp-2">{recipe.description}</p>
                      </div>
                      <span className="text-gray-400 ml-2" title="View details">
                        ›
                      </span>
                    </div>
                    <div className="flex flex-wrap gap-1 mt-2">
                      {recipe.tags.slice(0, 3).map((tag) => (
                        <span
                          key={tag}
                          className="px-2 py-0.5 bg-gray-100 text-gray-600 text-xs rounded"
                        >
                          {tag}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {filteredRecipes.length === 0 && (
            <div className="text-center py-8 text-gray-500">
              No recipes match this filter
            </div>
          )}

          {/* Recipe count */}
          <p className="text-sm text-gray-500 text-center mb-3">
            {recipePool?.length ?? 0} recipes available
          </p>
        </div>
      ) : !mealPlan ? (
        <div className="text-center py-12">
          <div className="mb-6">
            <div className="w-16 h-16 bg-primary-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <span className="text-3xl">{wasInterrupted ? '⚠️' : '📅'}</span>
            </div>
            <h2 className="text-lg font-semibold text-gray-900 mb-2">
              {wasInterrupted ? 'Generation was interrupted' : 'No plan for this week'}
            </h2>
            <p className="text-gray-600 text-sm mb-6">
              {wasInterrupted
                ? 'The app went to sleep or you navigated away. Tap below to try again.'
                : 'Generate a meal plan based on your pantry and preferences'}
            </p>
          </div>

          <button
            onClick={handleGeneratePlan}
            disabled={isGenerating}
            className="btn btn-primary"
          >
            {isGenerating ? (
              <span className="flex items-center gap-2">
                <Spinner />
                {generationProgress?.phase === 'outlines'
                  ? 'Loading recipes...'
                  : generationProgress?.phase === 'details'
                    ? `Selecting recipes ${generationProgress.current}/${generationProgress.total}...`
                    : generationProgress?.phase === 'normalizing'
                      ? 'Finalizing selection...'
                      : 'Generating...'}
              </span>
            ) : wasInterrupted ? (
              'Retry Generation'
            ) : (
              'Generate Meal Plan'
            )}
          </button>

          {/* Progress bar during generation */}
          {isGenerating && generationProgress && (
            <div className="mt-4 w-full max-w-xs mx-auto">
              <div className="flex justify-between text-xs text-gray-500 mb-1">
                <span>
                  {generationProgress.phase === 'outlines'
                    ? 'Loading recipe database'
                    : generationProgress.phase === 'normalizing'
                      ? 'Finalizing selection'
                      : 'Selecting recipes'}
                </span>
                {generationProgress.phase === 'details' && (
                  <span>{generationProgress.current}/{generationProgress.total}</span>
                )}
              </div>
              <div className="w-full bg-gray-200 rounded-full h-2">
                <div
                  className="bg-primary-600 h-2 rounded-full transition-all duration-300"
                  style={{
                    width: generationProgress.phase === 'outlines'
                      ? '10%'
                      : generationProgress.phase === 'normalizing'
                        ? '95%'
                        : `${10 + (generationProgress.current / generationProgress.total) * 85}%`
                  }}
                />
              </div>
            </div>
          )}

          {!wasInterrupted && !isGenerating && (
            <div className="mt-8 card text-left">
              <h3 className="font-medium text-gray-900 mb-2">How it works</h3>
              <ol className="text-sm text-gray-600 space-y-2">
                <li className="flex gap-2">
                  <span className="text-primary-600 font-medium">1.</span>
                  We search 5,000+ recipes to find 24 diverse options
                </li>
                <li className="flex gap-2">
                  <span className="text-primary-600 font-medium">2.</span>
                  Recipes are scored based on your pantry and preferences
                </li>
                <li className="flex gap-2">
                  <span className="text-primary-600 font-medium">3.</span>
                  Pick your 6 favorites from the selection
                </li>
                <li className="flex gap-2">
                  <span className="text-primary-600 font-medium">4.</span>
                  Confirm to generate your shopping list
                </li>
              </ol>
            </div>
          )}

          {wasInterrupted && (
            <p className="mt-4 text-sm text-gray-500">
              Tip: Keep the screen on while generating to avoid interruptions.
            </p>
          )}
        </div>
      ) : (
        <div>
          {/* Planned recipes */}
          <section className="mb-6">
            <div className="flex justify-between items-center mb-3">
              <h2 className="font-semibold text-gray-900">This Week's Meals</h2>
              <span className="text-sm text-gray-500">
                {mealPlan.recipes.filter((r) => r.cooked).length} /{' '}
                {mealPlan.recipes.length} cooked
              </span>
            </div>

            <div className="space-y-3">
              {plannedRecipeDetails?.map((recipe, index) => {
                const planned = mealPlan.recipes[index];
                return (
                  <RecipeCard
                    key={recipe.id}
                    recipe={recipe}
                    cooked={planned?.cooked ?? false}
                    onClick={() => navigate(`/recipe/${encodeURIComponent(recipe.name)}`, {
                      state: {
                        recipe: {
                          name: recipe.name,
                          description: recipe.description,
                          servings: recipe.servings,
                          prepTimeMinutes: recipe.prepTimeMinutes,
                          cookTimeMinutes: recipe.cookTimeMinutes,
                          ingredients: recipe.ingredients,
                          steps: recipe.steps,
                          tags: recipe.tags,
                        }
                      }
                    })}
                  />
                );
              })}
            </div>
          </section>

          {/* Actions */}
          <div className="space-y-2">
            <button
              onClick={() => navigate('/shop')}
              className="w-full btn btn-primary"
            >
              View Shopping List
            </button>
            <button
              onClick={async () => {
                if (confirm('This will clear your current meal plan and let you generate a new one. Continue?')) {
                  // Delete the current meal plan
                  if (mealPlan?.id) {
                    await db.mealPlans.delete(mealPlan.id);
                  }
                }
              }}
              className="w-full btn btn-secondary"
            >
              New Plan
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function RecipeCard({ recipe, cooked, onCook, onClick }: {
  recipe: Recipe;
  cooked: boolean;
  onCook?: () => void;
  onClick?: () => void;
}) {
  const totalTime = recipe.prepTimeMinutes + recipe.cookTimeMinutes;

  return (
    <div
      className={`card cursor-pointer ${cooked ? 'opacity-60' : ''}`}
      onClick={onClick}
    >
      <div className="flex justify-between items-start">
        <div className="flex-1">
          <h3 className="font-medium text-gray-900 flex items-center gap-2">
            {recipe.name}
            {cooked && <span className="text-green-600">✓</span>}
          </h3>
          <p className="text-sm text-gray-500 mt-1">{recipe.description}</p>
          <div className="flex gap-3 mt-2 text-xs text-gray-500">
            <span>{totalTime} min</span>
            <span>{recipe.servings} servings</span>
          </div>
        </div>
        {!cooked && (
          <button
            className="btn btn-secondary text-xs py-1 px-2"
            onClick={(e) => {
              e.stopPropagation();
              onCook?.();
            }}
          >
            Cook
          </button>
        )}
      </div>
    </div>
  );
}

function Spinner() {
  return (
    <svg
      className="animate-spin h-4 w-4"
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
    >
      <circle
        className="opacity-25"
        cx="12"
        cy="12"
        r="10"
        stroke="currentColor"
        strokeWidth="4"
      />
      <path
        className="opacity-75"
        fill="currentColor"
        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
      />
    </svg>
  );
}

function formatWeekOf(date: Date): string {
  const weekStart = getNextWeekStart(date);

  return weekStart.toLocaleDateString('en-CA', {
    month: 'short',
    day: 'numeric',
  });
}
