# Native Kotlin Rewrite - Progress Tracker

## Phase 1: Backend API ✅

- [x] Set up Express server with TypeScript
- [x] Create project structure (routes, services, types)
- [x] Import recipe dataset (20K recipes)
- [x] Implement `/api/recipes` search endpoint
- [x] Implement `/api/recipes/stats` endpoint
- [x] Port `mealArchitect.ts` to `geminiService.ts`
- [x] Add SSE streaming for progress updates
- [x] Create `/api/meal-plan/generate` endpoint
- [x] Create image caching service stub
- [x] Test all endpoints

## Phase 2: Android Project Setup ✅

- [x] Create new Android project with Jetpack Compose
- [x] Configure Gradle with required dependencies (Hilt, Room, Retrofit, Coil, etc.)
- [x] Set up Hilt dependency injection modules (AppModule, NetworkModule, DatabaseModule)
- [x] Configure Room database with entities and DAOs
- [x] Set up Retrofit client for API calls
- [x] Create base Application class
- [x] Create Compose theme (colors, typography)
- [x] Create basic navigation graph with placeholder screens

## Phase 3: Core Domain Layer ✅

- [x] Define domain models (Recipe, MealPlan, DayPlan, etc.)
- [x] Create repository interfaces (contracts)
- [x] Implement use cases:
  - [x] GenerateMealPlanUseCase
  - [x] SearchRecipesUseCase
  - [x] ManageShoppingListUseCase
  - [x] ManagePreferencesUseCase

## Phase 4: Data Layer ✅

- [x] Implement Room DAOs:
  - [x] MealPlanDao
  - [x] ShoppingDao
  - [x] PreferencesDao
- [x] Create API DTOs and mappers
- [x] Implement repository classes:
  - [x] RecipeRepositoryImpl
  - [x] MealPlanRepositoryImpl
  - [x] ShoppingRepositoryImpl
  - [x] UserRepositoryImpl (local-only, Firebase-ready)
- [x] Create RepositoryModule for Hilt bindings

## Phase 5: UI Screens ✅

- [x] Set up navigation (NavGraph)
- [x] Create theme (colors, typography)
- [x] Home screen (basic)
- [x] Build reusable components:
  - [x] RecipeCard
  - [x] MealSlot
  - [x] ShoppingListItem
  - [x] LoadingIndicator (with GenerationProgressIndicator)
- [x] Implement screens:
  - [x] Meal plan screen with generation UI
  - [x] Shopping list screen
  - [x] Recipe detail screen
  - [x] Profile/preferences screen

## Phase 5b: UI/UX Alignment (Match Capacitor App)

### Navigation ✅
- [x] Replace card-based home with bottom tab navigation
- [x] Add 4 tabs: Home, Pantry, Meals, Profile
- [x] Update NavGraph for tab-based routing (MainScreen.kt)
- [x] Create Settings screen (separate from Profile)
- [x] Create Pantry screen placeholder

### Home Screen (Dashboard) ✅
- [x] Week display header with settings icon
- [x] Setup banner (if API key not configured)
- [x] "This Week's Meals" section with progress bar
- [x] Planned recipe list with cooked toggle
- [x] Quick Stats grid (pantry items, saved recipes)
- [x] Quick Action buttons
- [x] HomeViewModel for data management

### Pantry Screen (NEW) ✅
- [x] Add Pantry domain model and repository
- [x] Create PantryDao and entities
- [x] Filter carousel (All, Check Stock, categories)
- [x] Ingredient grid with water-level cards
- [x] Quantity adjuster with slider (tap-to-expand)
- [x] Add ingredient modal
- [x] Low stock indicators

### Meals Screen (Redesign) ✅
- [x] Three-state interface (no plan → browsing → confirmed)
- [x] Filter carousel for recipe categories
- [x] Visual recipe cards with selection checkbox
- [x] Selection counter in confirm button (X/6)
- [x] Confirmed plan view with cook progress
- [x] Browse & pick recipes mode
- [x] AI generation and simple generation options

### Shopping Screen (Enhance) ✅
- [x] Shopping mode toggle (optimized for in-store use)
- [x] Add item modal dialog
- [x] Complete shopping trip flow
- [x] Integration with Pantry (checked items → inventory)
- [x] Categories in fixed order
- [x] Delete item functionality

### Profile Screen (Tabs) ✅
- [x] Tab navigation: Preferences, History, Stats
- [x] Taste profile edit mode (likes/dislikes chips)
- [x] Meal plan history view
- [x] Stats dashboard with milestones

### Recipe Detail (Polish) ✅
- [x] Match original layout with hero image and gradient overlay
- [x] Ingredient status indicators (in pantry, expiring, low)
- [x] Star rating with "I Made This!" completion flow
- [x] Recipe history tracking for ratings
- [x] Padding/spacing fixes between hero and description
- [x] Step number alignment with headings
- [x] Vertical lines extending through all sub-steps
- [ ] Step images support (future)

### Developer Tools ✅
- [x] Sample data loader (creates meal plan, pantry items, shopping list)
- [x] No API key required for testing

## Phase 6: Foreground Service ✅

- [x] Create MealGenerationService (with Hilt DI)
- [x] Implement progress notification (with cancel action)
- [x] Add SSE client for streaming updates (reuses RecipeRepository)
- [x] Handle service lifecycle (start/stop with static state flow)
- [x] Update MealPlanViewModel to use service
- [x] Add cancel button to generation UI

## Phase 7: Image Caching

- [ ] Backend: Implement similarity matching algorithm
- [ ] Backend: Integrate cloud storage (Firebase Storage or S3)
- [ ] Android: Configure Coil for image loading
- [ ] Android: Implement image cache management

## Phase 8: Future - Firebase Auth

- [ ] Add Firebase SDK to Android project
- [ ] Create FirebaseUserRepository implementation
- [ ] Build Google Sign-In UI
- [ ] Implement Firestore sync for user data
- [ ] Add auth state management

---

## Current Status

**Phase 6 Complete** - Foreground Service for meal generation

The app now uses a foreground service for AI meal plan generation, allowing:
- Generation continues even when app is in background
- Progress notification with current phase and progress bar
- Cancel button in notification and in-app UI
- Proper lifecycle handling with state persistence

### Quick Start (Android)
1. Open `android-app/` folder in Android Studio
2. Wait for Gradle sync to complete
3. Start the backend: `cd backend && npm run dev`
4. Run the app on emulator (API base URL uses `10.0.2.2` for localhost)

### Quick Start (Backend)
```bash
cd backend
npm install
cp ../public/data/recipes.json data/
npm run dev
```

### Project Structure
```
android-app/
├── app/src/main/java/com/mealplanner/
│   ├── di/                  # Hilt modules (App, Network, Database, Repository)
│   ├── data/
│   │   ├── local/           # Room database, DAOs, entities
│   │   ├── remote/          # Retrofit APIs, DTOs
│   │   └── repository/      # Repository implementations
│   ├── domain/              # Models, repository interfaces, use cases
│   ├── presentation/
│   │   ├── components/      # Reusable UI components
│   │   ├── theme/           # Compose theme
│   │   ├── navigation/      # NavGraph
│   │   └── screens/         # UI screens (home, mealplan, shopping, profile, recipe)
│   └── service/             # Foreground service (future)
├── build.gradle.kts
└── gradle/libs.versions.toml
```
