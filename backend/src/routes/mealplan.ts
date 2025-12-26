import { Router } from 'express';
import { loadRecipes } from '../services/recipeService.js';
import { generateMealPlan, polishGroceryList } from '../services/geminiService.js';
import {
  createJob,
  getJob,
  startJob,
  updateJobProgress,
  completeJob,
  failJob,
  deleteJob,
  createGroceryPolishJob,
  getGroceryPolishJob,
  startGroceryPolishJob,
  updateGroceryPolishProgress,
  completeGroceryPolishJob,
  failGroceryPolishJob,
  deleteGroceryPolishJob,
} from '../services/jobService.js';
import type { MealPlanRequest, ProgressEvent, GroceryPolishRequest, GroceryPolishProgress } from '../types.js';

const router = Router();

// Ensure recipes are loaded
let initialized = false;
router.use((_req, _res, next) => {
  if (!initialized) {
    loadRecipes();
    initialized = true;
  }
  next();
});

/**
 * POST /api/meal-plan/generate
 * Generate a new meal plan with SSE progress streaming
 *
 * Body:
 * {
 *   pantryItems: [{ name, quantity, unit }],
 *   preferences: { likes: [], dislikes: [], summary: "" } | null,
 *   recentRecipeHashes: string[]
 * }
 *
 * Headers:
 *   X-Gemini-Key: Your Gemini API key
 *   Accept: text/event-stream (for SSE) or application/json
 */
router.post('/generate', async (req, res) => {
  const apiKey = (req.headers['x-gemini-key'] as string) || process.env.GEMINI_API_KEY;

  if (!apiKey) {
    return res.status(401).json({ error: 'Gemini API key required in X-Gemini-Key header or GEMINI_API_KEY env var' });
  }

  const request = req.body as MealPlanRequest;

  if (!request.pantryItems) {
    request.pantryItems = [];
  }
  if (!request.recentRecipeHashes) {
    request.recentRecipeHashes = [];
  }

  // Check if client wants SSE
  const wantsSSE = req.headers.accept?.includes('text/event-stream');

  if (wantsSSE) {
    // Set up SSE headers
    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');
    res.setHeader('X-Accel-Buffering', 'no'); // Disable nginx buffering
    res.flushHeaders();

    // Send initial connection event
    console.log('[SSE] Client connected, sending initial event');
    res.write(`data: ${JSON.stringify({ type: 'connected' })}\n\n`);

    // Progress callback sends SSE events
    const onProgress = (event: ProgressEvent) => {
      console.log(`[SSE] Progress: ${event.phase} - ${event.current}/${event.total} - ${event.message || ''}`);
      res.write(`data: ${JSON.stringify({ type: 'progress', ...event })}\n\n`);
    };

    try {
      const result = await generateMealPlan(apiKey, request, onProgress);

      // Send final result
      console.log(`[SSE] Complete: ${result.recipes.length} recipes generated`);
      res.write(`data: ${JSON.stringify({ type: 'complete', result })}\n\n`);
      res.end();
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      console.error(`[SSE] Error: ${message}`);
      res.write(`data: ${JSON.stringify({ type: 'error', error: message })}\n\n`);
      res.end();
    }
  } else {
    // Regular JSON response
    try {
      const result = await generateMealPlan(apiKey, request);
      res.json(result);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      res.status(500).json({ error: message });
    }
  }
});

/**
 * POST /api/meal-plan/generate-async
 * Start an async meal plan generation job
 * Returns a job ID immediately - poll /jobs/:id for status
 *
 * Body: Same as /generate
 * Headers:
 *   X-Gemini-Key: Your Gemini API key
 *
 * Returns: { jobId: string }
 */
router.post('/generate-async', async (req, res) => {
  const apiKey = (req.headers['x-gemini-key'] as string) || process.env.GEMINI_API_KEY;

  if (!apiKey) {
    return res.status(401).json({ error: 'Gemini API key required in X-Gemini-Key header or GEMINI_API_KEY env var' });
  }

  const request = req.body as MealPlanRequest;

  if (!request.pantryItems) {
    request.pantryItems = [];
  }
  if (!request.recentRecipeHashes) {
    request.recentRecipeHashes = [];
  }

  // Create job and return immediately
  const job = createJob();
  console.log(`[Async] Created job ${job.id}`);

  // Start generation in background
  setImmediate(async () => {
    startJob(job.id);
    console.log(`[Async] Starting job ${job.id}`);

    const onProgress = (event: ProgressEvent) => {
      console.log(`[Async] Job ${job.id} progress: ${event.phase} - ${event.current}/${event.total}`);
      updateJobProgress(job.id, event);
    };

    try {
      const result = await generateMealPlan(apiKey, request, onProgress);
      console.log(`[Async] Job ${job.id} completed with ${result.recipes.length} recipes`);
      completeJob(job.id, result);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      console.error(`[Async] Job ${job.id} failed: ${message}`);
      failJob(job.id, message);
    }
  });

  // Return job ID immediately
  res.json({ jobId: job.id });
});

/**
 * GET /api/meal-plan/jobs/:id
 * Get the status of a meal plan generation job
 *
 * Returns:
 * - status: 'pending' | 'running' | 'completed' | 'failed'
 * - progress: { phase, current, total, message } (if running)
 * - result: MealPlanResponse (if completed)
 * - error: string (if failed)
 */
router.get('/jobs/:id', (req, res) => {
  const job = getJob(req.params.id);

  if (!job) {
    return res.status(404).json({ error: 'Job not found or expired' });
  }

  // Return job status without dates (not needed by client)
  res.json({
    id: job.id,
    status: job.status,
    progress: job.progress,
    result: job.result,
    error: job.error,
  });
});

/**
 * DELETE /api/meal-plan/jobs/:id
 * Delete a job after retrieving the result
 */
router.delete('/jobs/:id', (req, res) => {
  const deleted = deleteJob(req.params.id);

  if (!deleted) {
    return res.status(404).json({ error: 'Job not found' });
  }

  res.json({ success: true });
});

/**
 * POST /api/meal-plan/generate-simple
 * Simpler endpoint that generates from dataset without Gemini
 * Uses the recipe scoring algorithm for quick generation
 */
router.post('/generate-simple', async (req, res) => {
  const { pantryItems = [], preferences = null, recentRecipeHashes = [] } = req.body;

  try {
    const { searchRecipes } = await import('../services/recipeService.js');

    // Get a pool of recipes matching preferences
    const excludeIngredients = preferences?.dislikes || [];

    // Search for diverse recipes
    const results = searchRecipes({
      excludeIngredients,
      limit: 100,
      random: true,
    });

    // Score and select 24 diverse recipes
    const selectedRecipes = results.slice(0, 24).map(r => ({
      name: r.recipe.name,
      description: r.recipe.description,
      servings: r.recipe.servings,
      prepTimeMinutes: Math.round((r.recipe.totalTimeMinutes || 45) * 0.3),
      cookTimeMinutes: Math.round((r.recipe.totalTimeMinutes || 45) * 0.7),
      ingredients: r.recipe.ingredients.map(i => ({
        ingredientName: i.name,
        quantity: i.quantity || 1,
        unit: i.unit || 'pcs',
        preparation: i.preparation,
      })),
      steps: groupSteps(r.recipe.steps),
      tags: r.recipe.tags,
    }));

    // Select default 6 with variety
    const defaultSelections = [0, 1, 2, 3, 4, 5];

    res.json({
      recipes: selectedRecipes,
      defaultSelections,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Unknown error';
    res.status(500).json({ error: message });
  }
});

// Helper to group steps into sections
function groupSteps(steps: string[]): { title: string; substeps: string[] }[] {
  if (steps.length <= 4) {
    return [{ title: 'Instructions', substeps: steps }];
  }

  const sectionSize = Math.ceil(steps.length / Math.ceil(steps.length / 4));
  const titles = ['Preparation', 'Cooking', 'Assembly', 'Finishing'];
  const sections: { title: string; substeps: string[] }[] = [];

  for (let i = 0; i < steps.length; i += sectionSize) {
    const sectionIndex = Math.floor(i / sectionSize);
    sections.push({
      title: titles[sectionIndex] || `Step ${sectionIndex + 1}`,
      substeps: steps.slice(i, i + sectionSize),
    });
  }

  return sections;
}

/**
 * POST /api/meal-plan/polish-grocery-list
 * Polish a grocery list using Gemini to consolidate duplicates,
 * convert to practical shopping quantities, and categorize items.
 *
 * Body:
 * {
 *   ingredients: [{ name, quantity, unit }],
 *   pantryItems?: [{ name, quantity?, unit? }]  // Items user already has
 * }
 *
 * Headers:
 *   X-Gemini-Key: Your Gemini API key
 */
router.post('/polish-grocery-list', async (req, res) => {
  const apiKey = (req.headers['x-gemini-key'] as string) || process.env.GEMINI_API_KEY;

  if (!apiKey) {
    return res.status(401).json({ error: 'Gemini API key required in X-Gemini-Key header or GEMINI_API_KEY env var' });
  }

  const request = req.body as GroceryPolishRequest;

  if (!request.ingredients || !Array.isArray(request.ingredients)) {
    return res.status(400).json({ error: 'ingredients array is required' });
  }

  try {
    console.log(`[GroceryPolish] Polishing ${request.ingredients.length} ingredients (pantry: ${request.pantryItems?.length ?? 0})`);
    const result = await polishGroceryList(apiKey, request.ingredients, request.pantryItems ?? []);
    res.json(result);
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Unknown error';
    console.error(`[GroceryPolish] Error: ${message}`);
    res.status(500).json({ error: message });
  }
});

/**
 * POST /api/meal-plan/polish-grocery-list-async
 * Start an async grocery polish job
 * Returns a job ID immediately - poll /grocery-polish-jobs/:id for status
 *
 * Body: Same as /polish-grocery-list
 * Headers:
 *   X-Gemini-Key: Your Gemini API key
 *
 * Returns: { jobId: string }
 */
router.post('/polish-grocery-list-async', async (req, res) => {
  const apiKey = (req.headers['x-gemini-key'] as string) || process.env.GEMINI_API_KEY;

  if (!apiKey) {
    return res.status(401).json({ error: 'Gemini API key required in X-Gemini-Key header or GEMINI_API_KEY env var' });
  }

  const request = req.body as GroceryPolishRequest;

  if (!request.ingredients || !Array.isArray(request.ingredients)) {
    return res.status(400).json({ error: 'ingredients array is required' });
  }

  // Create job and return immediately
  const job = createGroceryPolishJob();
  const pantryItems = request.pantryItems ?? [];
  console.log(`[GroceryPolishAsync] Created job ${job.id} for ${request.ingredients.length} ingredients (pantry: ${pantryItems.length})`);

  // Start polish in background
  setImmediate(async () => {
    startGroceryPolishJob(job.id);
    console.log(`[GroceryPolishAsync] Starting job ${job.id}`);

    const onProgress = (progress: GroceryPolishProgress) => {
      console.log(`[GroceryPolishAsync] Job ${job.id} progress: batch ${progress.currentBatch}/${progress.totalBatches}`);
      updateGroceryPolishProgress(job.id, progress);
    };

    try {
      const result = await polishGroceryList(apiKey, request.ingredients, pantryItems, onProgress);
      console.log(`[GroceryPolishAsync] Job ${job.id} completed with ${result.items.length} items`);
      completeGroceryPolishJob(job.id, result);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      console.error(`[GroceryPolishAsync] Job ${job.id} failed: ${message}`);
      failGroceryPolishJob(job.id, message);
    }
  });

  // Return job ID immediately
  res.json({ jobId: job.id });
});

/**
 * GET /api/meal-plan/grocery-polish-jobs/:id
 * Get the status of a grocery polish job
 *
 * Returns:
 * - status: 'pending' | 'running' | 'completed' | 'failed'
 * - progress: { phase, currentBatch, totalBatches, message } (if running)
 * - result: GroceryPolishResponse (if completed)
 * - error: string (if failed)
 */
router.get('/grocery-polish-jobs/:id', (req, res) => {
  const job = getGroceryPolishJob(req.params.id);

  if (!job) {
    return res.status(404).json({ error: 'Job not found or expired' });
  }

  // Return job status without dates
  res.json({
    id: job.id,
    status: job.status,
    progress: job.progress,
    result: job.result,
    error: job.error,
  });
});

/**
 * DELETE /api/meal-plan/grocery-polish-jobs/:id
 * Delete a grocery polish job after retrieving the result
 */
router.delete('/grocery-polish-jobs/:id', (req, res) => {
  const deleted = deleteGroceryPolishJob(req.params.id);

  if (!deleted) {
    return res.status(404).json({ error: 'Job not found' });
  }

  res.json({ success: true });
});

export default router;
