import { Link, useNavigate } from 'react-router-dom';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, type Recipe } from '../db';
import { getGeminiKey } from '../api/gemini';
import { getNextWeekStart } from '../utils/settings';

export default function Home() {
  const navigate = useNavigate();
  const geminiKeyConfigured = !!getGeminiKey();

  const ingredients = useLiveQuery(() => db.ingredients.count());
  const recipes = useLiveQuery(() => db.recipes.count());

  // Get the meal plan for the current/upcoming week
  const mealPlan = useLiveQuery(() => {
    const weekStart = getNextWeekStart(new Date());
    return db.mealPlans.where('weekOf').equals(weekStart).first();
  });

  // Get the actual recipe details for the meal plan
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

  const plannedCount = mealPlan?.recipes.length ?? 0;
  const cookedCount = mealPlan?.recipes.filter(r => r.cooked).length ?? 0;

  // Navigate to recipe detail
  const openRecipe = (recipe: Recipe) => {
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

  return (
    <div className="p-4 max-w-md mx-auto">
      <header className="flex justify-between items-start mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Meal Planner</h1>
          <p className="text-gray-600 mt-1">
            Week of {getNextWeekStart(new Date()).toLocaleDateString('en-CA', { month: 'short', day: 'numeric' })}
          </p>
        </div>
        <Link
          to="/settings"
          className="p-2 rounded-lg hover:bg-gray-100"
          title="Settings"
        >
          <svg className="w-6 h-6 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
          </svg>
        </Link>
      </header>

      {/* API Key Status */}
      {!geminiKeyConfigured && (
        <Link
          to="/settings"
          className="block mb-6 p-4 bg-amber-50 border border-amber-200 rounded-lg hover:bg-amber-100 transition-colors"
        >
          <p className="text-sm text-amber-800">
            <strong>Setup recommended:</strong> Add your Gemini API key for recipe images and smart shopping lists.
          </p>
        </Link>
      )}

      {/* This Week's Meals */}
      <section className="mb-6">
        <div className="flex justify-between items-center mb-3">
          <h2 className="font-semibold text-gray-900">This Week</h2>
          {plannedCount > 0 && (
            <span className="text-sm text-gray-500">
              {cookedCount}/{plannedCount} cooked
            </span>
          )}
        </div>

        {plannedCount > 0 && plannedRecipeDetails ? (
          <div className="space-y-2">
            {/* Progress bar */}
            <div className="w-full bg-gray-200 rounded-full h-2 mb-4">
              <div
                className="bg-primary-600 h-2 rounded-full transition-all"
                style={{ width: `${(cookedCount / plannedCount) * 100}%` }}
              />
            </div>

            {/* Recipe list */}
            {plannedRecipeDetails.map((recipe) => (
              <button
                key={recipe.id}
                onClick={() => openRecipe(recipe)}
                className={`w-full text-left p-3 rounded-xl border transition-all ${
                  recipe.cooked
                    ? 'bg-gray-50 border-gray-200 opacity-60'
                    : 'bg-white border-gray-200 hover:border-primary-300 hover:shadow-sm'
                }`}
              >
                <div className="flex items-center gap-3">
                  <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm ${
                    recipe.cooked
                      ? 'bg-green-100 text-green-600'
                      : 'bg-primary-100 text-primary-600'
                  }`}>
                    {recipe.cooked ? '✓' : '🍽'}
                  </div>
                  <div className="flex-1 min-w-0">
                    <h3 className={`font-medium truncate ${recipe.cooked ? 'text-gray-500 line-through' : 'text-gray-900'}`}>
                      {recipe.name}
                    </h3>
                    <p className="text-xs text-gray-500">
                      {recipe.prepTimeMinutes + recipe.cookTimeMinutes} min
                    </p>
                  </div>
                  <span className="text-gray-400">›</span>
                </div>
              </button>
            ))}
          </div>
        ) : (
          <div className="card text-center py-6">
            <div className="w-12 h-12 bg-primary-100 rounded-full flex items-center justify-center mx-auto mb-3">
              <span className="text-2xl">📅</span>
            </div>
            <p className="text-gray-600 text-sm mb-3">
              No meal plan for this week yet
            </p>
            <button
              onClick={() => navigate('/meals')}
              className="btn btn-primary text-sm"
            >
              Create Plan
            </button>
          </div>
        )}
      </section>

      {/* Quick stats */}
      <div className="grid grid-cols-2 gap-4 mb-6">
        <Link to="/pantry" className="card hover:shadow-md transition-shadow">
          <p className="text-3xl font-bold text-primary-600">{ingredients ?? 0}</p>
          <p className="text-sm text-gray-600">Pantry items</p>
        </Link>
        <Link to="/search" className="card hover:shadow-md transition-shadow">
          <p className="text-3xl font-bold text-primary-600">{recipes ?? 0}</p>
          <p className="text-sm text-gray-600">Saved recipes</p>
        </Link>
      </div>

      {/* Quick actions */}
      <section>
        <h2 className="font-semibold text-gray-900 mb-3">Quick Actions</h2>
        <div className="space-y-2">
          <button
            onClick={() => navigate('/meals')}
            className="w-full btn btn-primary text-left flex items-center gap-3"
          >
            <span>📝</span>
            <span>{plannedCount > 0 ? 'View Meals' : 'Plan This Week\'s Meals'}</span>
          </button>
          <button
            onClick={() => navigate('/pantry')}
            className="w-full btn btn-secondary text-left flex items-center gap-3"
          >
            <span>📷</span>
            <span>Add Items to Pantry</span>
          </button>
          <button
            onClick={() => navigate('/profile')}
            className="w-full btn btn-secondary text-left flex items-center gap-3"
          >
            <span>👤</span>
            <span>View Profile & History</span>
          </button>
        </div>
      </section>
    </div>
  );
}
