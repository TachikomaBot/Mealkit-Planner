import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, addRecipe, createMealPlan, generateShoppingList, getRecentRecipes, addIngredient, type ShoppingListItem, type Recipe } from '../db';
import { type GeneratedRecipe, type GenerationProgress } from '../api/claude';
import { generateRecipeImages, hasGeminiKey } from '../api/gemini';
import { generateRecipePoolFromDataset } from '../services/recipeData';
import { generateMealsWithArchitect, isMealArchitectAvailable } from '../services/mealArchitect';
import { keepScreenAwake, allowScreenSleep } from '../native';
import { getNextWeekStart, isImageGenerationEnabled, formatQuantity } from '../utils/settings';
import { getPreGeneratedRecipes, clearPreGeneratedRecipes } from '../utils/scheduling';

// ============================================================================
// Types & Constants
// ============================================================================

type MealsViewState = 'plan' | 'shop' | 'cook';

const FILTERS = [
  { id: 'all', label: 'All' },
  { id: 'quick', label: 'Quick (<30m)', match: (r: GeneratedRecipe) => r.prepTimeMinutes + r.cookTimeMinutes <= 30 },
  { id: 'weekend', label: 'Weekend', match: (r: GeneratedRecipe) => r.prepTimeMinutes + r.cookTimeMinutes > 30 },
  { id: 'vegetarian', label: 'Vegetarian', match: (r: GeneratedRecipe) => r.tags.some(t => t.toLowerCase().includes('vegetarian')) },
  { id: 'one-pot', label: 'One-Pot', match: (r: GeneratedRecipe) => r.tags.some(t => t.toLowerCase().includes('one-pot') || t.toLowerCase().includes('sheet-pan')) },
  { id: 'asian', label: 'Asian', match: (r: GeneratedRecipe) => r.tags.some(t => ['asian', 'thai', 'chinese', 'japanese', 'korean', 'vietnamese'].includes(t.toLowerCase())) },
  { id: 'italian', label: 'Italian', match: (r: GeneratedRecipe) => r.tags.some(t => t.toLowerCase().includes('italian')) },
  { id: 'mexican', label: 'Mexican', match: (r: GeneratedRecipe) => r.tags.some(t => t.toLowerCase().includes('mexican')) },
] as const;

const CATEGORY_ORDER = ['Produce', 'Protein', 'Dairy', 'Bakery', 'Pantry', 'Spices', 'Oils', 'Frozen', 'Household', 'Other'];

const MANUAL_ITEM_CATEGORIES = [
  { value: 'Household', label: 'Household (paper, cleaning, etc.)' },
  { value: 'Produce', label: 'Produce' },
  { value: 'Protein', label: 'Protein' },
  { value: 'Dairy', label: 'Dairy' },
  { value: 'Bakery', label: 'Bakery' },
  { value: 'Pantry', label: 'Pantry' },
  { value: 'Oils', label: 'Oils' },
  { value: 'Frozen', label: 'Frozen' },
  { value: 'Other', label: 'Other' },
];

// ============================================================================
// Progress Notification Helpers
// ============================================================================

let lastProgressPercent = 0;
let lastProgressBody = '';

const updateProgress = async (progress: GenerationProgress) => {
  try {
    const { Capacitor } = await import('@capacitor/core');
    if (!Capacitor.isNativePlatform()) return;

    const { LocalNotifications } = await import('@capacitor/local-notifications');

    const perm = await LocalNotifications.checkPermissions();
    if (perm.display !== 'granted') return;

    const percent = Math.round(
      progress.phase === 'outlines' ? 10 :
        progress.phase === 'normalizing' ? 95 :
          10 + (progress.current / progress.total) * 85
    );

    const body = progress.phase === 'outlines' ? 'Loading recipes...' :
      progress.phase === 'normalizing' ? 'Finalizing selection...' :
        `Selecting recipes (${percent}%)`;

    lastProgressPercent = percent;
    lastProgressBody = body;

    await LocalNotifications.schedule({
      notifications: [{
        id: 999,
        title: 'Generating Meal Plan',
        body,
        channelId: 'progress',
        ongoing: true,
        autoCancel: false,
        extra: { progressBar: true, progressMax: 100, progressCurrent: percent },
      }]
    });
  } catch (e) {
    console.error('Failed to send progress notification:', e);
  }
};

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
        extra: { progressBar: true, progressMax: 100, progressCurrent: lastProgressPercent },
      }]
    });
  } catch (e) {
    console.error('Failed to repost progress notification:', e);
  }
};

const clearProgress = async () => {
  try {
    const { LocalNotifications } = await import('@capacitor/local-notifications');
    await LocalNotifications.cancel({ notifications: [{ id: 999 }] });
  } catch (e) { /* ignore */ }
};

// ============================================================================
// Main Component
// ============================================================================

export default function Meals() {
  const navigate = useNavigate();

  // Shared state
  const [isGenerating, setIsGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Plan state
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

  // Shop state
  const [shoppingMode, setShoppingMode] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [showAddItem, setShowAddItem] = useState(false);
  const [newItemName, setNewItemName] = useState('');
  const [newItemQuantity, setNewItemQuantity] = useState('1');
  const [newItemUnit, setNewItemUnit] = useState('units');
  const [newItemCategory, setNewItemCategory] = useState('Household');
  const [isShoppingListGenerating, setIsShoppingListGenerating] = useState(() =>
    localStorage.getItem('shopping_list_generating') === 'true'
  );

  // Refs
  const hasLoadedSession = useRef(false);
  const isGeneratingRef = useRef(false);
  const longPressTimer = useRef<number | null>(null);
  const longPressStartTime = useRef<number>(0);

  // Database queries
  const ingredients = useLiveQuery(() => db.ingredients.toArray());
  const shoppingList = useLiveQuery(() => db.shoppingList.orderBy('category').toArray());

  const mealPlan = useLiveQuery(() => {
    const weekStart = getNextWeekStart(new Date());
    return db.mealPlans.where('weekOf').equals(weekStart).first();
  });

  const plannedRecipeDetails = useLiveQuery(async () => {
    if (!mealPlan?.recipes) return [];
    const details: (Recipe & { cooked: boolean })[] = [];
    for (const planned of mealPlan.recipes) {
      const recipe = await db.recipes.get(planned.recipeId);
      if (recipe) {
        details.push({ ...recipe, cooked: planned.cooked });
      }
    }
    return details;
  }, [mealPlan]);

  // ============================================================================
  // Determine Current View State
  // ============================================================================

  const determineViewState = (): MealsViewState => {
    // If we're in the middle of selecting recipes from the pool, stay in plan
    if (recipePool && !mealPlan) {
      return 'plan';
    }

    const hasActivePlan = !!mealPlan && mealPlan.recipes.length > 0;
    const hasShoppingItems = !!shoppingList && shoppingList.length > 0;
    const allCooked = hasActivePlan && mealPlan.recipes.every(r => r.cooked);

    // If all recipes cooked, check if we should reset for new week
    if (allCooked) {
      const now = new Date();
      const nextWeek = getNextWeekStart(now);
      const planWeek = mealPlan.weekOf;
      // If plan is for a previous week, show plan state
      if (planWeek < nextWeek) {
        return 'plan';
      }
    }

    // No active plan -> Plan
    if (!hasActivePlan) {
      return 'plan';
    }

    // Has shopping items -> Shop
    if (hasShoppingItems) {
      return 'shop';
    }

    // Has plan, no shopping items -> Cook
    return 'cook';
  };

  const viewState = determineViewState();

  // ============================================================================
  // Effects
  // ============================================================================

  // Load session state on mount (for plan state)
  useEffect(() => {
    if (hasLoadedSession.current) return;
    hasLoadedSession.current = true;

    db.planSessionState.toArray().then((sessions) => {
      if (sessions.length > 0) {
        const latest = sessions[sessions.length - 1];
        try {
          if (latest.generationStartedAt && !latest.recipePool) {
            const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
            if (latest.generationStartedAt < oneHourAgo) {
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
          }
        } catch (e) {
          db.planSessionState.clear();
        }
      } else {
        const preGenerated = getPreGeneratedRecipes();
        if (preGenerated && preGenerated.recipes && preGenerated.recipes.length > 0) {
          setRecipePool(preGenerated.recipes);
          setSelectedIndices(new Set([0, 1, 2, 3, 4, 5].slice(0, Math.min(6, preGenerated.recipes.length))));
          clearPreGeneratedRecipes();
        }
      }
    });
  }, []);

  // Sync isGenerating to ref
  useEffect(() => {
    isGeneratingRef.current = isGenerating;
  }, [isGenerating]);

  // Re-post notification on foreground
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && isGeneratingRef.current) {
        setTimeout(() => {
          if (isGeneratingRef.current) repostProgressNotification();
        }, 300);
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, []);

  // Background task handler
  useEffect(() => {
    import('@capawesome/capacitor-background-task').then(({ BackgroundTask }) => {
      BackgroundTask.beforeExit(async () => {
        const taskId = 'manual-generation-task';
        if (isGeneratingRef.current) {
          while (isGeneratingRef.current) {
            await new Promise(resolve => setTimeout(resolve, 1000));
          }
        }
        await BackgroundTask.finish({ taskId });
      });
    }).catch(() => { });
  }, []);

  // Persist session state
  useEffect(() => {
    if (!hasLoadedSession.current) return;

    if (!recipePool) {
      db.planSessionState.clear();
      return;
    }

    db.planSessionState.clear().then(() => {
      db.planSessionState.add({
        recipePool: JSON.stringify(recipePool),
        selectedIndices: Array.from(selectedIndices),
        generationStartedAt: null,
        savedAt: new Date(),
      });
    });
  }, [recipePool, selectedIndices]);

  // Generate images when pool changes
  useEffect(() => {
    if (!recipePool || recipePool.length === 0) return;
    if (!isImageGenerationEnabled() || !hasGeminiKey()) return;

    setLoadingImages(true);
    setImageProgress({ completed: 0, total: recipePool.length });

    generateRecipeImages(
      recipePool.map(r => ({
        name: r.name,
        tags: r.tags,
        ingredients: r.ingredients.map(i => i.ingredientName)
      })),
      (completed, total) => setImageProgress({ completed, total })
    )
      .then(images => {
        setRecipeImages(images);
        setLoadingImages(false);
        setImageProgress(null);
      })
      .catch(err => {
        console.error('Failed to generate images:', err);
        setLoadingImages(false);
        setImageProgress(null);
      });
  }, [recipePool]);

  // Poll for shopping list generation completion
  useEffect(() => {
    if (!isShoppingListGenerating) return;

    const interval = setInterval(() => {
      const stillGenerating = localStorage.getItem('shopping_list_generating') === 'true';
      if (!stillGenerating) setIsShoppingListGenerating(false);
    }, 500);
    return () => clearInterval(interval);
  }, [isShoppingListGenerating]);

  // ============================================================================
  // Plan Handlers
  // ============================================================================

  const filteredRecipes = recipePool
    ? (() => {
        const mapped = recipePool.map((r, i) => ({ recipe: r, originalIndex: i }));
        const filtered = activeFilter === 'all'
          ? mapped
          : mapped.filter(({ recipe }) => {
              const filter = FILTERS.find(f => f.id === activeFilter);
              return filter && 'match' in filter ? filter.match(recipe) : true;
            });
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

    await keepScreenAwake();

    import('../native').then(({ requestNotificationPermissions }) => {
      requestNotificationPermissions();
    });

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

      if (isMealArchitectAvailable()) {
        const result = await generateMealsWithArchitect(
          ingredients ?? [],
          (progress) => {
            setGenerationProgress(progress);
            updateProgress(progress);
          }
        );
        await db.planSessionState.clear();
        setRecipePool(result.recipes);
        setSelectedIndices(new Set(result.defaultSelections));
      } else {
        const result = await generateRecipePoolFromDataset(
          ingredients ?? [],
          recentHashes,
          null,
          (progress) => {
            setGenerationProgress(progress);
            updateProgress(progress);
          }
        );
        await db.planSessionState.clear();
        setRecipePool(result.recipes);
        setSelectedIndices(new Set(result.defaultSelections));
      }
    } catch (err) {
      console.error('Failed to generate recipes:', err);
      setError(err instanceof Error ? err.message : 'Failed to generate recipes.');
      await db.planSessionState.clear();
    } finally {
      setIsGenerating(false);
      setGenerationProgress(null);
      await clearProgress();
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

  const startLongPress = () => {
    if (selectedIndices.size !== 6 || isGenerating) return;
    setIsLongPressing(true);
    longPressStartTime.current = Date.now();

    const animate = () => {
      const elapsed = Date.now() - longPressStartTime.current;
      const progress = Math.min(elapsed / 800, 1);
      setLongPressProgress(progress);

      if (progress >= 1) {
        handleConfirmPlan();
        setIsLongPressing(false);
        setLongPressProgress(0);
      } else if (longPressTimer.current !== null) {
        longPressTimer.current = requestAnimationFrame(animate);
      }
    };

    longPressTimer.current = requestAnimationFrame(animate);
  };

  const cancelLongPress = () => {
    if (longPressTimer.current !== null) {
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

      const mealPlanId = await createMealPlan(recipeIds.map(recipeId => ({ recipeId, plannedDate: null })));

      setRecipePool(null);
      setSelectedIndices(new Set());

      localStorage.setItem('shopping_list_generating', 'true');
      setIsShoppingListGenerating(true);

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

  const openRecipeDetail = (recipe: GeneratedRecipe, imageUrl?: string) => {
    navigate(`/recipe/${encodeURIComponent(recipe.name)}`, {
      state: { recipe, imageUrl }
    });
  };

  // ============================================================================
  // Shop Handlers
  // ============================================================================

  const groupedItems = shoppingList?.reduce((acc, item) => {
    if (!acc[item.category]) acc[item.category] = [];
    acc[item.category].push(item);
    return acc;
  }, {} as Record<string, ShoppingListItem[]>);

  const sortedCategories = groupedItems
    ? Object.keys(groupedItems).sort((a, b) => {
        const aIndex = CATEGORY_ORDER.indexOf(a);
        const bIndex = CATEGORY_ORDER.indexOf(b);
        if (aIndex === -1 && bIndex === -1) return a.localeCompare(b);
        if (aIndex === -1) return 1;
        if (bIndex === -1) return -1;
        return aIndex - bIndex;
      })
    : [];

  const checkedCount = shoppingList?.filter(i => i.checked).length ?? 0;
  const totalCount = shoppingList?.length ?? 0;
  const allChecked = totalCount > 0 && checkedCount === totalCount;

  const handleAddManualItem = async () => {
    if (!newItemName.trim()) return;

    await db.shoppingList.add({
      mealPlanId: 0,
      ingredientName: newItemName.trim(),
      quantity: parseFloat(newItemQuantity) || 1,
      unit: newItemUnit,
      category: newItemCategory,
      checked: false,
      inCart: false,
      notes: null,
    });

    setNewItemName('');
    setNewItemQuantity('1');
    setNewItemUnit('units');
    setNewItemCategory('Household');
    setShowAddItem(false);
  };

  const handleCompleteShoppingTrip = async () => {
    if (!shoppingList) return;

    setCompleting(true);
    try {
      const checkedItems = shoppingList.filter(item => item.checked);

      for (const item of checkedItems) {
        if (item.mealPlanId === 0) continue;
        await addIngredient({
          name: item.ingredientName,
          brand: null,
          quantityInitial: item.quantity,
          quantityRemaining: item.quantity,
          unit: item.unit as 'g' | 'ml' | 'units' | 'bunch',
          category: mapToIngredientCategory(item.category),
          perishable: ['Produce', 'Protein', 'Dairy'].includes(item.category),
          expiryDate: getDefaultExpiry(item.category),
        });
      }

      await db.shoppingList.clear();
      setShoppingMode(false);
    } catch (err) {
      console.error('Failed to complete shopping:', err);
    } finally {
      setCompleting(false);
    }
  };

  // ============================================================================
  // Cook Handlers
  // ============================================================================

  const openSavedRecipe = (recipe: Recipe) => {
    navigate(`/recipe/${encodeURIComponent(recipe.name)}`, {
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
    });
  };

  // ============================================================================
  // Render
  // ============================================================================

  // Show loading state for shopping list generation
  if (viewState === 'shop' && isShoppingListGenerating) {
    return (
      <div className="p-4 max-w-md mx-auto">
        <header className="mb-6">
          <h1 className="text-2xl font-bold text-gray-900">Shopping List</h1>
        </header>
        <div className="text-center py-16">
          <div className="w-20 h-20 mx-auto mb-6 relative">
            <div className="absolute inset-0 border-4 border-primary-200 rounded-full"></div>
            <div className="absolute inset-0 border-4 border-primary-600 rounded-full border-t-transparent animate-spin"></div>
            <span className="absolute inset-0 flex items-center justify-center text-3xl">🛒</span>
          </div>
          <h2 className="text-lg font-semibold text-gray-900 mb-2">
            Building Shopping List
          </h2>
          <p className="text-gray-600 text-sm max-w-xs mx-auto">
            AI is consolidating ingredients into practical shopping quantities...
          </p>
        </div>
      </div>
    );
  }

  // PLAN VIEW
  if (viewState === 'plan') {
    return (
      <div className="p-4 max-w-md mx-auto">
        <header className="mb-6">
          <h1 className="text-2xl font-bold text-gray-900">Plan Meals</h1>
          <p className="text-gray-600 mt-1">
            Week of {getNextWeekStart(new Date()).toLocaleDateString('en-CA', { month: 'short', day: 'numeric' })}
          </p>
        </header>

        {error && (
          <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {error}
          </div>
        )}

        {/* Recipe Pool Selection View */}
        {recipePool ? (
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
              <div className={`absolute w-full h-full rounded-full border-4 border-white shadow-lg transition-opacity duration-150 ${isLongPressing ? 'opacity-100' : 'opacity-0'}`} />
              <div
                className={`rounded-full flex items-center justify-center shadow-lg transition-all ${isLongPressing ? 'duration-0' : 'duration-200'} ${selectedIndices.size === 6 ? 'bg-green-500' : 'bg-primary-600'} ${isGenerating ? 'opacity-50' : ''}`}
                style={{
                  width: isLongPressing ? `${48 + longPressProgress * 84}px` : '96px',
                  height: isLongPressing ? `${48 + longPressProgress * 84}px` : '96px',
                }}
              >
                <span className={`text-white font-bold pointer-events-none transition-all ${isLongPressing ? 'text-lg' : 'text-2xl'}`}>
                  {isGenerating ? <Spinner /> : `${selectedIndices.size}/6`}
                </span>
              </div>
            </button>

            <div className="flex justify-between items-center mb-4">
              <h2 className="font-semibold text-gray-900">Select Your Meals</h2>
              {loadingImages && (
                <button onClick={() => { setLoadingImages(false); setImageProgress(null); }} className="text-sm text-gray-500 hover:text-gray-700">
                  Cancel images
                </button>
              )}
            </div>

            {/* Filter Carousel */}
            <div className="flex gap-2 overflow-x-auto pb-3 mb-4 -mx-4 px-4 scrollbar-hide">
              {FILTERS.map((filter) => (
                <button
                  key={filter.id}
                  onClick={() => setActiveFilter(filter.id)}
                  className={`px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-colors ${activeFilter === filter.id ? 'bg-primary-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'}`}
                >
                  {filter.label}
                </button>
              ))}
            </div>

            {/* Image progress */}
            {loadingImages && imageProgress && (
              <div className="mb-4 p-3 bg-blue-50 border border-blue-200 rounded-lg">
                <p className="text-sm text-blue-800">Generating images... {imageProgress.completed}/{imageProgress.total}</p>
              </div>
            )}

            {/* Recipe cards */}
            <div className="space-y-4 pb-40">
              {filteredRecipes.map(({ recipe, originalIndex }) => {
                const imageUrl = recipeImages.get(recipe.name);
                const isSelected = selectedIndices.has(originalIndex);

                return (
                  <div
                    key={originalIndex}
                    className={`bg-white rounded-xl overflow-hidden shadow-sm border transition-all cursor-pointer ${isSelected ? 'ring-2 ring-primary-500' : 'hover:shadow-md'}`}
                    onClick={() => openRecipeDetail(recipe, imageUrl)}
                  >
                    <div className="relative h-48 bg-gray-100">
                      {imageUrl ? (
                        <img src={imageUrl} alt={recipe.name} className="w-full h-full object-cover" />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center text-5xl">🍽️</div>
                      )}
                      <button
                        onClick={(e) => { e.stopPropagation(); toggleRecipe(originalIndex); }}
                        className="absolute top-2 right-2 p-2"
                      >
                        <div className={`w-7 h-7 rounded-full border-2 flex items-center justify-center ${isSelected ? 'bg-primary-600 border-primary-600 text-white' : 'bg-white/80 border-gray-300'}`}>
                          {isSelected && <span className="text-sm">✓</span>}
                        </div>
                      </button>
                      <div className="absolute bottom-3 left-3 bg-black/70 text-white text-xs px-2 py-1 rounded">
                        {recipe.prepTimeMinutes + recipe.cookTimeMinutes} min
                      </div>
                    </div>
                    <div className="p-3">
                      <h3 className="font-semibold text-gray-900 truncate">{recipe.name}</h3>
                      <p className="text-sm text-gray-500 line-clamp-2">{recipe.description}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        ) : (
          // Generate button view
          <div className="text-center py-12">
            <div className="w-20 h-20 bg-primary-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <span className="text-4xl">📅</span>
            </div>
            <h2 className="text-lg font-semibold text-gray-900 mb-2">
              {wasInterrupted ? 'Generation Interrupted' : 'Plan Your Week'}
            </h2>
            <p className="text-gray-600 text-sm mb-6 max-w-xs mx-auto">
              {wasInterrupted
                ? 'Your last generation was interrupted. Would you like to try again?'
                : 'Generate personalized dinner suggestions based on your pantry and preferences.'}
            </p>
            <button onClick={handleGeneratePlan} disabled={isGenerating} className="btn btn-primary">
              {isGenerating ? (
                <span className="flex items-center gap-2">
                  <Spinner />
                  {generationProgress?.phase === 'outlines' ? 'Loading recipes...'
                    : generationProgress?.phase === 'details' ? `Selecting ${generationProgress.current}/${generationProgress.total}...`
                    : generationProgress?.phase === 'normalizing' ? 'Finalizing...'
                    : 'Generating...'}
                </span>
              ) : wasInterrupted ? 'Retry Generation' : 'Generate Meal Plan'}
            </button>

            {isGenerating && generationProgress && (
              <div className="mt-4 w-full max-w-xs mx-auto">
                <div className="w-full bg-gray-200 rounded-full h-2">
                  <div
                    className="bg-primary-600 h-2 rounded-full transition-all duration-300"
                    style={{
                      width: generationProgress.phase === 'outlines' ? '10%'
                        : generationProgress.phase === 'normalizing' ? '95%'
                        : `${10 + (generationProgress.current / generationProgress.total) * 85}%`
                    }}
                  />
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    );
  }

  // SHOP VIEW
  if (viewState === 'shop') {
    return (
      <div className="p-4 max-w-md mx-auto">
        <header className="flex justify-between items-center mb-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Shopping List</h1>
            <p className="text-sm text-gray-500">{checkedCount} of {totalCount} items</p>
          </div>
          <div className="flex gap-2">
            <button onClick={() => setShowAddItem(true)} className="btn btn-secondary">+ Add</button>
            <button onClick={() => setShoppingMode(!shoppingMode)} className={`btn ${shoppingMode ? 'btn-primary' : 'btn-secondary'}`}>
              {shoppingMode ? 'Exit Shopping' : 'Start Shopping'}
            </button>
          </div>
        </header>

        {shoppingMode && (
          <div className="mb-4 p-3 bg-primary-50 border border-primary-200 rounded-lg">
            <p className="text-sm text-primary-800">
              <strong>Shopping Mode Active</strong> — Tap items to mark as found.
            </p>
          </div>
        )}

        {/* Add Item Modal */}
        {showAddItem && (
          <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
            <div className="bg-white rounded-xl w-full max-w-sm p-4">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Add Item</h2>
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Item Name</label>
                  <input
                    type="text"
                    value={newItemName}
                    onChange={(e) => setNewItemName(e.target.value)}
                    placeholder="e.g., Paper towels"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                    autoFocus
                  />
                </div>
                <div className="flex gap-3">
                  <div className="flex-1">
                    <label className="block text-sm font-medium text-gray-700 mb-1">Quantity</label>
                    <input type="number" value={newItemQuantity} onChange={(e) => setNewItemQuantity(e.target.value)} min="0.1" step="0.5" className="w-full px-3 py-2 border border-gray-300 rounded-lg" />
                  </div>
                  <div className="flex-1">
                    <label className="block text-sm font-medium text-gray-700 mb-1">Unit</label>
                    <select value={newItemUnit} onChange={(e) => setNewItemUnit(e.target.value)} className="w-full px-3 py-2 border border-gray-300 rounded-lg">
                      <option value="units">units</option>
                      <option value="g">g</option>
                      <option value="ml">ml</option>
                      <option value="pack">pack</option>
                      <option value="box">box</option>
                      <option value="bottle">bottle</option>
                    </select>
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Category</label>
                  <select value={newItemCategory} onChange={(e) => setNewItemCategory(e.target.value)} className="w-full px-3 py-2 border border-gray-300 rounded-lg">
                    {MANUAL_ITEM_CATEGORIES.map(cat => <option key={cat.value} value={cat.value}>{cat.label}</option>)}
                  </select>
                </div>
              </div>
              <div className="flex gap-3 mt-6">
                <button onClick={() => setShowAddItem(false)} className="flex-1 btn btn-secondary">Cancel</button>
                <button onClick={handleAddManualItem} disabled={!newItemName.trim()} className="flex-1 btn btn-primary disabled:opacity-50">Add Item</button>
              </div>
            </div>
          </div>
        )}

        {/* Progress bar */}
        <div className="mb-6">
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div className="bg-primary-600 h-2 rounded-full transition-all" style={{ width: `${totalCount > 0 ? (checkedCount / totalCount) * 100 : 0}%` }} />
          </div>
        </div>

        {/* Shopping list by category */}
        <div className="space-y-6 pb-24">
          {sortedCategories.map(category => (
            <section key={category}>
              <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-2">{category}</h2>
              <div className="space-y-2">
                {groupedItems![category].map(item => (
                  <ShoppingItem key={item.id} item={item} shoppingMode={shoppingMode} />
                ))}
              </div>
            </section>
          ))}
        </div>

        {/* Shopping mode actions */}
        {shoppingMode && (
          <div className="fixed bottom-20 left-4 right-4 max-w-md mx-auto">
            <div className="bg-white border border-gray-200 rounded-xl shadow-lg p-3">
              {allChecked ? (
                <button onClick={handleCompleteShoppingTrip} disabled={completing} className="w-full btn btn-primary">
                  {completing ? 'Adding to Pantry...' : 'Complete Shopping Trip'}
                </button>
              ) : (
                <p className="text-center text-sm text-gray-600">Tap items to mark as found ({checkedCount}/{totalCount})</p>
              )}
            </div>
          </div>
        )}
      </div>
    );
  }

  // COOK VIEW
  const plannedCount = mealPlan?.recipes.length ?? 0;
  const cookedCount = mealPlan?.recipes.filter(r => r.cooked).length ?? 0;

  return (
    <div className="p-4 max-w-md mx-auto">
      <header className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">This Week's Meals</h1>
        <p className="text-gray-600 mt-1">{cookedCount}/{plannedCount} cooked</p>
      </header>

      {/* Progress bar */}
      <div className="w-full bg-gray-200 rounded-full h-2 mb-6">
        <div className="bg-primary-600 h-2 rounded-full transition-all" style={{ width: `${(cookedCount / plannedCount) * 100}%` }} />
      </div>

      {/* Recipe list */}
      <div className="space-y-3 pb-24">
        {plannedRecipeDetails?.map(recipe => (
          <button
            key={recipe.id}
            onClick={() => openSavedRecipe(recipe)}
            className={`w-full text-left p-4 rounded-xl border transition-all ${recipe.cooked ? 'bg-gray-50 border-gray-200 opacity-60' : 'bg-white border-gray-200 hover:border-primary-300 hover:shadow-sm'}`}
          >
            <div className="flex items-center gap-3">
              <div className={`w-10 h-10 rounded-full flex items-center justify-center text-lg ${recipe.cooked ? 'bg-green-100 text-green-600' : 'bg-primary-100 text-primary-600'}`}>
                {recipe.cooked ? '✓' : '🍽'}
              </div>
              <div className="flex-1 min-w-0">
                <h3 className={`font-medium truncate ${recipe.cooked ? 'text-gray-500 line-through' : 'text-gray-900'}`}>
                  {recipe.name}
                </h3>
                <p className="text-sm text-gray-500">{recipe.prepTimeMinutes + recipe.cookTimeMinutes} min</p>
              </div>
              <span className="text-gray-400">›</span>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

// ============================================================================
// Sub-components
// ============================================================================

function ShoppingItem({ item, shoppingMode }: { item: ShoppingListItem; shoppingMode: boolean }) {
  const handleToggle = async () => {
    await db.shoppingList.update(item.id!, { checked: !item.checked });
  };

  return (
    <div
      onClick={shoppingMode ? handleToggle : undefined}
      className={`card flex items-center gap-3 ${shoppingMode ? 'cursor-pointer active:bg-gray-50' : ''} ${item.checked ? 'opacity-50' : ''}`}
    >
      <div className={`w-6 h-6 rounded-full border-2 flex items-center justify-center ${item.checked ? 'bg-primary-600 border-primary-600 text-white' : 'border-gray-300'}`}>
        {item.checked && <span className="text-sm">✓</span>}
      </div>
      <div className="flex-1">
        <p className={`font-medium ${item.checked ? 'line-through text-gray-400' : 'text-gray-900'}`}>{item.ingredientName}</p>
        <p className="text-sm text-gray-500">{formatQuantity(item.quantity)} {item.unit}</p>
      </div>
    </div>
  );
}

function Spinner() {
  return (
    <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
    </svg>
  );
}

// ============================================================================
// Helper Functions
// ============================================================================

function mapToIngredientCategory(shopCategory: string): 'dry goods' | 'dairy' | 'produce' | 'protein' | 'condiment' | 'spice' | 'oils' | 'frozen' | 'other' {
  switch (shopCategory) {
    case 'Produce': return 'produce';
    case 'Protein': return 'protein';
    case 'Dairy': return 'dairy';
    case 'Pantry': return 'dry goods';
    case 'Spices': return 'spice';
    case 'Oils': return 'oils';
    case 'Frozen': return 'frozen';
    case 'Bakery': return 'dry goods';
    default: return 'other';
  }
}

function getDefaultExpiry(category: string): Date | null {
  const now = new Date();
  switch (category) {
    case 'Produce': return new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
    case 'Protein': return new Date(now.getTime() + 5 * 24 * 60 * 60 * 1000);
    case 'Dairy': return new Date(now.getTime() + 14 * 24 * 60 * 60 * 1000);
    case 'Bakery': return new Date(now.getTime() + 5 * 24 * 60 * 60 * 1000);
    default: return null;
  }
}
