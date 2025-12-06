/**
 * Google Gemini API Integration for recipe image generation
 */

import { GoogleGenAI } from '@google/genai';
import { db } from '../db';

let apiKey: string | null = null;

export function setGeminiKey(key: string) {
  apiKey = key;
  localStorage.setItem('gemini_api_key', key);
}

export function getGeminiKey(): string | null {
  if (!apiKey) {
    apiKey = localStorage.getItem('gemini_api_key');
  }
  return apiKey;
}

export function hasGeminiKey(): boolean {
  return !!getGeminiKey();
}

// In-memory cache for current session
const memoryCache = new Map<string, string>();

// Check IndexedDB cache for persisted images
async function getCachedImage(recipeName: string): Promise<string | null> {
  try {
    const cacheKey = recipeName.toLowerCase();

    // Check memory cache first
    if (memoryCache.has(cacheKey)) {
      return memoryCache.get(cacheKey)!;
    }

    // Check IndexedDB
    const cached = await db.imageCache
      .where('recipeName')
      .equalsIgnoreCase(recipeName)
      .first();

    if (cached) {
      // Add to memory cache for faster subsequent access
      memoryCache.set(cacheKey, cached.imageDataUrl);
      return cached.imageDataUrl;
    }

    return null;
  } catch {
    return null;
  }
}

// Save image to IndexedDB cache
async function cacheImage(recipeName: string, imageDataUrl: string): Promise<void> {
  try {
    const cacheKey = recipeName.toLowerCase();
    memoryCache.set(cacheKey, imageDataUrl);

    // Check if already cached in DB
    const existing = await db.imageCache
      .where('recipeName')
      .equalsIgnoreCase(recipeName)
      .first();

    if (existing) {
      await db.imageCache.update(existing.id!, {
        imageDataUrl,
        generatedAt: new Date(),
      });
    } else {
      await db.imageCache.add({
        recipeName,
        imageDataUrl,
        generatedAt: new Date(),
      });
    }
  } catch (error) {
    console.error('Failed to cache image:', error);
  }
}

export async function generateRecipeImage(
  recipeName: string,
  tags: string[] = [],
  ingredients: string[] = []
): Promise<string | null> {
  const key = getGeminiKey();
  if (!key) {
    return null;
  }

  // Check cache first (memory + IndexedDB)
  const cached = await getCachedImage(recipeName);
  if (cached) {
    return cached;
  }

  try {
    const ai = new GoogleGenAI({ apiKey: key });

    // Build a descriptive prompt for the food image
    const cuisineHint = tags.find(t =>
      ['italian', 'mexican', 'asian', 'thai', 'indian', 'mediterranean', 'chinese', 'japanese', 'korean', 'vietnamese'].includes(t.toLowerCase())
    );

    // Include key ingredients to ensure accuracy
    const mainIngredients = ingredients.slice(0, 6).join(', ');
    const ingredientNote = mainIngredients ? `\n\nKey ingredients that MUST be visible: ${mainIngredients}. Do NOT add ingredients that are not listed.` : '';

    const prompt = `Generate a professional, appetizing food photography image of "${recipeName}"${cuisineHint ? ` (${cuisineHint} cuisine)` : ''}.${ingredientNote}

STYLE: Real photograph from a cookbook or food magazine. Finished dish plated beautifully on a nice plate/bowl. Natural lighting, shallow depth of field, clean background.

CRITICAL: Generate ONLY the image. NEVER include ANY text, words, titles, ingredient lists, labels, captions, watermarks, or typography anywhere in the image. The image must be purely visual with zero text elements.`;

    const response = await ai.models.generateContentStream({
      model: 'gemini-3-pro-image-preview',
      config: {
        responseModalities: ['IMAGE', 'TEXT'],
      },
      contents: [
        {
          role: 'user',
          parts: [{ text: prompt }],
        },
      ],
    });

    // Process the stream to get the image
    for await (const chunk of response) {
      if (!chunk.candidates?.[0]?.content?.parts) {
        continue;
      }

      const part = chunk.candidates[0].content.parts[0];
      if (part.inlineData?.data) {
        const mimeType = part.inlineData.mimeType || 'image/png';
        const dataUrl = `data:${mimeType};base64,${part.inlineData.data}`;
        // Cache in memory and IndexedDB for persistence
        await cacheImage(recipeName, dataUrl);
        return dataUrl;
      }
    }

    return null;
  } catch (error) {
    console.error('Failed to generate recipe image:', error);
    return null;
  }
}

// Batch generate images for multiple recipes with progress callback
export async function generateRecipeImages(
  recipes: { name: string; tags: string[]; ingredients?: string[] }[],
  onProgress?: (completed: number, total: number, images: Map<string, string>) => void
): Promise<Map<string, string>> {
  const results = new Map<string, string>();
  const total = recipes.length;

  // Generate sequentially to avoid rate limits
  // Gemini has rate limits so we do one at a time
  for (let i = 0; i < recipes.length; i++) {
    const recipe = recipes[i];
    try {
      const url = await generateRecipeImage(recipe.name, recipe.tags, recipe.ingredients || []);
      if (url) {
        results.set(recipe.name, url);
      }
    } catch (error) {
      console.error(`Failed to generate image for ${recipe.name}:`, error);
    }

    // Report progress after each image
    if (onProgress) {
      onProgress(i + 1, total, new Map(results));
    }
  }

  return results;
}

// Generate a step-by-step cooking process image
export async function generateStepImage(
  recipeName: string,
  stepTitle: string,
  _stepInstructions: string[], // Kept for API compatibility, not currently used
  stepNumber: number,
  totalSteps: number
): Promise<string | null> {
  const key = getGeminiKey();
  if (!key) {
    return null;
  }

  // Use a unique cache key for step images
  const cacheKey = `step:${recipeName}:${stepNumber}`;
  const cached = await getCachedImage(cacheKey);
  if (cached) {
    return cached;
  }

  try {
    const ai = new GoogleGenAI({ apiKey: key });

    // Build a prompt focused on the RESULT of the step - minimal, focused composition
    const isEarlyStep = stepNumber <= Math.ceil(totalSteps / 3);
    const isMidStep = stepNumber > Math.ceil(totalSteps / 3) && stepNumber <= Math.ceil(2 * totalSteps / 3);

    let stageDescription = '';
    if (isEarlyStep) {
      stageDescription = 'Show prepped ingredients in 2-3 small bowls or on a cutting board - only what is needed for this step.';
    } else if (isMidStep) {
      stageDescription = 'Show a single pan or bowl with the food being cooked or mixed - focus on just one vessel.';
    } else {
      stageDescription = 'Show the nearly-finished dish in its cooking vessel or assembled but not plated.';
    }

    // Create a focused prompt based on the step title
    const prompt = `Food photography: "${stepTitle}" step for ${recipeName}.

${stageDescription}

RULES:
- NO hands, arms, or people
- NO finished plated dish
- MINIMAL scene: only 1-2 bowls/pans directly relevant to this step
- Clean, uncluttered background
- Overhead angle, soft natural light
- No text or labels`;

    const response = await ai.models.generateContentStream({
      model: 'gemini-3-pro-image-preview',
      config: {
        responseModalities: ['IMAGE', 'TEXT'],
      },
      contents: [
        {
          role: 'user',
          parts: [{ text: prompt }],
        },
      ],
    });

    // Process the stream to get the image
    for await (const chunk of response) {
      if (!chunk.candidates?.[0]?.content?.parts) {
        continue;
      }

      const part = chunk.candidates[0].content.parts[0];
      if (part.inlineData?.data) {
        const mimeType = part.inlineData.mimeType || 'image/png';
        const dataUrl = `data:${mimeType};base64,${part.inlineData.data}`;
        // Cache in memory and IndexedDB for persistence
        await cacheImage(cacheKey, dataUrl);
        return dataUrl;
      }
    }

    return null;
  } catch (error) {
    console.error('Failed to generate step image:', error);
    return null;
  }
}

// Generate an artisanal style image for a pantry ingredient
export async function generateIngredientImage(
  ingredientName: string,
  category: string
): Promise<string | null> {
  const key = getGeminiKey();
  if (!key) {
    return null;
  }

  // Check cache first (memory + IndexedDB) - use ingredient prefix to avoid recipe collisions
  const cacheKey = `ingredient:${ingredientName}`;
  const cached = await getCachedImage(cacheKey);
  if (cached) {
    return cached;
  }

  try {
    const ai = new GoogleGenAI({ apiKey: key });

    const prompt = `Generate a beautiful artisanal cookbook-style photograph of "${ingredientName}" (${category}). The image should look like it's from a premium cookbook or culinary magazine - moody lighting, rustic wooden surface or marble counter, the ingredient displayed elegantly. Focus on the raw ingredient itself in its natural form. Photorealistic, high-end food photography aesthetic. No text or labels.`;

    const response = await ai.models.generateContentStream({
      model: 'gemini-3-pro-image-preview',
      config: {
        responseModalities: ['IMAGE', 'TEXT'],
      },
      contents: [
        {
          role: 'user',
          parts: [{ text: prompt }],
        },
      ],
    });

    // Process the stream to get the image
    for await (const chunk of response) {
      if (!chunk.candidates?.[0]?.content?.parts) {
        continue;
      }

      const part = chunk.candidates[0].content.parts[0];
      if (part.inlineData?.data) {
        const mimeType = part.inlineData.mimeType || 'image/png';
        const dataUrl = `data:${mimeType};base64,${part.inlineData.data}`;
        // Cache in memory and IndexedDB for persistence
        await cacheImage(cacheKey, dataUrl);
        return dataUrl;
      }
    }

    return null;
  } catch (error) {
    console.error('Failed to generate ingredient image:', error);
    return null;
  }
}

// Type for history entry used in preference summarization
export interface HistoryEntryForSummary {
  recipeName: string;
  rating: number;
  wouldMakeAgain: boolean | null;
  dateCooked: Date;
  tags: string[];
  cuisines: string[];
  ingredients: string[];
}

// Generate a preference summary from recipe history
export async function generatePreferenceSummary(
  historyEntries: HistoryEntryForSummary[],
  existingSummary: string | null
): Promise<{ summary: string; likes: string[]; dislikes: string[] } | null> {
  const key = getGeminiKey();
  if (!key) {
    return null;
  }

  try {
    const ai = new GoogleGenAI({ apiKey: key });

    // Build the history data as structured text
    const historyText = historyEntries.map(h => {
      const stars = '★'.repeat(h.rating) + '☆'.repeat(5 - h.rating);
      const again = h.wouldMakeAgain === true ? 'Yes' : h.wouldMakeAgain === false ? 'No' : 'N/A';
      return `- ${h.recipeName} [${stars}] (Make again: ${again})
  Tags: ${h.tags.join(', ') || 'none'}
  Cuisines: ${h.cuisines.join(', ') || 'none'}
  Key ingredients: ${h.ingredients.slice(0, 6).join(', ')}`;
    }).join('\n\n');

    const existingSummarySection = existingSummary
      ? `\n\nPREVIOUS SUMMARY (merge and update with new data):\n${existingSummary}`
      : '';

    const prompt = `Analyze this meal planning app user's recipe rating history and create a taste preference profile.

RECIPE HISTORY:
${historyText}
${existingSummarySection}

Create a JSON response with exactly this structure:
{
  "summary": "A ~500-800 word profile describing this person's food preferences, cooking style, and taste patterns. Be specific about cuisines, ingredients, cooking methods, and flavor profiles they enjoy. Note any patterns like preference for quick meals vs elaborate dishes, spice tolerance, dietary tendencies, etc.",
  "likes": ["specific ingredient or cuisine 1", "specific ingredient or cuisine 2", ...],
  "dislikes": ["specific ingredient or cuisine 1", "specific ingredient or cuisine 2", ...]
}

Rules:
- The summary should read like a personal food profile, written in third person
- Extract SPECIFIC likes and dislikes (e.g., "cilantro", "Thai cuisine", "spicy food") not vague ones
- Only include items in likes/dislikes with clear evidence from ratings (4-5 stars = like, 1-2 stars = dislike)
- If merging with previous summary, preserve important preferences but update based on new data
- Keep likes and dislikes lists to max 20 items each, prioritizing strongest preferences

Respond with ONLY the JSON, no markdown or explanation.`;

    const response = await ai.models.generateContent({
      model: 'gemini-2.5-flash',
      contents: [{ role: 'user', parts: [{ text: prompt }] }],
    });

    const text = response.text?.trim();
    if (!text) return null;

    // Parse the JSON response (handle potential markdown wrapping)
    const jsonMatch = text.match(/\{[\s\S]*\}/);
    if (!jsonMatch) return null;

    const parsed = JSON.parse(jsonMatch[0]);
    return {
      summary: parsed.summary || '',
      likes: Array.isArray(parsed.likes) ? parsed.likes : [],
      dislikes: Array.isArray(parsed.dislikes) ? parsed.dislikes : [],
    };
  } catch (error) {
    console.error('Failed to generate preference summary:', error);
    return null;
  }
}
