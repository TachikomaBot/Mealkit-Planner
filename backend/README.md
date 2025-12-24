# Meal Planner Backend API

Node.js/Express backend for the Meal Planner app. Provides recipe search, meal plan generation, and image caching services.

## Setup

```bash
cd backend
npm install

# Copy recipe data from frontend
cp ../public/data/recipes.json data/

# Start development server
npm run dev
```

The server runs on `http://localhost:3001` by default.

## API Endpoints

### Health Check

```
GET /health
```
Returns server status.

### Recipes

```
GET /api/recipes
```
Search recipes with filters:
- `q` - Text search in name/description
- `category` - Filter by category (dinner, lunch, side, soup, salad)
- `cuisines` - Comma-separated cuisine list
- `dietaryFlags` - Comma-separated dietary flags
- `maxTotalTime` - Maximum cooking time in minutes
- `includeIngredients` - Comma-separated ingredients to include
- `excludeIngredients` - Comma-separated ingredients to exclude
- `limit` - Results per page (default 20)
- `offset` - Pagination offset
- `random` - Randomize results (true/false)

```
GET /api/recipes/stats
```
Get category and cuisine statistics.

```
GET /api/recipes/:id
```
Get a specific recipe by ID.

### Meal Plan Generation

```
POST /api/meal-plan/generate
```
Generate a meal plan using Gemini AI.

**Headers:**
- `X-Gemini-Key` - Your Gemini API key (required)
- `Accept: text/event-stream` - For SSE progress streaming

**Body:**
```json
{
  "pantryItems": [{ "name": "chicken", "quantity": 2, "unit": "lb" }],
  "preferences": { "likes": ["italian"], "dislikes": ["fish"] },
  "recentRecipeHashes": []
}
```

**SSE Events:**
- `{ type: "connected" }` - Connection established
- `{ type: "progress", phase, current, total, message }` - Progress update
- `{ type: "complete", result }` - Generation complete with recipes
- `{ type: "error", error }` - Error occurred

```
POST /api/meal-plan/generate-simple
```
Quick generation from dataset without AI (no API key needed).

### Images

```
POST /api/images/find-similar
```
Find a similar cached image by keywords.

```
POST /api/images/generate
```
Generate a new image (placeholder - future Gemini integration).

## Environment Variables

Create `.env` file (see `.env.example`):
- `PORT` - Server port (default 3001)

## Architecture

```
backend/
├── src/
│   ├── index.ts          # Express app entry
│   ├── types.ts          # TypeScript types
│   ├── routes/
│   │   ├── recipes.ts    # Recipe search endpoints
│   │   ├── mealplan.ts   # Meal generation with SSE
│   │   └── images.ts     # Image caching
│   └── services/
│       ├── recipeService.ts  # Recipe data & search
│       └── geminiService.ts  # AI meal generation
├── data/
│   └── recipes.json      # Recipe dataset (copy from frontend)
└── package.json
```
