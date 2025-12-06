/**
 * Gemini-powered shopping list consolidation
 *
 * Uses AI to intelligently consolidate and convert recipe ingredients
 * into practical shopping quantities.
 */

import { GoogleGenAI } from '@google/genai';
import { getGeminiKey } from '../api/gemini';
import { getUnitSystem } from '../utils/settings';

export interface RawIngredient {
  name: string;
  quantity: number;
  unit: string;
  recipeName: string;
}

export interface ConsolidatedItem {
  name: string;
  quantity: number;
  unit: string;
  category: string;
  notes: string | null;
}

// Valid categories for shopping list items
const VALID_CATEGORIES = [
  'Produce', 'Protein', 'Dairy', 'Bakery', 'Pantry',
  'Spices', 'Oils', 'Frozen', 'Other', 'Household'
];

// Timeout for Gemini API call (45 seconds)
const CONSOLIDATION_TIMEOUT_MS = 45000;

/**
 * Consolidate raw recipe ingredients into practical shopping quantities using Gemini.
 *
 * Examples of what Gemini handles:
 * - "100g chopped carrots" + "2 julienned carrots" → "3 medium carrots"
 * - "2 cups broccoli florets" → "1 head broccoli"
 * - "1/4 cup olive oil" + "2 tbsp olive oil" → "1 bottle olive oil (if not in pantry)"
 */
export async function consolidateShoppingList(
  rawIngredients: RawIngredient[],
  pantryItems: { name: string; quantity: number; unit: string }[]
): Promise<ConsolidatedItem[]> {
  const key = getGeminiKey();
  if (!key) {
    throw new Error('Gemini API key not configured');
  }

  const ai = new GoogleGenAI({ apiKey: key });

  // Get user's preferred unit system
  const unitSystem = getUnitSystem();
  const unitInstructions = unitSystem === 'metric'
    ? `- Proteins: use grams (g) for weight (e.g., "500 g" not "1 lb")
   - Liquids: use milliliters (ml) for volume
   - Produce by weight: use grams (g)`
    : `- Proteins: use pounds (lb) for weight (e.g., "1.5 lb" not "680 g")
   - Liquids: use cups or fluid ounces
   - Produce by weight: use pounds (lb) or ounces (oz)`;

  const prompt = `You are a smart grocery shopping assistant. Convert these recipe ingredients into a practical shopping list.

RAW INGREDIENTS FROM RECIPES:
${rawIngredients.map(i => `- ${i.quantity} ${i.unit} ${i.name} (for ${i.recipeName})`).join('\n')}

CURRENT PANTRY (already have - don't include unless we need more):
${pantryItems.length > 0 ? pantryItems.map(p => `- ${p.name}: ${p.quantity} ${p.unit}`).join('\n') : '(empty)'}

UNIT SYSTEM: ${unitSystem.toUpperCase()}
${unitInstructions}

INSTRUCTIONS:
1. CONSOLIDATE similar ingredients:
   - "100g chopped carrots" + "2 julienned carrots" + "1 cup shredded carrots" → combine into purchasable units
   - Egg yolks + egg whites + whole eggs → total eggs needed
   - Different cuts of same protein → may need separate if significantly different

2. CONVERT to PURCHASABLE UNITS:
   - Fresh produce: use "medium", "large", "head", "bunch" (not cups/grams for whole items)
   - Pantry staples (oils, sauces, spices): "bottle" or "jar" if user needs to buy one

3. SUBTRACT pantry items intelligently:
   - If we have 500g chicken and need 400g → don't list chicken
   - If we have 500g and need 800g → list the difference (300g)
   - For shelf-stable items (oils, spices), only list if pantry is empty or very low

4. CATEGORIZE each item:
   - Produce: fresh fruits, vegetables, herbs
   - Protein: meat, fish, tofu, eggs
   - Dairy: milk, cheese, cream, butter, yogurt
   - Bakery: bread, tortillas, buns
   - Pantry: pasta, rice, canned goods, dry goods
   - Spices: dried spices, seasonings, salt, pepper
   - Oils: cooking oils, olive oil, sesame oil
   - Frozen: frozen items only
   - Other: anything else

5. SKIP items that don't need purchasing:
   - Water, ice
   - Items fully covered by pantry
   - Basic seasonings if likely in any kitchen (salt, pepper)

Respond with ONLY valid JSON (no markdown, no explanation):
{
  "items": [
    {
      "name": "Carrots",
      "quantity": 3,
      "unit": "medium",
      "category": "Produce",
      "notes": null
    },
    {
      "name": "Chicken breast",
      "quantity": 1.5,
      "unit": "lb",
      "category": "Protein",
      "notes": "boneless skinless"
    }
  ]
}`;

  try {
    // Create a timeout promise
    const timeoutPromise = new Promise<never>((_, reject) => {
      setTimeout(() => reject(new Error('Shopping list consolidation timed out')), CONSOLIDATION_TIMEOUT_MS);
    });

    // Race between the API call and timeout
    const response = await Promise.race([
      ai.models.generateContent({
        model: 'gemini-2.5-flash',
        contents: [{ role: 'user', parts: [{ text: prompt }] }],
      }),
      timeoutPromise,
    ]);

    const text = response.candidates?.[0]?.content?.parts?.[0]?.text || '';

    // Extract JSON from response (handle potential markdown wrapping)
    let jsonStr = text.trim();
    if (jsonStr.startsWith('```')) {
      jsonStr = jsonStr.replace(/^```json?\n?/, '').replace(/\n?```$/, '');
    }

    const parsed = JSON.parse(jsonStr);

    if (!parsed.items || !Array.isArray(parsed.items)) {
      throw new Error('Invalid response format from Gemini');
    }

    // Validate and sanitize each item
    return parsed.items
      .map((item: { name?: string; quantity?: number; unit?: string; category?: string; notes?: string | null }) => {
        // Validate required fields exist
        if (!item.name || typeof item.name !== 'string') {
          console.warn('Skipping item with invalid name:', item);
          return null;
        }

        // Validate and clamp quantity
        let quantity = Number(item.quantity);
        if (isNaN(quantity) || quantity < 0) {
          console.warn(`Invalid quantity for ${item.name}, defaulting to 1`);
          quantity = 1;
        }
        if (quantity > 1000) {
          console.warn(`Clamping excessive quantity for ${item.name}: ${quantity} → 100`);
          quantity = 100;
        }

        // Validate category, default to 'Other' if invalid
        const category = VALID_CATEGORIES.includes(item.category || '')
          ? item.category!
          : 'Other';

        return {
          name: item.name.trim(),
          quantity,
          unit: (item.unit || 'units').trim(),
          category,
          notes: item.notes || null,
        };
      })
      .filter((item: ConsolidatedItem | null): item is ConsolidatedItem => item !== null);
  } catch (error) {
    console.error('Failed to consolidate shopping list with Gemini:', error);
    throw error;
  }
}
