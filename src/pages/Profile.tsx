import { useState, useEffect } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, getStoredPreferenceSummary } from '../db';
import type { PreferenceSummary, MealPlan } from '../db/types';

// ============================================================================
// Types
// ============================================================================

interface MealPlanWithRecipes extends MealPlan {
  recipeNames: string[];
}

// ============================================================================
// Main Component
// ============================================================================

export default function Profile() {
  const [activeTab, setActiveTab] = useState<'preferences' | 'history' | 'stats'>('preferences');
  const [preferenceSummary, setPreferenceSummary] = useState<PreferenceSummary | null>(null);
  const [isEditingProfile, setIsEditingProfile] = useState(false);
  const [editedSummary, setEditedSummary] = useState('');
  const [editedLikes, setEditedLikes] = useState<string[]>([]);
  const [editedDislikes, setEditedDislikes] = useState<string[]>([]);
  const [newLike, setNewLike] = useState('');
  const [newDislike, setNewDislike] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [focusedField, setFocusedField] = useState<string | null>(null);

  // Load preference summary
  useEffect(() => {
    getStoredPreferenceSummary().then(summary => {
      if (summary) {
        setPreferenceSummary(summary);
        setEditedSummary(summary.summary);
        setEditedLikes(summary.likes || []);
        setEditedDislikes(summary.dislikes || []);
      }
    });
  }, []);

  // Load meal plan history with recipe names
  const mealPlanHistory = useLiveQuery(async () => {
    const plans = await db.mealPlans.orderBy('weekOf').reverse().toArray();
    const plansWithRecipes: MealPlanWithRecipes[] = [];

    for (const plan of plans) {
      const recipeNames: string[] = [];
      for (const pr of plan.recipes) {
        const recipe = await db.recipes.get(pr.recipeId);
        if (recipe) recipeNames.push(recipe.name);
      }
      plansWithRecipes.push({ ...plan, recipeNames });
    }

    return plansWithRecipes;
  });

  // Stats
  const recipeHistory = useLiveQuery(() => db.recipeHistory.toArray());
  const totalCooked = recipeHistory?.length ?? 0;
  const ratedHistory = recipeHistory?.filter(r => r.rating !== null) ?? [];
  const avgRating = ratedHistory.length > 0
    ? (ratedHistory.reduce((sum, r) => sum + (r.rating ?? 0), 0) / ratedHistory.length).toFixed(1)
    : 'N/A';

  // ============================================================================
  // Handlers
  // ============================================================================

  const handleSaveProfile = async () => {
    setIsSaving(true);
    try {
      const existingSummary = await getStoredPreferenceSummary();

      const updatedSummary: Omit<PreferenceSummary, 'id'> = {
        summary: editedSummary,
        likes: editedLikes,
        dislikes: editedDislikes,
        lastUpdated: new Date(),
        entriesProcessed: existingSummary?.entriesProcessed ?? 0,
      };

      if (existingSummary?.id) {
        await db.preferenceSummary.update(existingSummary.id, updatedSummary);
      } else {
        await db.preferenceSummary.add(updatedSummary as PreferenceSummary);
      }

      setPreferenceSummary({ ...updatedSummary, id: existingSummary?.id } as PreferenceSummary);
      setIsEditingProfile(false);
    } catch (err) {
      console.error('Failed to save profile:', err);
    } finally {
      setIsSaving(false);
    }
  };

  const addLike = () => {
    if (newLike.trim() && !editedLikes.includes(newLike.trim())) {
      setEditedLikes([...editedLikes, newLike.trim()]);
      setNewLike('');
    }
  };

  const removeLike = (item: string) => {
    setEditedLikes(editedLikes.filter(l => l !== item));
  };

  const addDislike = () => {
    if (newDislike.trim() && !editedDislikes.includes(newDislike.trim())) {
      setEditedDislikes([...editedDislikes, newDislike.trim()]);
      setNewDislike('');
    }
  };

  const removeDislike = (item: string) => {
    setEditedDislikes(editedDislikes.filter(d => d !== item));
  };

  // ============================================================================
  // Render
  // ============================================================================

  return (
    <div className="p-4 max-w-md mx-auto">
      <header className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Profile</h1>
        <p className="text-gray-600 mt-1">Your taste preferences and history</p>
      </header>

      {/* Tab Navigation */}
      <div className="flex border-b border-gray-200 mb-6">
        <button
          onClick={() => setActiveTab('preferences')}
          className={`flex-1 py-3 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'preferences'
              ? 'border-primary-600 text-primary-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          Preferences
        </button>
        <button
          onClick={() => setActiveTab('history')}
          className={`flex-1 py-3 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'history'
              ? 'border-primary-600 text-primary-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          History
        </button>
        <button
          onClick={() => setActiveTab('stats')}
          className={`flex-1 py-3 text-sm font-medium border-b-2 transition-colors ${
            activeTab === 'stats'
              ? 'border-primary-600 text-primary-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          Stats
        </button>
      </div>

      {/* PREFERENCES TAB */}
      {activeTab === 'preferences' && (
        <div className="space-y-6">
          {/* Dietary Preferences (Stub) */}
          <section className="card">
            <div className="flex justify-between items-center mb-3">
              <h2 className="font-semibold text-gray-900">Dietary Preferences</h2>
              <span className="text-xs bg-gray-100 text-gray-500 px-2 py-1 rounded">Coming Soon</span>
            </div>
            <p className="text-sm text-gray-600">
              Quick settings for dietary restrictions and preferences (vegetarian, low-carb, allergies, etc.)
            </p>
          </section>

          {/* Taste Profile */}
          <section className="card">
            <div className="flex justify-between items-center mb-3">
              <h2 className="font-semibold text-gray-900">Taste Profile</h2>
              {!isEditingProfile && (
                <button
                  onClick={() => setIsEditingProfile(true)}
                  className="text-sm text-primary-600 hover:text-primary-700"
                >
                  Edit
                </button>
              )}
            </div>

            {isEditingProfile ? (
              <div className={`space-y-4 ${focusedField ? 'pb-48' : ''}`}>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    About Your Taste
                  </label>
                  <textarea
                    value={editedSummary}
                    onChange={(e) => setEditedSummary(e.target.value)}
                    onFocus={(e) => {
                      setFocusedField('summary');
                      setTimeout(() => e.target.scrollIntoView({ behavior: 'smooth', block: 'center' }), 300);
                    }}
                    onBlur={() => setFocusedField(null)}
                    rows={6}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                    placeholder="Describe your food preferences in detail. For example: 'I enjoy bold Asian flavors but prefer milder curries. I dislike raw tomatoes in salads but enjoy them cooked in sauces...'"
                  />
                  <p className="text-xs text-gray-500 mt-1">
                    This helps personalize your meal recommendations
                  </p>
                </div>

                {/* Favorite Ingredients */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Favorite Ingredients
                  </label>
                  <div className="flex flex-wrap gap-2 mb-2">
                    {editedLikes.map(item => (
                      <span
                        key={item}
                        className="inline-flex items-center gap-1 bg-green-100 text-green-800 text-sm px-3 py-1 rounded-full"
                      >
                        {item}
                        <button onClick={() => removeLike(item)} className="text-green-600 hover:text-green-800">×</button>
                      </span>
                    ))}
                  </div>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={newLike}
                      onChange={(e) => setNewLike(e.target.value)}
                      onKeyDown={(e) => e.key === 'Enter' && addLike()}
                      onFocus={(e) => {
                        setFocusedField('likes');
                        setTimeout(() => e.target.scrollIntoView({ behavior: 'smooth', block: 'center' }), 300);
                      }}
                      onBlur={() => setFocusedField(null)}
                      placeholder="Add ingredient..."
                      className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm"
                    />
                    <button onClick={addLike} className="btn btn-secondary text-sm">Add</button>
                  </div>
                </div>

                {/* Ingredients to Avoid */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Ingredients to Avoid
                  </label>
                  <div className="flex flex-wrap gap-2 mb-2">
                    {editedDislikes.map(item => (
                      <span
                        key={item}
                        className="inline-flex items-center gap-1 bg-red-100 text-red-800 text-sm px-3 py-1 rounded-full"
                      >
                        {item}
                        <button onClick={() => removeDislike(item)} className="text-red-600 hover:text-red-800">×</button>
                      </span>
                    ))}
                  </div>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={newDislike}
                      onChange={(e) => setNewDislike(e.target.value)}
                      onKeyDown={(e) => e.key === 'Enter' && addDislike()}
                      onFocus={(e) => {
                        setFocusedField('dislikes');
                        setTimeout(() => e.target.scrollIntoView({ behavior: 'smooth', block: 'center' }), 300);
                      }}
                      onBlur={() => setFocusedField(null)}
                      placeholder="Add ingredient..."
                      className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm"
                    />
                    <button onClick={addDislike} className="btn btn-secondary text-sm">Add</button>
                  </div>
                </div>

                <div className="flex gap-3 pt-2">
                  <button
                    onClick={() => {
                      setIsEditingProfile(false);
                      setEditedSummary(preferenceSummary?.summary ?? '');
                      setEditedLikes(preferenceSummary?.likes ?? []);
                      setEditedDislikes(preferenceSummary?.dislikes ?? []);
                    }}
                    className="flex-1 btn btn-secondary"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleSaveProfile}
                    disabled={isSaving}
                    className="flex-1 btn btn-primary"
                  >
                    {isSaving ? 'Saving...' : 'Save'}
                  </button>
                </div>
              </div>
            ) : (preferenceSummary?.summary || preferenceSummary?.likes?.length || preferenceSummary?.dislikes?.length) ? (
              <div className="space-y-4">
                {preferenceSummary?.summary && (
                  <p className="text-sm text-gray-700 whitespace-pre-wrap">
                    {preferenceSummary.summary.length > 300
                      ? preferenceSummary.summary.slice(0, 300) + '...'
                      : preferenceSummary.summary}
                  </p>
                )}

                {preferenceSummary?.likes && preferenceSummary.likes.length > 0 && (
                  <div>
                    <p className="text-xs font-medium text-gray-500 mb-1">Favorite Ingredients</p>
                    <div className="flex flex-wrap gap-1">
                      {preferenceSummary.likes.slice(0, 8).map(item => (
                        <span key={item} className="bg-green-100 text-green-800 text-xs px-2 py-0.5 rounded">
                          {item}
                        </span>
                      ))}
                      {preferenceSummary.likes.length > 8 && (
                        <span className="text-xs text-gray-500">+{preferenceSummary.likes.length - 8} more</span>
                      )}
                    </div>
                  </div>
                )}

                {preferenceSummary?.dislikes && preferenceSummary.dislikes.length > 0 && (
                  <div>
                    <p className="text-xs font-medium text-gray-500 mb-1">Ingredients to Avoid</p>
                    <div className="flex flex-wrap gap-1">
                      {preferenceSummary.dislikes.slice(0, 8).map(item => (
                        <span key={item} className="bg-red-100 text-red-800 text-xs px-2 py-0.5 rounded">
                          {item}
                        </span>
                      ))}
                      {preferenceSummary.dislikes.length > 8 && (
                        <span className="text-xs text-gray-500">+{preferenceSummary.dislikes.length - 8} more</span>
                      )}
                    </div>
                  </div>
                )}

                {preferenceSummary.lastUpdated && (
                  <p className="text-xs text-gray-400">
                    Last updated: {preferenceSummary.lastUpdated.toLocaleDateString()}
                  </p>
                )}
              </div>
            ) : (
              <div className="text-center py-4">
                <p className="text-sm text-gray-500 mb-3">
                  No taste profile yet. Rate some recipes or add your preferences manually.
                </p>
                <button
                  onClick={() => setIsEditingProfile(true)}
                  className="btn btn-secondary text-sm"
                >
                  Add Preferences
                </button>
              </div>
            )}
          </section>
        </div>
      )}

      {/* HISTORY TAB */}
      {activeTab === 'history' && (
        <div className="space-y-4">
          {mealPlanHistory && mealPlanHistory.length > 0 ? (
            mealPlanHistory.map(plan => (
              <div key={plan.id} className="card">
                <div className="flex justify-between items-start mb-2">
                  <h3 className="font-medium text-gray-900">
                    Week of {plan.weekOf.toLocaleDateString('en-CA', { month: 'short', day: 'numeric', year: 'numeric' })}
                  </h3>
                  <span className="text-xs text-gray-500">
                    {plan.recipes.filter(r => r.cooked).length}/{plan.recipes.length} cooked
                  </span>
                </div>
                <div className="space-y-1">
                  {plan.recipeNames.map((name, i) => {
                    const cooked = plan.recipes[i]?.cooked;
                    return (
                      <div
                        key={i}
                        className={`text-sm flex items-center gap-2 ${cooked ? 'text-gray-400' : 'text-gray-700'}`}
                      >
                        <span className={`w-4 h-4 rounded-full flex items-center justify-center text-xs ${cooked ? 'bg-green-100 text-green-600' : 'bg-gray-100 text-gray-400'}`}>
                          {cooked ? '✓' : '○'}
                        </span>
                        <span className={cooked ? 'line-through' : ''}>{name}</span>
                      </div>
                    );
                  })}
                </div>
              </div>
            ))
          ) : (
            <div className="text-center py-12">
              <div className="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <span className="text-3xl">📋</span>
              </div>
              <h2 className="text-lg font-semibold text-gray-900 mb-2">No Meal Plans Yet</h2>
              <p className="text-sm text-gray-600">
                Your meal plan history will appear here after you create your first plan.
              </p>
            </div>
          )}
        </div>
      )}

      {/* STATS TAB */}
      {activeTab === 'stats' && (
        <div className="space-y-6">
          {/* Basic Stats */}
          <div className="grid grid-cols-2 gap-4">
            <div className="card text-center">
              <p className="text-3xl font-bold text-primary-600">{totalCooked}</p>
              <p className="text-sm text-gray-600">Meals Cooked</p>
            </div>
            <div className="card text-center">
              <p className="text-3xl font-bold text-primary-600">{avgRating}</p>
              <p className="text-sm text-gray-600">Avg Rating</p>
            </div>
          </div>

          <div className="card text-center">
            <p className="text-3xl font-bold text-primary-600">{mealPlanHistory?.length ?? 0}</p>
            <p className="text-sm text-gray-600">Meal Plans Created</p>
          </div>

          {/* Placeholder for future stats */}
          <div className="card">
            <h3 className="font-medium text-gray-900 mb-2">More Stats Coming Soon</h3>
            <p className="text-sm text-gray-600">
              We'll be adding: favorite cuisines, cooking time trends, ingredient usage patterns, and more.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
