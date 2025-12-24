import { Router } from 'express';

const router = Router();

// In-memory image cache (will be replaced with cloud storage)
const imageCache = new Map<string, { url: string; keywords: string[]; createdAt: Date }>();

/**
 * POST /api/images/find-similar
 * Find a similar cached image based on keywords
 */
router.post('/find-similar', (req, res) => {
  const { keywords, mealType } = req.body as { keywords: string[]; mealType?: string };

  if (!keywords || !Array.isArray(keywords)) {
    return res.status(400).json({ error: 'Keywords array required' });
  }

  // Search for similar images by keyword overlap
  let bestMatch: { url: string; score: number } | null = null;

  for (const [_key, entry] of imageCache) {
    let score = 0;
    for (const kw of keywords) {
      if (entry.keywords.some(ek => ek.toLowerCase().includes(kw.toLowerCase()))) {
        score++;
      }
    }
    // Require at least 50% keyword match
    if (score >= keywords.length * 0.5 && (!bestMatch || score > bestMatch.score)) {
      bestMatch = { url: entry.url, score };
    }
  }

  if (bestMatch) {
    res.json({ found: true, url: bestMatch.url });
  } else {
    res.json({ found: false });
  }
});

/**
 * POST /api/images/generate
 * Generate a new image (stub - will integrate with Gemini)
 */
router.post('/generate', async (req, res) => {
  const { recipeName, keywords } = req.body as { recipeName: string; keywords: string[] };

  if (!recipeName) {
    return res.status(400).json({ error: 'Recipe name required' });
  }

  // TODO: Integrate with Gemini image generation
  // For now, return a placeholder
  const placeholderUrl = `https://via.placeholder.com/400x300?text=${encodeURIComponent(recipeName)}`;

  // Cache the result
  const cacheKey = recipeName.toLowerCase().replace(/\s+/g, '-');
  imageCache.set(cacheKey, {
    url: placeholderUrl,
    keywords: keywords || [recipeName],
    createdAt: new Date(),
  });

  res.json({
    url: placeholderUrl,
    cached: true,
  });
});

/**
 * GET /api/images/stats
 * Get image cache statistics
 */
router.get('/stats', (_req, res) => {
  res.json({
    cachedImages: imageCache.size,
  });
});

export default router;
