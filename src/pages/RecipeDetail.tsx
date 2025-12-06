import { useLocation, useNavigate } from 'react-router-dom';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, rateRecipe, getRecipeRating } from '../db';
import type { GeneratedRecipe, CookingStep } from '../api/claude';
import { useState, useEffect } from 'react';
import { generateStepImage } from '../api/gemini';
import { formatQuantity } from '../utils/settings';

interface RecipeDetailState {
  recipe: GeneratedRecipe;
  imageUrl?: string;
  expiringIngredientsUsed?: string[];  // Ingredients that need to be used up
}

export default function RecipeDetail() {
  const location = useLocation();
  const navigate = useNavigate();
  const state = location.state as RecipeDetailState | null;
  const [stepImages, setStepImages] = useState<Map<number, string>>(new Map());
  const [loadingStepImages, setLoadingStepImages] = useState(false);
  const [rating, setRating] = useState<number | null>(null);
  const [wouldMakeAgain, setWouldMakeAgain] = useState<boolean | null>(null);
  const [ratingSaved, setRatingSaved] = useState(false);

  // Scroll to top on mount and load cached step images + ratings
  useEffect(() => {
    window.scrollTo(0, 0);

    // Load any cached step images for this recipe
    if (state?.recipe) {
      loadCachedStepImages(state.recipe.name, state.recipe.steps.length);
      // Load existing rating
      getRecipeRating(state.recipe.name).then(existing => {
        if (existing) {
          setRating(existing.rating);
          setWouldMakeAgain(existing.wouldMakeAgain);
        }
      });
    }
  }, []);

  // Load cached step images from IndexedDB
  const loadCachedStepImages = async (recipeName: string, stepCount: number) => {
    const cached = new Map<number, string>();

    for (let i = 0; i < stepCount; i++) {
      const cacheKey = `step:${recipeName}:${i + 1}`;
      try {
        const entry = await db.imageCache
          .where('recipeName')
          .equals(cacheKey)
          .first();

        if (entry) {
          cached.set(i, entry.imageDataUrl);
        }
      } catch (e) {
        // Ignore cache lookup errors
      }
    }

    if (cached.size > 0) {
      setStepImages(cached);
    }
  };

  // Get pantry ingredients for comparison
  const pantryIngredients = useLiveQuery(() => db.ingredients.toArray());

  if (!state?.recipe) {
    return (
      <div className="p-4 text-center">
        <p className="text-gray-600 mb-4">Recipe not found</p>
        <button onClick={() => navigate(-1)} className="btn btn-primary">
          Go Back
        </button>
      </div>
    );
  }

  const { recipe, imageUrl, expiringIngredientsUsed } = state;
  const totalTime = recipe.prepTimeMinutes + recipe.cookTimeMinutes;

  // Check if an ingredient is one that needs to be used up (expiring)
  const isExpiringIngredient = (ingredientName: string): boolean => {
    if (!expiringIngredientsUsed || expiringIngredientsUsed.length === 0) return false;
    const normalized = ingredientName.toLowerCase();
    return expiringIngredientsUsed.some(exp =>
      normalized.includes(exp.toLowerCase()) ||
      exp.toLowerCase().includes(normalized)
    );
  };

  // Check if an ingredient is in the pantry and its status
  const getPantryStatus = (ingredientName: string): { inPantry: boolean; isLow: boolean } => {
    if (!pantryIngredients) return { inPantry: false, isLow: false };
    const normalized = ingredientName.toLowerCase();
    const match = pantryIngredients.find(p =>
      p.name.toLowerCase().includes(normalized) ||
      normalized.includes(p.name.toLowerCase())
    );
    if (!match) return { inPantry: false, isLow: false };
    // Consider "low" if less than 25% remaining
    const percentRemaining = (match.quantityRemaining / match.quantityInitial) * 100;
    return { inPantry: true, isLow: percentRemaining < 25 };
  };

  // Generate step images with process-focused prompts
  const handleGenerateStepImages = async () => {
    setLoadingStepImages(true);
    const newImages = new Map<number, string>(stepImages); // Keep any already cached

    for (let i = 0; i < recipe.steps.length; i++) {
      // Skip if already have this image
      if (newImages.has(i)) continue;

      const step = recipe.steps[i];
      try {
        // Use the new step-specific image generator
        const url = await generateStepImage(
          recipe.name,
          step.title,
          step.substeps,
          i + 1, // 1-indexed step number
          recipe.steps.length
        );
        if (url) {
          newImages.set(i, url);
          setStepImages(new Map(newImages));
        }
      } catch (e) {
        console.error(`Failed to generate image for step ${i}:`, e);
      }
    }

    setLoadingStepImages(false);
  };

  // Save rating
  const handleSaveRating = async (newRating: number | null, newWouldMakeAgain: boolean | null) => {
    if (!recipe) return;
    setRating(newRating);
    setWouldMakeAgain(newWouldMakeAgain);
    await rateRecipe(recipe.name, newRating, newWouldMakeAgain);
    setRatingSaved(true);
    setTimeout(() => setRatingSaved(false), 1500);
  };

  return (
    <div className="pb-20">
      {/* Header - sticky title bar */}
      <div className="sticky top-0 bg-white border-b z-10">
        <div className="p-4">
          <h1 className="font-semibold text-lg truncate">{recipe.name}</h1>
        </div>
      </div>

      {/* Hero image */}
      {imageUrl && (
        <div className="w-full h-64 bg-gray-100">
          <img
            src={imageUrl}
            alt={recipe.name}
            className="w-full h-full object-cover"
          />
        </div>
      )}

      {/* Recipe info */}
      <div className="p-4">
        <p className="text-gray-600 mb-4">{recipe.description}</p>

        {/* Time and servings */}
        <div className="flex gap-4 mb-6">
          <div className="flex items-center gap-2 text-sm text-gray-600">
            <span>⏱</span>
            <span>{totalTime} min</span>
          </div>
          <div className="flex items-center gap-2 text-sm text-gray-600">
            <span>🍽</span>
            <span>{recipe.servings} servings</span>
          </div>
          {recipe.prepTimeMinutes > 0 && (
            <div className="text-sm text-gray-500">
              ({recipe.prepTimeMinutes} prep + {recipe.cookTimeMinutes} cook)
            </div>
          )}
        </div>

        {/* Tags */}
        {recipe.tags.length > 0 && (
          <div className="flex flex-wrap gap-2 mb-6">
            {recipe.tags.map(tag => (
              <span
                key={tag}
                className="px-2 py-1 bg-gray-100 rounded-full text-xs text-gray-600"
              >
                {tag}
              </span>
            ))}
          </div>
        )}

        {/* Ingredients */}
        <section className="mb-8">
          <h2 className="font-semibold text-lg mb-3 flex items-center gap-2">
            <span>🥬</span> Ingredients
          </h2>
          {/* Expiring ingredients banner */}
          {expiringIngredientsUsed && expiringIngredientsUsed.length > 0 && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 mb-3 flex items-start gap-2">
              <span className="text-amber-500">🥬</span>
              <div>
                <p className="text-sm font-medium text-amber-800">Use it up!</p>
                <p className="text-xs text-amber-700">
                  This recipe uses: {expiringIngredientsUsed.join(', ')}
                </p>
              </div>
            </div>
          )}
          <div className="bg-gray-50 rounded-xl p-4">
            <ul className="space-y-2">
              {recipe.ingredients.map((ing, i) => {
                const { inPantry, isLow } = getPantryStatus(ing.ingredientName);
                const isExpiring = isExpiringIngredient(ing.ingredientName);
                return (
                  <li
                    key={i}
                    className={`flex items-start gap-2 ${isExpiring ? 'bg-amber-50 -mx-2 px-2 py-1 rounded-lg' : ''}`}
                  >
                    <span className={`mt-1 ${isExpiring ? 'text-amber-500' : inPantry ? 'text-green-400' : 'text-gray-400'}`}>
                      {isExpiring ? '⚡' : inPantry ? '✓' : '•'}
                    </span>
                    <span className={`${inPantry && !isExpiring ? 'line-through text-gray-400' : isExpiring ? 'text-amber-900 font-medium' : 'text-gray-800'}`}>
                      <strong>{formatQuantity(ing.quantity)} {ing.unit}</strong> {ing.ingredientName}
                      {ing.preparation && (
                        <span className={inPantry && !isExpiring ? 'text-gray-400' : isExpiring ? 'text-amber-700' : 'text-gray-500'}>, {ing.preparation}</span>
                      )}
                    </span>
                    {isExpiring && (
                      <span className="ml-auto text-xs text-amber-600 font-medium whitespace-nowrap">
                        use up
                      </span>
                    )}
                    {inPantry && isLow && !isExpiring && (
                      <span className="ml-1 text-xs text-amber-500 font-medium">
                        (low)
                      </span>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        </section>

        {/* Cooking Steps */}
        <section>
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-semibold text-lg flex items-center gap-2">
              <span>🔥</span> Instructions
            </h2>
            {!loadingStepImages && stepImages.size === 0 && (
              <button
                onClick={handleGenerateStepImages}
                className="text-sm text-primary-600 hover:text-primary-700"
              >
                Generate step images
              </button>
            )}
            {loadingStepImages && (
              <span className="text-sm text-gray-500">
                Generating images...
              </span>
            )}
          </div>

          <div className="space-y-6">
            {recipe.steps.map((step, stepIndex) => (
              <StepCard
                key={stepIndex}
                step={step}
                stepNumber={stepIndex + 1}
                imageUrl={stepImages.get(stepIndex)}
              />
            ))}
          </div>
        </section>

        {/* Rating Section */}
        <section className="mt-8 mb-4">
          <div className="bg-gray-50 rounded-xl p-4">
            <h3 className="font-semibold text-gray-900 mb-3">Rate this recipe</h3>

            {/* Star Rating */}
            <div className="flex items-center gap-1 mb-4">
              {[1, 2, 3, 4, 5].map((star) => (
                <button
                  key={star}
                  onClick={() => handleSaveRating(rating === star ? null : star, wouldMakeAgain)}
                  className="p-1 transition-transform hover:scale-110"
                >
                  <span className={`text-2xl ${rating && star <= rating ? 'text-mustard-400' : 'text-gray-300'}`}>
                    ★
                  </span>
                </button>
              ))}
              {rating && (
                <span className="ml-2 text-sm text-gray-500">
                  {rating === 5 ? 'Amazing!' : rating === 4 ? 'Great' : rating === 3 ? 'Good' : rating === 2 ? 'Okay' : 'Not for me'}
                </span>
              )}
            </div>

            {/* Would Make Again */}
            <div className="flex items-center gap-3">
              <span className="text-sm text-gray-600">Would make again?</span>
              <div className="flex gap-2">
                <button
                  onClick={() => handleSaveRating(rating, wouldMakeAgain === true ? null : true)}
                  className={`px-3 py-1 rounded-full text-sm font-medium transition-colors ${
                    wouldMakeAgain === true
                      ? 'bg-green-100 text-green-700 ring-2 ring-green-500'
                      : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}
                >
                  Yes
                </button>
                <button
                  onClick={() => handleSaveRating(rating, wouldMakeAgain === false ? null : false)}
                  className={`px-3 py-1 rounded-full text-sm font-medium transition-colors ${
                    wouldMakeAgain === false
                      ? 'bg-red-100 text-red-700 ring-2 ring-red-500'
                      : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}
                >
                  No
                </button>
              </div>
              {ratingSaved && (
                <span className="ml-auto text-sm text-green-600">Saved!</span>
              )}
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}

function StepCard({
  step,
  stepNumber,
  imageUrl,
}: {
  step: CookingStep;
  stepNumber: number;
  imageUrl?: string;
}) {
  return (
    <div className="bg-white rounded-xl border overflow-hidden">
      {/* Step image */}
      {imageUrl && (
        <div className="w-full h-40 bg-gray-100">
          <img
            src={imageUrl}
            alt={step.title}
            className="w-full h-full object-cover"
          />
        </div>
      )}

      {/* Step content */}
      <div className="p-4">
        <div className="flex items-center gap-3 mb-3">
          <div className="w-8 h-8 rounded-full bg-primary-100 text-primary-700 flex items-center justify-center font-semibold text-sm">
            {stepNumber}
          </div>
          <h3 className="font-medium text-gray-900">{step.title}</h3>
        </div>

        <ol className="space-y-2 ml-11">
          {step.substeps.map((substep, i) => (
            <li key={i} className="text-gray-600 text-sm flex gap-2">
              <span className="text-gray-400 shrink-0">{i + 1}.</span>
              <span>{substep}</span>
            </li>
          ))}
        </ol>
      </div>
    </div>
  );
}
