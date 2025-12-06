# Meal Kit App - Architecture Document

> Internal reference for AI assistants and developers working on this codebase.

## Overview

A meal planning app for a **single user** on **Android** (sideloaded APK). The user selects 6 dinner recipes from a curated pool each week, generating a shopping list. After shopping, ingredients are added to the pantry. When a recipe is cooked, ingredients are auto-deducted from pantry.

**Target: 1.0 Release** - Focused, minimal feature set.

---

## User Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           WEEKLY CYCLE                                   │
└─────────────────────────────────────────────────────────────────────────┘

  Friday/Saturday: PLAN
  ┌────────────────────────────────────────────────────────────────────┐
  │  1. User taps "Generate Meal Plan"                                 │
  │  2. App queries recipe dataset (5000+ recipes in IndexedDB)        │
  │  3. Scores recipes by: pantry matches, learned preferences,        │
  │     cuisine/protein diversity, avoiding recent recipes             │
  │  4. Presents 24 diverse options                                    │
  │  5. User selects 6 favorites                                       │
  │  6. (Optional) Gemini generates recipe images                      │
  │  7. User confirms → creates MealPlan + ShoppingList                │
  └────────────────────────────────────────────────────────────────────┘
           │
           ▼
  Weekend: SHOP
  ┌────────────────────────────────────────────────────────────────────┐
  │  1. User views shopping list (organized by store section)          │
  │  2. "Start Shopping" mode - tap items as found                     │
  │  3. All items checked → "Complete Shopping Trip"                   │
  │  4. Checked items auto-added to Pantry with default expiry dates   │
  │  5. Shopping list cleared                                          │
  └────────────────────────────────────────────────────────────────────┘
           │
           ▼
  Weeknights: COOK
  ┌────────────────────────────────────────────────────────────────────┐
  │  1. User picks a recipe from meal plan (any day, no schedule)      │
  │  2. Views recipe detail (ingredients + steps)                      │
  │  3. Marks recipe as "Cooked"                                       │
  │  4. App auto-deducts ingredients from pantry                       │
  │  5. User can rate recipe (affects future recommendations)          │
  │  6. 2 servings = 1 meal + 1 leftover (12 meals from 6 recipes)     │
  └────────────────────────────────────────────────────────────────────┘
```

---

## Architecture Layers

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              UI (React)                                  │
│  pages/          Plan, Shop, Pantry, Home, RecipeDetail, Settings       │
│  components/     Layout, SearchBar, ApiKeyModal                         │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           Services Layer                                 │
│  services/recipeData.ts    Recipe dataset queries, pool generation      │
│  api/claude.ts             LLM API (currently unused for recipes)       │
│  api/gemini.ts             Image generation for recipe cards            │
│  native/index.ts           Capacitor wrappers (notifications, etc.)     │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Database (Dexie/IndexedDB)                       │
│  db/index.ts               Schema, CRUD helpers, shopping list logic    │
│  db/types.ts               TypeScript interfaces                        │
│  db/ingredients.ts         Ingredient normalization (250+ mappings)     │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Static Data (public/)                            │
│  public/data/recipes-dinner.json   5000 dinner recipes (~17MB)          │
│  Loaded into IndexedDB on first app open                                │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Database Schema (Dexie v4)

| Table             | Purpose                                           | Key Fields |
|-------------------|---------------------------------------------------|------------|
| `ingredients`     | Pantry inventory                                  | name, quantity, category, expiryDate, lastStockCheck |
| `recipes`         | User's saved recipes (from selections)            | name, ingredients[], steps[], tags[] |
| `recipeHistory`   | Cooking history + ratings                         | recipeName, rating, wouldMakeAgain |
| `mealPlans`       | Weekly plans                                      | weekOf, recipes[] |
| `shoppingList`    | Current shopping list items                       | ingredientName, quantity, checked |
| `recipeDataset`   | Imported Food.com recipes                         | sourceId, name, cuisines[], dietaryFlags[] |
| `imageCache`      | AI-generated recipe images                        | recipeName, imageDataUrl |
| `planSessionState`| Persists recipe pool if user navigates away       | recipePool (JSON), selectedIndices |
| `preferenceSummary`| Compacted taste preferences from rated history   | summary, likes[], dislikes[], entriesProcessed |

---

## Key Files Reference

### Pages (src/pages/)

| File              | Purpose |
|-------------------|---------|
| `Plan.tsx`        | Main workflow: generate pool → select 6 → confirm |
| `Shop.tsx`        | Shopping list with "shopping mode" for in-store use |
| `Pantry.tsx`      | View/manage pantry inventory |
| `RecipeDetail.tsx`| View recipe, mark as cooked, rate |
| `Home.tsx`        | Dashboard with current week status |
| `Settings.tsx`    | API keys, preferences |

### Services (src/services/)

| File                       | Purpose |
|----------------------------|---------|
| `recipeData.ts`            | `generateRecipePoolFromDataset()` - main recipe selection logic |
|                            | `searchRecipes()` - generic recipe queries |
|                            | `datasetToGeneratedRecipe()` - format conversion |
| `shoppingConsolidation.ts` | `consolidateShoppingList()` - Gemini-powered ingredient consolidation |
|                            | Handles unit conversion, pantry subtraction, categorization |

### Database (src/db/)

| File              | Purpose |
|-------------------|---------|
| `index.ts`        | Schema, CRUD functions, shopping list generation |
|                   | `generateShoppingList()` - aggregates recipe ingredients, subtracts pantry |
|                   | `markRecipeCooked()` - updates history, deducts pantry |
| `types.ts`        | Core interfaces (Ingredient, Recipe, MealPlan, etc.) |
| `ingredients.ts`  | `normalizeIngredientName()` - canonical mappings |
|                   | `detectCategory()` - produce/protein/dairy/etc. |

### API Integrations (src/api/)

| File              | Purpose |
|-------------------|---------|
| `gemini.ts`       | Recipe image generation (optional) |
| `claude.ts`       | LLM-based recipe generation (legacy, not used in 1.0) |

### Build Tools (scripts/)

| File                  | Purpose |
|-----------------------|---------|
| `import-recipes.ts`   | Parse Food.com CSV → JSON |
|                       | `npm run import:recipes:dinner` → public/data/recipes-dinner.json |

---

## Recipe Selection Algorithm

Located in `src/services/recipeData.ts`:

```
generateRecipePoolFromDataset(pantryState, recentRecipes, preferences)
│
├── 1. Score all recipes (scoreRecipe)
│   ├── Exclude recipes with avoided ingredients (score = 0)
│   ├── Exclude recently cooked (by name hash)
│   ├── Boost +0.3 for 2-4 pantry matches
│   ├── Boost +0.2 for liked tags/cuisines
│   ├── Penalty -0.3 for disliked tags/cuisines
│   └── Add small random factor (0-0.2)
│
├── 2. Select with diversity constraints (selectDiverseRecipes)
│   ├── Max 6 per cuisine type
│   ├── Max 6 per protein type (chicken/beef/pork/seafood/vegetarian)
│   └── Early picks are strict, later picks are lenient
│
└── 3. Pick default 6 (selectDefaultSix)
    └── Ensure variety in first 4 picks (different proteins/cuisines)
```

---

## Shopping List Generation

Located in `src/services/shoppingConsolidation.ts` (Gemini-powered):

```
consolidateShoppingList(rawIngredients, pantryItems)
│
├── 1. Send to Gemini Flash with structured prompt
│   ├── Raw ingredients from all recipes with recipe names
│   ├── Current pantry state
│   └── User's unit system preference (metric/imperial)
│
├── 2. Gemini intelligently:
│   ├── Consolidates similar ingredients ("100g chopped carrots" + "2 julienned carrots" → "3 medium carrots")
│   ├── Converts to purchasable units (cups → heads, grams → medium)
│   ├── Subtracts pantry items (500g chicken - 400g needed = skip)
│   ├── Categorizes for store layout (Produce, Protein, Dairy, etc.)
│   └── Respects unit system (metric: grams, imperial: pounds)
│
└── 3. Returns structured JSON with name, quantity, unit, category, notes
```

### Manual Shopping Items

Users can add non-food items (paper towels, cleaning supplies) via the Shop page:
- Items with `mealPlanId: 0` are manual items
- Categorized as "Household" by default
- Skipped when adding to pantry after shopping

---

## Check Stock Feature

Located in `src/pages/Pantry.tsx` and `src/utils/scheduling.ts`:

### Pantry Filter

The "Check Stock" pseudo-category filter highlights perishable items that may need verification before meal planning:

```
needsStockCheck(ingredient) → boolean
│
├── Only checks perishables: protein, dairy, produce
│
├── Returns true if ANY of:
│   ├── Expiring within 3 days
│   ├── Added more than 3 days ago (freshness uncertain)
│   └── Partially consumed (quantityRemaining < quantityInitial)
│
└── Displayed with warning badge showing count
```

### Pre-Planning Notification

The evening before scheduled meal generation, the app sends a reminder notification:

```
scheduleCheckStockReminder(settings)
│
├── Scheduled for 6 PM the day before meal generation
│   (e.g., Friday 6 PM if generation is Saturday morning)
│
├── Notification body shows count of items needing check
│
└── Tapping notification opens Pantry with "Check Stock" filter
    └── Deep link via localStorage('pantry_initial_filter')
```

---

## User Settings

Located in `src/utils/settings.ts`:

| Setting | Purpose | Default |
|---------|---------|---------|
| `unitSystem` | Metric (g, kg, mL) or Imperial (oz, lb, cups) | `'metric'` |
| `schedule.enabled` | Enable automatic meal generation | `false` |
| `schedule.dayOfWeek` | Day for meal generation (0=Sun, 6=Sat) | `6` (Saturday) |
| `schedule.timeOfDay` | Hour for generation (24h format) | `9` (9 AM) |
| `imageCacheExpirationDays` | Days before cached images expire | `30` |

Unit system preference is passed to Gemini for shopping list consolidation, ensuring quantities display in user's preferred units.

---

## 1.0 Roadmap

### Current State (Working)

- [x] Recipe dataset import (5000 dinner recipes)
- [x] Dataset-based recipe pool generation
- [x] Recipe selection UI (24 options → pick 6)
- [x] Shopping list generation with unit conversion
- [x] Gemini-powered shopping list consolidation
- [x] Manual shopping items (household goods)
- [x] Shopping mode with check-off
- [x] Complete shopping → add to pantry
- [x] Pantry management UI
- [x] Check Stock filter for perishable verification
- [x] Pre-planning notification (check stock reminder)
- [x] Recipe detail view
- [x] Recipe image generation (Gemini)
- [x] Preference learning from ratings
- [x] Preference history compaction (Gemini summarization)
- [x] Basic ingredient deduction on cook
- [x] Unit system preference (metric/imperial)

### Needs Work (1.0 Blockers)

- [ ] **Ingredient deduction override**: When marking cooked, let user edit quantities before deducting
- [ ] **Recipe filtering**: Surface "quick" vs "weekend" filters more prominently
- [ ] **Onboarding**: First-run flow to set up pantry staples
- [ ] **Capacitor removal** (optional): If native features aren't needed, simplify to PWA

### Nice-to-Have (Post 1.0)

- Specific day scheduling for meals
- Batch cooking / prep recipes
- Multi-week planning
- Recipe URL import
- Sharing shopping list
- Grocery delivery integration

---

## Build & Development

```bash
# Install dependencies
npm install

# Development server
npm run dev

# Import recipe dataset (required for first run)
npm run import:recipes:dinner

# Build for production
npm run build

# Sync to Android
npx cap sync android
```

### Recipe Data Pipeline

```
Food.com CSV (576K recipes)
    │
    ▼ npm run import:recipes:dinner
    │ (filters to "dinner" category, limit 10000)
    │
    ▼ parseIngredientString()
    │ "1 (14 ounce) can diced tomatoes" → {qty: 14, unit: "oz", name: "canned diced tomatoes"}
    │
    ▼ normalizeIngredientName()
    │ "garlic cloves" → "garlic"
    │
    ▼ public/data/recipes-dinner.json (5000 recipes, ~17MB)
    │
    ▼ Loaded into IndexedDB on app open
```

---

## API Keys (Optional)

| Service | Purpose | Required? |
|---------|---------|-----------|
| Gemini  | Recipe images + Shopping list consolidation + Preference summarization | Recommended (fallback logic exists) |

Keys are stored in localStorage and configured via Settings page.

Without Gemini key:
- Recipe images show placeholder icons
- Shopping list uses basic aggregation without intelligent consolidation
- Preference history is not compacted (continues to grow)

---

## File Structure

```
d:\Shopping App\
├── public/
│   └── data/
│       └── recipes-dinner.json    # Recipe dataset
├── scripts/
│   └── import-recipes.ts          # CSV → JSON converter
├── src/
│   ├── api/
│   │   ├── claude.ts              # LLM (legacy)
│   │   └── gemini.ts              # Image generation
│   ├── components/
│   │   ├── Layout.tsx             # Bottom nav
│   │   └── SearchBar.tsx
│   ├── db/
│   │   ├── index.ts               # Database + helpers
│   │   ├── types.ts               # TypeScript interfaces
│   │   └── ingredients.ts         # Normalization rules
│   ├── native/
│   │   └── index.ts               # Capacitor wrappers
│   ├── pages/
│   │   ├── Plan.tsx               # Main workflow
│   │   ├── Shop.tsx               # Shopping list
│   │   ├── Pantry.tsx             # Inventory
│   │   ├── RecipeDetail.tsx       # Recipe view + cook
│   │   └── ...
│   ├── services/
│   │   ├── recipeData.ts          # Recipe queries
│   │   └── shoppingConsolidation.ts # Gemini shopping list
│   └── utils/
│       ├── settings.ts            # Local settings
│       └── scheduling.ts          # Background tasks
├── android/                        # Capacitor Android project
├── package.json
├── vite.config.ts
└── ARCHITECTURE.md                 # This file
```

---

## Notes for AI Assistants

1. **Recipe generation is now database-based**, not LLM-based. The `generateRecipePoolPhased()` in `claude.ts` is legacy code.

2. **Shopping list consolidation uses Gemini** (`src/services/shoppingConsolidation.ts`). The old `generateShoppingList()` in `db/index.ts` is a fallback when no API key is configured. Gemini handles:
   - Intelligent ingredient consolidation (combining "diced carrots" + "julienned carrots")
   - Unit conversion to purchasable quantities
   - Pantry subtraction with fuzzy matching
   - Store section categorization

3. **Ingredient normalization happens in two places**:
   - Import time: `scripts/import-recipes.ts` uses `db/ingredients.ts`
   - Shopping list: Gemini handles normalization during consolidation

4. **The Capacitor setup is for Android only**. There's no iOS or web deployment target. If Capacitor adds complexity without benefit, it could be removed in favor of a simpler WebView wrapper or PWA.

5. **Recipe images are optional**. The Gemini API generates them, but the app works fine with placeholder icons if no API key is set.

6. **Check Stock notifications** require Capacitor's LocalNotifications plugin. They're scheduled via `scheduleCheckStockReminder()` in `scheduling.ts` when the user enables automatic meal generation in Settings.

7. **Preference history compaction** uses Gemini Flash to summarize old recipe ratings into a prose summary with explicit likes/dislikes lists. Triggered automatically when `recipeHistory` exceeds 50 entries. The 20 most recent entries are kept for granular scoring; older entries are compacted into `preferenceSummary`.
