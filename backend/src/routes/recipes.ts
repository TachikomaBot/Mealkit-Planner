import { Router } from 'express';
import {
  loadRecipes,
  searchRecipes,
  getRecipeById,
  getRecipeBySourceId,
  getRecipeCount,
  getCategoryStats,
  getCuisineStats,
} from '../services/recipeService.js';

const router = Router();

// Ensure recipes are loaded on first request
let initialized = false;
router.use((_req, _res, next) => {
  if (!initialized) {
    loadRecipes();
    initialized = true;
  }
  next();
});

/**
 * GET /api/recipes
 * Search recipes with filters
 */
router.get('/', (req, res) => {
  const {
    category,
    cuisines,
    dietaryFlags,
    maxTotalTime,
    servingsMin,
    servingsMax,
    includeIngredients,
    excludeIngredients,
    q,
    limit,
    offset,
    random,
  } = req.query;

  const results = searchRecipes({
    category: category as string | undefined,
    cuisines: cuisines ? (cuisines as string).split(',') : undefined,
    dietaryFlags: dietaryFlags ? (dietaryFlags as string).split(',') : undefined,
    maxTotalTime: maxTotalTime ? parseInt(maxTotalTime as string) : undefined,
    servings: (servingsMin || servingsMax) ? {
      min: servingsMin ? parseInt(servingsMin as string) : undefined,
      max: servingsMax ? parseInt(servingsMax as string) : undefined,
    } : undefined,
    includeIngredients: includeIngredients ? (includeIngredients as string).split(',') : undefined,
    excludeIngredients: excludeIngredients ? (excludeIngredients as string).split(',') : undefined,
    searchText: q as string | undefined,
    limit: limit ? parseInt(limit as string) : 20,
    offset: offset ? parseInt(offset as string) : 0,
    random: random === 'true',
  });

  res.json({
    count: results.length,
    total: getRecipeCount(),
    results: results.map(r => ({
      ...r.recipe,
      score: r.score,
      matchedIngredients: r.matchedIngredients,
    })),
  });
});

/**
 * GET /api/recipes/stats
 * Get category and cuisine statistics
 */
router.get('/stats', (_req, res) => {
  res.json({
    total: getRecipeCount(),
    categories: getCategoryStats(),
    cuisines: getCuisineStats(),
  });
});

/**
 * GET /api/recipes/:id
 * Get a specific recipe by ID
 */
router.get('/:id', (req, res) => {
  const id = parseInt(req.params.id);
  const recipe = getRecipeById(id);

  if (!recipe) {
    return res.status(404).json({ error: 'Recipe not found' });
  }

  res.json(recipe);
});

/**
 * GET /api/recipes/source/:sourceId
 * Get a recipe by Food.com source ID
 */
router.get('/source/:sourceId', (req, res) => {
  const sourceId = parseInt(req.params.sourceId);
  const recipe = getRecipeBySourceId(sourceId);

  if (!recipe) {
    return res.status(404).json({ error: 'Recipe not found' });
  }

  res.json(recipe);
});

export default router;
