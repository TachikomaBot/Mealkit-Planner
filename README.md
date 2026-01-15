# Meal Planner App

> Native Android meal planning app with AI-powered recipe generation.

## Overview

A meal planning app for a **single user** on **Android**. The user selects 6 dinner recipes from AI-generated suggestions each week, generating a shopping list. After shopping, ingredients are added to the pantry. When a recipe is cooked, ingredients are auto-deducted from pantry.

**Architecture:** Native Kotlin (Jetpack Compose) + Node.js backend on Railway

---

## Project Structure

```
Mealkit-Planner/
├── android-app/          # Native Android app (Kotlin + Jetpack Compose)
├── backend/              # Node.js/Express API (hosted on Railway)
├── public/data/          # Recipe dataset JSON (copied to backend)
├── scripts/              # Recipe import scripts
├── Todo.md               # Current feature backlog
└── NATIVE_KOTLIN_PROGRESS.md  # Kotlin rewrite progress tracker
```

---

## Quick Start

### Backend (Railway - Production)

The backend is deployed on Railway and auto-deploys from GitHub:
- **Project:** https://railway.com/project/9d51de57-565f-445b-a06d-49baa2b5faa0

### Backend (Local Development)

```bash
cd backend
npm install
cp ../public/data/recipes.json data/
npm run dev
# Runs on http://localhost:3001
```

### Android App

1. Open `android-app/` in Android Studio
2. Wait for Gradle sync
3. For local backend: App uses `10.0.2.2:3001` (emulator localhost)
4. Run on emulator or device

---

## Architecture

### Android App (`android-app/`)

Native Kotlin with Jetpack Compose, following Clean Architecture:

```
app/src/main/java/com/mealplanner/
├── di/                  # Hilt dependency injection modules
├── data/
│   ├── local/           # Room database, DAOs, entities
│   ├── remote/          # Retrofit APIs, DTOs
│   └── repository/      # Repository implementations
├── domain/              # Models, repository interfaces, use cases
├── presentation/
│   ├── components/      # Reusable UI components
│   ├── theme/           # Compose theme (colors, typography)
│   ├── navigation/      # NavGraph
│   └── screens/         # UI screens
└── service/             # Foreground service for AI generation
```

**Key Technologies:**
- Jetpack Compose (UI)
- Hilt (Dependency Injection)
- Room (Local Database)
- Retrofit (API Client)
- Coil (Image Loading)

### Backend (`backend/`)

Node.js/Express API providing:
- Recipe search from 20K+ Food.com dataset
- AI meal plan generation via Gemini (SSE streaming)
- Image caching (planned)

See [backend/README.md](backend/README.md) for API documentation.

---

## User Flow

```
Friday/Saturday: PLAN
┌────────────────────────────────────────────────────────────────────┐
│  1. User taps "Generate Meal Plan"                                 │
│  2. Backend queries recipe dataset + Gemini AI                     │
│  3. Presents 24 diverse meal options (SSE streaming progress)      │
│  4. User selects 6 favorites                                       │
│  5. Confirms → creates MealPlan + generates ShoppingList           │
└────────────────────────────────────────────────────────────────────┘
         │
         ▼
Weekend: SHOP
┌────────────────────────────────────────────────────────────────────┐
│  1. User views shopping list (organized by store section)          │
│  2. "Shopping Mode" - tap items as found in store                  │
│  3. All items checked → "Complete Shopping Trip"                   │
│  4. Checked items auto-added to Pantry                             │
└────────────────────────────────────────────────────────────────────┘
         │
         ▼
Weeknights: COOK
┌────────────────────────────────────────────────────────────────────┐
│  1. User picks a recipe from meal plan                             │
│  2. Views recipe detail (ingredients + steps)                      │
│  3. Marks recipe as "Cooked"                                       │
│  4. App auto-deducts ingredients from pantry                       │
│  5. User rates recipe (affects future recommendations)             │
│  6. 2 servings = dinner + leftover lunch                           │
└────────────────────────────────────────────────────────────────────┘
```

---

## Database Schema (Room)

| Entity | Purpose |
|--------|---------|
| `PantryItem` | Pantry inventory with quantities, categories, expiry |
| `Recipe` | Saved recipes from selections |
| `MealPlan` | Weekly plans with recipe references |
| `DayPlan` | Individual day within a meal plan |
| `ShoppingItem` | Shopping list items with check state |
| `RecipeHistory` | Cooking history with ratings |
| `UserPreferences` | Taste profile (likes, dislikes) |

---

## Key Screens

| Screen | Purpose |
|--------|---------|
| `HomeScreen` | Dashboard with week overview, quick stats, actions |
| `MealsScreen` | Generate/browse/select recipes, confirm meal plan |
| `ShoppingScreen` | Shopping list with shopping mode for in-store use |
| `PantryScreen` | Inventory management with water-level quantity cards |
| `RecipeDetailScreen` | Full recipe view, mark cooked, rate |
| `ProfileScreen` | Preferences, history, stats (tabbed) |
| `SettingsScreen` | API key config, preferences, test mode |

---

## Key Data Flows

### Shopping → Pantry Sync

When "Done Shopping" is pressed in the Grocery List:

```
MealPlanScreen
    └─> MealPlanViewModel.markShoppingComplete()
        └─> ManageShoppingListUseCase.completeShoppingTrip(mealPlanId)
            ├─> ShoppingRepository.getCheckedItems() - get all checked items
            ├─> Map to PantryItems (with unit/category conversion)
            ├─> PantryRepository.addFromShoppingList() - insert to pantry
            └─> ShoppingRepository.resetAllItems() - uncheck all items
        └─> MealPlanRepository.markShoppingComplete() - set flag
        └─> Show confirmation dialog with item count
```

**Key files:**
- `ManageShoppingListUseCase.kt` - orchestrates the pantry sync logic
- `MealPlanViewModel.kt` - calls use case, manages completion state
- `MealPlanScreen.kt` - shows completion dialog

### Cooking → Pantry Deduction

When a recipe is marked as "Cooked":

```
RecipeDetailScreen
    └─> RecipeDetailViewModel.markCooked()
        └─> For each ingredient:
            └─> PantryRepository.deductByName(name, amount)
        └─> MealPlanRepository.markRecipeCooked()
        └─> RecipeHistoryRepository.recordCooking()
```

### Test Mode

Test Mode provides data isolation for testing pantry sync:

```
SettingsScreen
    └─> SettingsViewModel.enableTestMode()
        └─> TestModeUseCase.enableTestMode()
            ├─> clearAllData() - clears pantry, shopping, meal plans
            └─> DataStore: test_mode_enabled = true

MainScreen shows "TEST MODE" banner when enabled
```

---

## Current Status

See [NATIVE_KOTLIN_PROGRESS.md](NATIVE_KOTLIN_PROGRESS.md) for detailed progress.

**Completed:**
- Native Kotlin app with all core screens
- Backend with recipe search and AI generation
- Foreground service for background generation
- Room database with full schema
- Shopping → Pantry flow
- Recipe rating and history

**In Progress (see Todo.md):**
- Pantry sync improvements (confirmation screens before add/deduct)
- Ingredient substitution
- Recipe units refinement

---

## API Keys

| Service | Purpose | Required? |
|---------|---------|-----------|
| Gemini | AI meal generation, recipe images | Yes (for AI features) |

Configure in Settings screen. Without Gemini:
- Can still browse/search recipes manually
- No AI-powered meal plan generation

---

## Development Notes

1. **Backend must be running** for meal generation. Either:
   - Use Railway deployment (production)
   - Run locally with `npm run dev` in `backend/`

2. **Recipe dataset** - 20K+ recipes from Food.com, stored in `backend/data/recipes.json`

3. **Emulator networking** - Android emulator uses `10.0.2.2` to reach host localhost

4. **Foreground service** - AI generation runs in a foreground service so it continues when app is backgrounded

---

## Build & Release

```bash
# Debug build
cd android-app
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease
```

APK output: `android-app/app/build/outputs/apk/`
