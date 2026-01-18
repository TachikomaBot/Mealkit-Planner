import { GoogleGenAI, Type, ThinkingLevel, type FunctionDeclaration } from '@google/genai';
import { searchRecipes, getRecipeBySourceId } from './recipeService.js';
import type {
  GeneratedRecipe,
  MealPlanRequest,
  ModifiedIngredient,
  PantryItem,
  PolishedGroceryItem,
  RecipeIngredient,
  UserPreferences,
  ProgressEvent,
  SubstitutionRequest,
  SubstitutionResponse,
  RecipeCustomizationRequest,
  RecipeCustomizationResponse,
} from '../types.js';

// ============================================================================
// Types
// ============================================================================

interface MealOutline {
  name: string;
  description: string;
  components: {
    main: string;
    carb?: string;
    veggie?: string;
  };
  sourceRecipeIds: number[];
  estimatedTime: number;
  tags: string[];
  usesExpiringIngredients: boolean;
  expiringIngredientsUsed: string[];
}

interface ComposedMeal extends GeneratedRecipe {
  usesExpiringIngredients: boolean;
  expiringIngredientsUsed: string[];
  sourceRecipeIds: number[];
}

// ============================================================================
// Tool Definitions for Gemini
// ============================================================================

const FUNCTION_DECLARATIONS: FunctionDeclaration[] = [
  {
    name: 'search_recipes',
    description: 'Search the recipe database by various criteria. Returns up to 20 matching recipes.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        query: {
          type: Type.STRING,
          description: 'Text search query for recipe names or descriptions'
        },
        ingredients: {
          type: Type.ARRAY,
          items: { type: Type.STRING },
          description: 'Ingredients that should be in the recipe'
        },
        cuisine: {
          type: Type.STRING,
          description: 'Cuisine type (e.g., italian, mexican, thai, chinese)'
        },
        maxTime: {
          type: Type.NUMBER,
          description: 'Maximum total cooking time in minutes'
        },
        tags: {
          type: Type.ARRAY,
          items: { type: Type.STRING },
          description: 'Tags to filter by (e.g., easy, quick, vegetarian)'
        },
        protein: {
          type: Type.STRING,
          description: 'Main protein type (chicken, beef, pork, seafood, vegetarian)'
        }
      }
    }
  },
  {
    name: 'get_recipe_details',
    description: 'Get full details for specific recipes by their IDs.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        recipeIds: {
          type: Type.ARRAY,
          items: { type: Type.NUMBER },
          description: 'Array of recipe source IDs to fetch'
        }
      },
      required: ['recipeIds']
    }
  }
];

// ============================================================================
// Tool Handlers
// ============================================================================

function handleSearchRecipes(input: {
  query?: string;
  ingredients?: string[];
  cuisine?: string;
  maxTime?: number;
  tags?: string[];
  protein?: string;
}): string {
  const results = searchRecipes({
    searchText: input.query,
    includeIngredients: input.ingredients,
    cuisines: input.cuisine ? [input.cuisine] : undefined,
    maxTotalTime: input.maxTime,
    limit: 20,
  });

  // Filter by protein if specified
  let filtered = results;
  if (input.protein) {
    const p = input.protein.toLowerCase();
    filtered = results.filter(r => {
      const ingText = r.recipe.ingredients.map(i => i.name.toLowerCase()).join(' ');
      if (p === 'chicken') return ingText.includes('chicken');
      if (p === 'beef') return ingText.includes('beef') || ingText.includes('steak');
      if (p === 'pork') return ingText.includes('pork') || ingText.includes('bacon') || ingText.includes('ham');
      if (p === 'seafood') return ingText.includes('fish') || ingText.includes('shrimp') || ingText.includes('salmon');
      if (p === 'vegetarian') return !ingText.includes('chicken') && !ingText.includes('beef') && !ingText.includes('pork') && !ingText.includes('fish');
      return true;
    });
  }

  // Filter by tags if specified
  if (input.tags && input.tags.length > 0) {
    const tagsLower = input.tags.map(t => t.toLowerCase());
    filtered = filtered.filter(r =>
      tagsLower.some(tag => r.recipe.tags.some(rt => rt.toLowerCase().includes(tag)))
    );
  }

  const output = filtered.slice(0, 20).map(r => ({
    id: r.recipe.sourceId,
    name: r.recipe.name,
    description: r.recipe.description.slice(0, 100),
    time: r.recipe.totalTimeMinutes,
    servings: r.recipe.servings,
    cuisines: r.recipe.cuisines,
    tags: r.recipe.tags.slice(0, 4),
    mainIngredients: r.recipe.ingredients.slice(0, 5).map(i => i.name)
  }));

  return JSON.stringify(output);
}

function handleGetRecipeDetails(input: { recipeIds: number[] }): string {
  const recipes = input.recipeIds
    .map(id => getRecipeBySourceId(id))
    .filter(Boolean);

  const output = recipes.map(r => ({
    id: r!.sourceId,
    name: r!.name,
    description: r!.description,
    servings: r!.servings,
    time: r!.totalTimeMinutes,
    ingredients: r!.ingredients,
    steps: r!.steps,
    tags: r!.tags,
    cuisines: r!.cuisines
  }));

  return JSON.stringify(output);
}

function processToolCall(toolName: string, args: Record<string, unknown>): string {
  switch (toolName) {
    case 'search_recipes':
      return handleSearchRecipes(args as Parameters<typeof handleSearchRecipes>[0]);
    case 'get_recipe_details':
      return handleGetRecipeDetails(args as { recipeIds: number[] });
    default:
      return JSON.stringify({ error: `Unknown tool: ${toolName}` });
  }
}

// ============================================================================
// Gemini API Calls with Function Calling
// ============================================================================

// Simple Gemini call without tools (for when we don't need function calling)
// Uses JSON response mode to ensure valid JSON output
async function callGeminiSimple(
  apiKey: string,
  systemPrompt: string,
  userPrompt: string
): Promise<string> {
  const ai = new GoogleGenAI({ apiKey });

  const response = await ai.models.generateContent({
    model: 'gemini-3-flash-preview',
    config: {
      systemInstruction: systemPrompt,
      maxOutputTokens: 16000, // Allow longer responses for recipe batches
      temperature: 0.7, // Slightly lower for more consistent JSON output
      responseMimeType: 'application/json', // Force valid JSON output
      thinkingConfig: {
        thinkingLevel: ThinkingLevel.MEDIUM,
      },
    },
    contents: [{ role: 'user', parts: [{ text: userPrompt }] }]
  });

  const candidate = response.candidates?.[0];
  if (!candidate?.content?.parts) {
    throw new Error('No response from Gemini');
  }

  // Check if response was truncated
  if (candidate.finishReason === 'MAX_TOKENS') {
    console.warn('[Gemini] Response was truncated due to max tokens limit');
  }

  const textPart = candidate.content.parts.find(p => p.text);
  return textPart?.text || '';
}

async function callGeminiWithTools(
  apiKey: string,
  systemPrompt: string,
  userPrompt: string,
  maxIterations: number = 10
): Promise<string> {
  const ai = new GoogleGenAI({ apiKey });
  const modelName = 'gemini-3-flash-preview';

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const contents: any[] = [
    { role: 'user', parts: [{ text: userPrompt }] }
  ];

  const warningThreshold = Math.floor(maxIterations * 0.7);
  const urgentThreshold = maxIterations - 2;

  for (let i = 0; i < maxIterations; i++) {
    let response;
    try {
      response = await ai.models.generateContent({
        model: modelName,
        config: {
          systemInstruction: systemPrompt,
          tools: [{ functionDeclarations: FUNCTION_DECLARATIONS }],
          thinkingConfig: {
            thinkingLevel: ThinkingLevel.MEDIUM,
          },
        },
        contents: contents
      });
    } catch (err) {
      console.warn(`Gemini API call failed (attempt ${i + 1}), retrying...`, err);
      await new Promise(resolve => setTimeout(resolve, 1000));
      response = await ai.models.generateContent({
        model: modelName,
        config: {
          systemInstruction: systemPrompt,
          tools: [{ functionDeclarations: FUNCTION_DECLARATIONS }],
          thinkingConfig: {
            thinkingLevel: ThinkingLevel.MEDIUM,
          },
        },
        contents: contents
      });
    }

    const candidate = response.candidates?.[0];
    if (!candidate?.content?.parts) {
      throw new Error('No response from Gemini');
    }

    const parts = candidate.content.parts;
    const functionCalls = parts.filter(p => p.functionCall);

    // Log what's happening
    console.log(`[Gemini] Iteration ${i + 1}/${maxIterations}: ${functionCalls.length} function calls`);
    functionCalls.forEach(fc => {
      const argsStr = JSON.stringify(fc.functionCall?.args || {});
      console.log(`  -> ${fc.functionCall?.name}(${argsStr.slice(0, 80)}${argsStr.length > 80 ? '...' : ''})`);
    });

    if (functionCalls.length === 0) {
      const textPart = parts.find(p => p.text);
      const text = textPart?.text || '';
      console.log(`[Gemini] Got final text response (${text.length} chars)`);

      // Check if the response looks like valid JSON
      if (text.includes('{') && text.includes('"')) {
        return text;
      }

      // Model output thinking text instead of JSON - make one retry request
      console.log(`[Gemini] Response doesn't look like JSON, attempting recovery...`);

      // Add the text response and ask for JSON conversion
      contents.push({ role: 'model', parts: [{ text }] });
      contents.push({
        role: 'user',
        parts: [{
          text: `Your response above is not in the required JSON format. You MUST output a valid JSON object with a "meals" array containing the recipes you have found. Output ONLY the JSON, no explanation. Start your response with { and end with }. If you haven't found enough recipes, include whatever you have.`
        }]
      });

      // One more API call for JSON recovery with JSON mode
      const recoveryResponse = await ai.models.generateContent({
        model: modelName,
        config: {
          systemInstruction: systemPrompt,
          responseMimeType: 'application/json', // Force valid JSON output
        },
        contents: contents
      });

      const recoveryText = recoveryResponse.candidates?.[0]?.content?.parts?.find(p => p.text)?.text || '';
      console.log(`[Gemini] Recovery response (${recoveryText.length} chars)`);
      return recoveryText;
    }

    // Process function calls
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const functionResponses: any[] = [];
    for (const part of functionCalls) {
      if (part.functionCall && part.functionCall.name) {
        const result = processToolCall(
          part.functionCall.name,
          (part.functionCall.args || {}) as Record<string, unknown>
        );
        const parsedResult = JSON.parse(result);
        const wrappedResult = Array.isArray(parsedResult)
          ? { results: parsedResult }
          : parsedResult;
        functionResponses.push({
          functionResponse: {
            name: part.functionCall.name,
            response: wrappedResult
          }
        });
      }
    }

    // Add iteration budget warnings to help Gemini know when to wrap up
    let budgetWarning = '';
    if (i >= urgentThreshold) {
      budgetWarning = `\n\n🛑 STOP! You have only ${maxIterations - i - 1} tool calls remaining. DO NOT make any more tool calls. Output your final JSON response RIGHT NOW with whatever meals you have planned. The JSON must start with { and contain a "meals" array. Example: {"meals": [{"name": "...", "description": "...", ...}]}`;
    } else if (i >= warningThreshold) {
      budgetWarning = `\n\n⚠️ WARNING: ${maxIterations - i - 1} tool calls remaining. You MUST finish searching and output your final JSON response within the next 2-3 iterations. Do not keep searching indefinitely.`;
    }

    contents.push({
      role: 'model',
      parts: parts
    });
    contents.push({
      role: 'user',
      parts: budgetWarning
        ? [...functionResponses, { text: budgetWarning }]
        : functionResponses
    });
  }

  console.error(`[Gemini] Max iterations (${maxIterations}) exceeded - model kept making tool calls without final response`);
  throw new Error(`Max tool iterations (${maxIterations}) exceeded - Gemini kept calling tools without producing final output`);
}

// ============================================================================
// JSON Extraction Helper
// ============================================================================

/**
 * Extract and parse JSON from Gemini response with robust error handling.
 * Handles common issues like markdown code blocks, trailing commas, truncation, etc.
 */
function extractAndParseJSON(text: string, expectedKey: string): Record<string, unknown> {
  // Remove markdown code blocks if present
  let cleaned = text.replace(/```json\s*/gi, '').replace(/```\s*/g, '');

  // Helper to apply common JSON fixes
  const fixCommonIssues = (json: string): string => {
    return json
      // Remove trailing commas before ] or }
      .replace(/,(\s*[}\]])/g, '$1')
      // Fix unescaped newlines in strings (common Gemini issue)
      .replace(/([^\\])\\n/g, '$1\\\\n');
  };

  // Strategy 1: Try to find balanced JSON directly from the cleaned text
  // This is more reliable than regex when there's extra text after the JSON
  const firstBrace = cleaned.indexOf('{');
  if (firstBrace !== -1) {
    const balancedJson = findBalancedJSON(cleaned.slice(firstBrace));
    if (balancedJson && balancedJson.includes(`"${expectedKey}"`)) {
      try {
        const fixed = fixCommonIssues(balancedJson);
        const parsed = JSON.parse(fixed);
        console.log('[JSON] Parsed using balanced extraction');
        return parsed;
      } catch (e) {
        console.log(`[JSON] Balanced extraction parse failed: ${(e as Error).message}`);
      }
    }
  }

  // Strategy 2: Fall back to regex matching
  const jsonRegex = new RegExp(`\\{[\\s\\S]*"${expectedKey}"[\\s\\S]*\\}`, 'g');
  const matches = cleaned.match(jsonRegex);

  if (!matches || matches.length === 0) {
    console.error('[JSON] No JSON found in response. First 500 chars:', text.slice(0, 500));
    throw new Error(`Failed to find JSON with "${expectedKey}" key in response`);
  }

  // Try each match (in case there are multiple JSON objects)
  for (const match of matches) {
    try {
      const fixedJson = fixCommonIssues(match);
      return JSON.parse(fixedJson);
    } catch (e) {
      const parseError = e as Error;
      console.log(`[JSON] Regex match parse failed: ${parseError.message}`);

      // Try balanced extraction on the match
      try {
        const bracketMatch = findBalancedJSON(match);
        if (bracketMatch) {
          const fixed = fixCommonIssues(bracketMatch);
          return JSON.parse(fixed);
        }
      } catch (e2) {
        console.log(`[JSON] Balanced JSON extraction failed: ${(e2 as Error).message}`);
      }

      // Try to repair truncated JSON
      try {
        const repaired = repairTruncatedJSON(match);
        if (repaired) {
          const fixed = fixCommonIssues(repaired);
          const parsed = JSON.parse(fixed);
          console.log('[JSON] Successfully repaired truncated JSON');
          return parsed;
        }
      } catch (e3) {
        console.error(`[JSON] Repair failed: ${(e3 as Error).message}`);
        console.error('[JSON] Repaired text sample:', repairTruncatedJSON(match)?.slice(-200));
        continue;
      }
    }
  }

  // Log the problematic text for debugging
  console.error('[JSON] Failed to parse any JSON match.');
  console.error('[JSON] First match start:', matches[0]?.slice(0, 300));
  console.error('[JSON] First match end:', matches[0]?.slice(-300));
  throw new Error(`Failed to parse JSON response: invalid JSON structure`);
}

/**
 * Attempt to repair truncated JSON by closing open brackets/braces.
 * Strategy: Find the last complete object (ending with },) and truncate there,
 * then close remaining brackets in the correct nesting order.
 */
function repairTruncatedJSON(text: string): string | null {
  const startIdx = text.indexOf('{');
  if (startIdx === -1) return null;

  let json = text.slice(startIdx);

  // Track bracket nesting order using a stack
  const countBrackets = (str: string): { stack: string[]; inString: boolean } => {
    const stack: string[] = [];
    let inString = false;

    for (let i = 0; i < str.length; i++) {
      const char = str[i];
      const prevChar = i > 0 ? str[i - 1] : '';

      if (char === '"' && prevChar !== '\\') {
        inString = !inString;
      }

      if (!inString) {
        if (char === '{' || char === '[') {
          stack.push(char);
        } else if (char === '}') {
          if (stack.length && stack[stack.length - 1] === '{') {
            stack.pop();
          }
        } else if (char === ']') {
          if (stack.length && stack[stack.length - 1] === '[') {
            stack.pop();
          }
        }
      }
    }

    return { stack, inString };
  };

  let { stack, inString } = countBrackets(json);

  // If balanced and not in string, no repair needed
  if (stack.length === 0 && !inString) {
    return json;
  }

  console.log(`[JSON] Attempting to repair: ${stack.length} unclosed brackets, inString=${inString}`);

  // Strategy: Find the last complete object/array element and truncate there
  // Handle whitespace between } and , (common in formatted JSON)

  // Find the last complete structure - look for }, or ], possibly with whitespace
  let lastComplete = -1;
  let truncateAt = -1;

  // Find all positions of } or ] followed by optional whitespace and comma
  const completeObjectPattern = /\}(\s*),/g;
  let match;
  while ((match = completeObjectPattern.exec(json)) !== null) {
    lastComplete = match.index;
    truncateAt = match.index + 1; // Include the }
  }

  // Also check for ] followed by comma (for arrays of primitives)
  const completeArrayPattern = /\](\s*),/g;
  while ((match = completeArrayPattern.exec(json)) !== null) {
    if (match.index > lastComplete) {
      lastComplete = match.index;
      truncateAt = match.index + 1; // Include the ]
    }
  }

  if (truncateAt > 0) {
    json = json.slice(0, truncateAt);
    // Rebuild the stack for truncated JSON
    ({ stack, inString } = countBrackets(json));
    console.log(`[JSON] Truncated to last complete object at position ${truncateAt}, ${stack.length} brackets remaining`);
  }

  // If still in a string, we need to close it first
  if (inString) {
    console.log('[JSON] Closing unclosed string');
    json += '"';
    ({ stack, inString } = countBrackets(json));
  }

  // Close brackets in reverse nesting order (last opened = first closed)
  let suffix = '';
  for (let i = stack.length - 1; i >= 0; i--) {
    suffix += stack[i] === '{' ? '}' : ']';
  }

  return json + suffix;
}

/**
 * Find a balanced JSON object by counting braces (string-aware)
 */
function findBalancedJSON(text: string): string | null {
  let depth = 0;
  let start = -1;
  let inString = false;

  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    const prevChar = i > 0 ? text[i - 1] : '';

    // Handle string boundaries (skip escaped quotes)
    if (char === '"' && prevChar !== '\\') {
      inString = !inString;
      continue;
    }

    // Only count braces outside strings
    if (!inString) {
      if (char === '{') {
        if (depth === 0) start = i;
        depth++;
      } else if (char === '}') {
        depth--;
        if (depth === 0 && start !== -1) {
          return text.slice(start, i + 1);
        }
      }
    }
  }

  return null;
}

// ============================================================================
// Stage 1: Meal Planning
// ============================================================================

async function planMeals(
  apiKey: string,
  pantryItems: PantryItem[],
  preferences: UserPreferences | null,
  recentRecipes: string[],
  targetServings: number,
  numMeals: number,
  onProgress?: (event: ProgressEvent) => void
): Promise<MealOutline[]> {
  onProgress?.({ phase: 'planning', current: 0, total: 1, message: 'Planning meals...' });

  // Build preference section
  let preferenceSection = '';
  if (preferences) {
    const parts: string[] = [];
    if (preferences.summary) {
      parts.push(`Taste profile: ${preferences.summary}`);
    }
    if (preferences.likes && preferences.likes.length > 0) {
      parts.push(`Favorite ingredients: ${preferences.likes.join(', ')}`);
    }
    if (preferences.dislikes && preferences.dislikes.length > 0) {
      parts.push(`Ingredients to avoid (DO NOT USE): ${preferences.dislikes.join(', ')}`);
    }
    if (parts.length > 0) {
      preferenceSection = `
USER TASTE PREFERENCES:
${parts.join('\n')}
- Respect the user's ingredients to avoid - do NOT include meals featuring those ingredients
- Favor the user's favorite ingredients
`;
    }
  }

  const systemPrompt = `You are a meal planning architect. Your job is to compose ${numMeals} complete, balanced dinner meals for the week.

IMPORTANT CONCEPTS:
- A "meal" is a complete dinner: main protein + carb side + vegetable side
- Use the search_recipes tool to find recipes from our database
- Scale recipes to ${targetServings} servings

BALANCE REQUIREMENTS (across all ${numMeals} meals):
- Mix of proteins: chicken, beef, pork, seafood, vegetarian
- Mix of formats: bowls, sandwiches/burgers, tacos/wraps, pasta, soups/stews, sheet pan, salads
- Mix of cook times: mostly quick (≤30 min), some longer (45-60 min)
${preferenceSection}

CRITICAL VARIETY RULES:
- NEVER repeat the same cuisine+protein combo more than twice
- Each meal must have a DISTINCT main flavor profile
- Spread cuisines evenly: Italian, Mexican, Asian, Indian, Mediterranean, American, etc.

RECENT MEALS TO AVOID:
${recentRecipes.length > 0 ? recentRecipes.join(', ') : 'None'}

WORKFLOW:
1. Use search_recipes to find main dishes, then sides as needed
2. Compose complete meals (main + carb + veggie)
3. Return the final ${numMeals} meal outlines

Respond with JSON at the end:
{
  "meals": [
    {
      "name": "Honey Garlic Pork Chops with Roasted Potatoes",
      "description": "Pan-seared pork with crispy herb potatoes and green beans",
      "components": {
        "main": "Honey Garlic Pork Chops",
        "carb": "Rosemary Roasted Potatoes",
        "veggie": "Sautéed Green Beans"
      },
      "sourceRecipeIds": [12345, 67890],
      "estimatedTime": 40,
      "tags": ["american", "comfort", "one-pan"],
      "usesExpiringIngredients": false,
      "expiringIngredientsUsed": []
    }
  ]
}`;

  const pantrySection = pantryItems.length > 0
    ? `Current pantry staples:\n${pantryItems.map(i => `${i.name}: ${i.quantity} ${i.unit}`).join('\n')}`
    : '(No pantry items specified - assume standard pantry staples like salt, pepper, oil, etc.)';

  const userPrompt = `Plan ${numMeals} complete dinner meals for this week.

${pantrySection}

CRITICAL INSTRUCTIONS:
1. You have a STRICT LIMIT of 10-15 tool calls total - search efficiently!
2. Do 4-6 broad searches to find diverse recipes (chicken, beef, pork, fish, vegetarian, etc.)
3. STOP SEARCHING after finding enough source recipes for ${numMeals} meals
4. OUTPUT YOUR JSON RESPONSE as soon as you have enough recipes
5. DO NOT analyze, explain, or think out loud - just output the JSON
6. Your response MUST be valid JSON starting with { and containing a "meals" array

If you've made more than 10 tool calls, STOP and output your JSON immediately with whatever recipes you have found.`;

  // Planning requires more iterations as it needs to search for recipes
  const result = await callGeminiWithTools(apiKey, systemPrompt, userPrompt, 25);

  onProgress?.({ phase: 'planning', current: 1, total: 1, message: 'Planning complete' });

  // Extract JSON with robust handling
  const parsed = extractAndParseJSON(result, 'meals');
  return parsed.meals as MealOutline[];
}

// ============================================================================
// Stage 2: Recipe Construction
// ============================================================================

async function constructMealBatch(
  apiKey: string,
  mealOutlines: MealOutline[],
  batchIndex: number,
  totalMeals: number,
  targetServings: number,
  onProgress?: (event: ProgressEvent) => void
): Promise<ComposedMeal[]> {
  const systemPrompt = `You are constructing detailed recipe cards for composed meals.

OUTPUT FORMAT for each meal:
- name: The composed meal name
- description: 1-2 sentence description
- servings: ${targetServings}
- prepTimeMinutes / cookTimeMinutes: Based on combined workflow
- ingredients: ALL ingredients from all components, scaled to ${targetServings} servings
- steps: 5-7 main procedure cards, each with 2-4 substeps (see STEP RULES below)
- tags: 3-4 relevant tags

STEP RULES - Follow this format for professional recipe cards:

Step Title Naming:
- Step 1 should be "Mise en Place" or "Prep Work" (gathering/cutting/measuring)
- Middle steps: "[ACTION] THE [TARGET]" format, e.g.:
  - "Cook the Rice", "Roast the Vegetables", "Prepare the Chicken"
  - "Make the Sauce", "Boil the Pasta", "Start the Gravy"
- Final step should be "Plate Your Dish" or "Finish & Serve"

Substep Writing Rules:
- Use **bold** markdown for all ingredient names: "Add the **garlic** and **ginger**"
- Use **bold** for cooking times: "Cook, **3 to 5 min.**, until golden"
- Use **bold** for temperatures: "Preheat the oven to **450°F**" or "heat on **medium-high**"
- Always pair times with visual cues: "Sauté, **2 to 3 min.**, until fragrant"
- Keep substeps concise and action-oriented (start with verbs)
- Group related actions in single substeps when logical

Example step structure:
{
  "title": "Cook the Beef",
  "substeps": [
    "In a large pan, heat a drizzle of **oil** on **medium-high**.",
    "Add the **beef** and **onions**; season with the **spices** and **S&P**.",
    "Cook, breaking up the meat, **4 to 6 min.**, until browned and cooked through."
  ]
}

Workflow Optimization:
- Consider parallel cooking (e.g., "Meanwhile, boil the pasta...")
- Use "Meanwhile" to indicate tasks that happen during wait times
- Reserve the pan/pot instructions when needed later for sauces

IMPORTANT: For ingredients with "to taste" or unmeasured quantities, use null for quantity, not a string.

QUANTITY RULES - Use COOKING MEASURES for human-friendly recipes:
- The "quantity" field MUST be a number (e.g., 0.5, 1, 2.5), NEVER a string like "1/2"
- Use cooking measures: cups, tbsp, tsp for most ingredients
- Common volumes: 1 cup, 0.5 cup, 0.25 cup, 0.33 cup, 1 tbsp, 0.5 tbsp, 1 tsp, 0.5 tsp
- Whole items (eggs, cloves, onions): use whole numbers (1, 2, 3)
- Proteins by piece: "2 chicken breasts", "4 salmon fillets" (not grams)
- Liquids: cups for cooking (1 cup broth), or practical amounts (1 can coconut milk)
- Rice/grains: cups (1 cup rice, 0.5 cup quinoa)
- Cheese: cups shredded (1 cup), or descriptive (4 oz block)
- Fresh produce: count with size (2 medium carrots, 1 large onion)
- Fresh herbs: descriptive amounts (1 bunch, 1 handful, 2 sprigs)
- Spices/seasonings: tsp or tbsp (1 tsp cumin, 0.5 tbsp paprika)
- EXCEPTION: Only use grams for proteins when weight matters (ground beef: 500g)

INGREDIENT RULES:
- NEVER use pre-made/frozen convenience ingredients like: refrigerated pizza dough, frozen pie crust,
  canned biscuit dough, store-bought rotisserie chicken, frozen vegetables
- If a recipe traditionally uses pre-made ingredients, adapt to fresh/homemade:
  - Refrigerated pizza dough → homemade dough (flour, water, yeast, salt, olive oil)
  - Frozen pie crust → homemade pastry
- Adjusting to fresh may increase prep time - that's acceptable
- Omit unpurchasable specialty items like "liquid smoke"

SAUCE RULES (nuanced):
- Store-bought sauces are FINE for: worcestershire sauce, soy sauce, fish sauce, tomato-based pasta sauce
  (marinara, arrabiata), hot sauce, oyster sauce, hoisin - these are complex fermented or long-process
  sauces that aren't practical to make at home
- Homemade sauces PREFERRED for: alfredo, cheese sauce/bechamel (roux-based), cream sauces,
  pan sauces, gravies, vinaigrettes, simple butter sauces - these are quick and taste better fresh
- When in doubt: if it requires fermentation, aging, or 20+ specialty ingredients → store-bought OK

Respond with JSON:
{
  "meals": [
    {
      "name": "Thai Basil Chicken with Jasmine Rice",
      "description": "Fragrant stir-fried chicken with holy basil served over fluffy jasmine rice.",
      "servings": ${targetServings},
      "prepTimeMinutes": 15,
      "cookTimeMinutes": 25,
      "ingredients": [
        {"ingredientName": "chicken breast", "quantity": 2, "unit": "pieces", "preparation": "diced"},
        {"ingredientName": "jasmine rice", "quantity": 1, "unit": "cup", "preparation": null},
        {"ingredientName": "garlic", "quantity": 4, "unit": "cloves", "preparation": "minced"},
        {"ingredientName": "vegetable broth", "quantity": 1.5, "unit": "cups", "preparation": null},
        {"ingredientName": "soy sauce", "quantity": 2, "unit": "tbsp", "preparation": null},
        {"ingredientName": "fresh basil", "quantity": 1, "unit": "bunch", "preparation": "leaves picked"},
        {"ingredientName": "salt", "quantity": null, "unit": "to taste", "preparation": null}
      ],
      "steps": [
        {
          "title": "Mise en Place",
          "substeps": [
            "Dice the **chicken** into bite-sized pieces; season with **S&P**.",
            "Mince the **garlic** and slice the **chilis** thinly.",
            "Pick the **basil leaves** from the stems."
          ]
        },
        {
          "title": "Cook the Rice",
          "substeps": [
            "In a medium pot, combine the **rice** and **1½ cups water**; bring to a boil.",
            "Reduce heat to low, cover, and simmer, **12 to 15 min.**, until tender.",
            "Remove from heat and let sit, covered, **5 min.** Fluff with a fork."
          ]
        },
        {
          "title": "Stir-Fry the Chicken",
          "substeps": [
            "In a large pan or wok, heat **2 tbsp oil** on **high**.",
            "Add the **chicken** and cook, **3 to 4 min.**, until golden and cooked through.",
            "Add the **garlic** and **chilis**; stir-fry, **30 sec.**, until fragrant."
          ]
        },
        {
          "title": "Plate Your Dish",
          "substeps": [
            "Divide the **rice** between your plates.",
            "Top with the **stir-fried chicken** and garnish with **basil leaves**. Bon appétit!"
          ]
        }
      ],
      "tags": ["quick", "thai", "stir-fry"],
      "usesExpiringIngredients": false,
      "expiringIngredientsUsed": [],
      "sourceRecipeIds": [123]
    }
  ]
}`;

  // Fetch source recipe details
  const allSourceIds = mealOutlines.flatMap(m => m.sourceRecipeIds);
  const sourceRecipes = allSourceIds
    .map(id => getRecipeBySourceId(id))
    .filter(Boolean);

  const sourceRecipeMap = new Map(sourceRecipes.map(r => [r!.sourceId, r!]));

  const userPrompt = `Construct detailed recipe cards for these ${mealOutlines.length} meals:

${mealOutlines.map((meal, i) => {
    const sources = meal.sourceRecipeIds
      .map(id => sourceRecipeMap.get(id))
      .filter(Boolean)
      .map(r => `  - ${r!.name}: ${r!.ingredients.length} ingredients, ${r!.steps.length} steps`);

    return `
MEAL ${i + 1}: ${meal.name}
Description: ${meal.description}
Components: Main=${meal.components.main}, Carb=${meal.components.carb || 'none'}, Veggie=${meal.components.veggie || 'none'}
Estimated time: ${meal.estimatedTime} min
Source recipes:
${sources.join('\n')}`;
  }).join('\n\n')}

SOURCE RECIPE DETAILS:
${sourceRecipes.map(r => `
[${r!.sourceId}] ${r!.name} (${r!.servings} servings, ${r!.totalTimeMinutes} min)
Ingredients: ${r!.ingredients.map(i => `${i.quantity || ''} ${i.unit || ''} ${i.name}`).join(', ')}
Steps: ${r!.steps.join(' | ')}
`).join('\n')}

Construct the full recipe cards, scaling all ingredients to ${targetServings} servings.`;

  // Use simple call without tools - we already provide all recipe details
  const result = await callGeminiSimple(apiKey, systemPrompt, userPrompt);

  // Update progress
  const currentProgress = (batchIndex + 1) * mealOutlines.length;
  onProgress?.({
    phase: 'building',
    current: currentProgress,
    total: totalMeals,
    day: batchIndex + 1,
    message: `Building recipes ${currentProgress}/${totalMeals}`
  });

  // Extract JSON with better handling
  const parsed = extractAndParseJSON(result, 'meals');

  // Sanitize the meals to ensure quantity is always a number or null
  const sanitizedMeals = sanitizeMeals(parsed.meals as ComposedMeal[]);
  return sanitizedMeals;
}

/**
 * Sanitize meals to ensure quantity values are valid numbers or null.
 * Gemini sometimes outputs "to taste" as a string instead of null.
 */
function sanitizeMeals(meals: ComposedMeal[]): ComposedMeal[] {
  return meals.map(meal => ({
    ...meal,
    ingredients: meal.ingredients.map(ing => ({
      ...ing,
      // Convert non-numeric quantity to null
      quantity: typeof ing.quantity === 'number' ? ing.quantity : null
    }))
  }));
}

// ============================================================================
// Main Entry Point
// ============================================================================

export interface MealPlanResult {
  recipes: ComposedMeal[];
  defaultSelections: number[];
}

export async function generateMealPlan(
  apiKey: string,
  request: MealPlanRequest,
  onProgress?: (event: ProgressEvent) => void
): Promise<MealPlanResult> {
  const targetServings = request.preferences?.targetServings ?? 2;
  const numMeals = request.numDays ?? 16; // Default to 16 for faster generation

  console.log('[MealGen] Starting meal plan generation...');
  console.log('[MealGen] Preferences:', request.preferences);
  console.log(`[MealGen] Target meals: ${numMeals}`);

  // Stage 1: Plan meals
  console.log('[MealGen] Stage 1: Planning meals...');
  const mealOutlines = await planMeals(
    apiKey,
    request.pantryItems,
    request.preferences,
    request.recentRecipeHashes,
    targetServings,
    numMeals,
    onProgress
  );

  console.log(`[MealGen] Stage 1 complete: Got ${mealOutlines.length} meal outlines`);
  mealOutlines.forEach((m, i) => console.log(`  ${i + 1}. ${m.name}`));

  if (mealOutlines.length < numMeals) {
    console.warn(`[MealGen] Warning: Only got ${mealOutlines.length} meal outlines, expected ${numMeals}`);
  }

  // Stage 2: Construct recipes in batches of 2 (smaller batches = less truncation risk)
  console.log('[MealGen] Stage 2: Building recipe cards...');
  const BATCH_SIZE = 2;
  const allMeals: ComposedMeal[] = [];

  for (let i = 0; i < mealOutlines.length; i += BATCH_SIZE) {
    const batch = mealOutlines.slice(i, i + BATCH_SIZE);
    const batchIndex = Math.floor(i / BATCH_SIZE);

    onProgress?.({
      phase: 'building',
      current: i,
      total: mealOutlines.length,
      day: batchIndex + 1,
      message: `Building batch ${batchIndex + 1}`
    });

    console.log(`[MealGen] Building batch ${batchIndex + 1}: ${batch.map(b => b.name).join(', ')}`);

    const constructedMeals = await constructMealBatch(
      apiKey,
      batch,
      batchIndex,
      mealOutlines.length,
      targetServings,
      onProgress
    );
    allMeals.push(...constructedMeals);
    console.log(`[MealGen] Batch ${batchIndex + 1} complete: ${constructedMeals.length} meals`);
  }

  console.log(`[MealGen] Stage 2 complete: ${allMeals.length} total meals`);

  // Select default 6 with variety
  const defaultSelections = selectDefaultSix(allMeals);

  onProgress?.({
    phase: 'complete',
    current: allMeals.length,
    total: allMeals.length,
    message: 'Generation complete!'
  });

  console.log(`[MealGen] Complete! Generated ${allMeals.length} meals, default selections: ${defaultSelections}`);

  return {
    recipes: allMeals,
    defaultSelections
  };
}

// Select 6 defaults with variety
function selectDefaultSix(meals: ComposedMeal[]): number[] {
  const selected: number[] = [];
  const usedProteins = new Set<string>();
  const usedFormats = new Set<string>();

  // Prioritize meals using expiring ingredients first
  const expiringFirst = [...meals.entries()]
    .sort((a, b) => {
      const aExp = a[1].usesExpiringIngredients ? 0 : 1;
      const bExp = b[1].usesExpiringIngredients ? 0 : 1;
      return aExp - bExp;
    });

  for (const [index, meal] of expiringFirst) {
    if (selected.length >= 6) break;

    const tags = meal.tags.map(t => t.toLowerCase());
    const protein = tags.find(t =>
      ['chicken', 'beef', 'pork', 'seafood', 'fish', 'vegetarian', 'tofu'].includes(t)
    ) || 'other';
    const format = tags.find(t =>
      ['bowl', 'sandwich', 'burger', 'taco', 'wrap', 'pasta', 'soup', 'stew', 'salad'].includes(t)
    ) || 'other';

    if (selected.length < 4) {
      if (usedProteins.has(protein) || usedFormats.has(format)) {
        continue;
      }
    }

    selected.push(index);
    usedProteins.add(protein);
    usedFormats.add(format);
  }

  // Fill remaining slots
  for (let i = 0; i < meals.length && selected.length < 6; i++) {
    if (!selected.includes(i)) {
      selected.push(i);
    }
  }

  return selected.slice(0, 6);
}

// ============================================================================
// Grocery List Polishing
// ============================================================================

import type {
  GroceryIngredient,
  GroceryPolishResponse,
  GroceryPolishProgress,
  ShoppingItemForPantry,
  CategorizedPantryItem,
  PantryCategorizeProgress,
  PantryCategorizeResponse,
} from '../types.js';

const POLISH_BATCH_SIZE = 8; // Smaller batches to avoid truncation issues with gemini-3-flash-preview

/**
 * Polish a single batch of ingredients.
 */
async function polishBatch(
  genAI: GoogleGenAI,
  batch: GroceryIngredient[],
  pantryItems: PantryItem[]
): Promise<PolishedGroceryItem[]> {
  const ingredientsJson = JSON.stringify(batch.map(i => ({
    name: i.name,
    quantity: i.quantity,
    unit: i.unit
  })), null, 2);

  // Build pantry context for the prompt
  const pantryContext = pantryItems.length > 0
    ? `\nUSER'S PANTRY (items they already have - adjust shopping quantities accordingly):
${pantryItems.map(p => {
  const qty = p.quantity ? `${p.quantity} ${p.unit || ''}`.trim() : '';
  const avail = p.availability || '';
  return `- ${p.name}${qty ? `: ${qty}` : ''}${avail ? ` (${avail})` : ''}`;
}).join('\n')}

PANTRY RULES:
- If user has PLENTY or SOME of an item → exclude from shopping list
- If user has a specific numeric count → subtract from needed amount
  - Example: Recipe needs 3 carrots, user has 1 → show "Carrots: 2"
- If user has LOW or OUT_OF_STOCK for shelf-stable staples (flour, rice, sugar, spices) → include as "1 bag" or "1 jar"
  - These items are bought in bulk, not measured amounts
`
    : '';

  const prompt = `You are a grocery shopping assistant. Transform these recipe ingredients into a practical shopping list.
${pantryContext}

YOUR JOB:
1. MERGE similar items aggressively:
   - Combine "Yukon Gold Potatoes" + "Yukon Gold potato" → "Yukon Gold Potatoes"
   - Combine all regular mushroom varieties (button, cremini, white, brown) → "Mushrooms"
   - Keep specialty mushrooms separate (shiitake, oyster, enoki, chanterelle, porcini)
   - Combine onion varieties (yellow, white, brown) → "Onions" (keep red onion separate)
   - Combine all bell pepper colors unless recipe specifically needs a color

2. Convert to PRACTICAL SHOPPING quantities:

   CONVERT recipe cooking measures to METRIC shopping quantities:
   - 2 chicken breasts → "450g (2 pieces)"
   - 1 bunch fresh herbs → "1 bunch" (herbs stay as bunches)
   - 2 medium carrots → "2 medium carrots" (produce stays as counts)
   - Shelf-stable staples (rice, flour, sugar) → skip or "1 bag" if out of stock

   CRITICAL - NEVER use these in displayQuantity:
   - NO cups, tablespoons, teaspoons, or any spoon/cup measurements
   - NO decimal quantities like "0.5" or "0.38" - round to whole numbers or use fractions
   - NO precise decimals for weights - round to nearest 5 (500g, 225g, 125g, not 227g or 340g)

   ROUNDING RULES:
   - Always round UP for fractional items: 0.5 apple → "1 apple", 0.5 head lettuce → "1 small head"
   - Use approximate sizes instead of fractions: "1 small head" not "0.5 head"
   - Round weights to end in 0 or 5: 340g → 350g, 227g → 225g, 453g → 450g
   - For liquids: 1L, 500ml, 250ml, 125ml (not 118ml or 237ml)

   PRODUCE - Use counts with size qualifiers:
   - Whole items: "2 medium cucumbers", "4 carrots", "1 apple"
   - Heads/bunches: "1 small head iceberg lettuce", "1 medium head broccoli", "1 bunch kale"
   - Leafy herbs: "1 bunch cilantro", "1 bunch parsley"
   - Root vegetables sold by weight: "500g potatoes", "250g carrots"

   PROTEIN - Include piece count when applicable AND rounded weight:
   - "Chicken Breast: 450g (2 pieces)"
   - "Salmon Fillets: 350g (2 fillets)"
   - "Ground Beef: 500g"
   - "Eggs: 1 carton" (NOT individual eggs - a carton has 12 eggs)

   DRIED PASTA/NOODLES - Use packages, not grams:
   - Spaghetti, Linguine, Penne, etc. → "1 package" (packages are typically 500g)
   - Asian noodles (Lo Mein, Rice Noodles, Udon) → "1 package"
   - Only use grams if the recipe needs a very small amount (under 100g) → still round up to "1 package"
   - These are shelf-stable items bought in standard package sizes

   PANTRY - Exclude tiny recipe amounts:
   - Skip items like cornstarch, flour, sugar in small quantities - these are pantry staples
   - Only include if buying a container makes sense (e.g., "Coconut Milk: 1 can")

3. Remove items that don't need purchasing:
   - Salt, pepper, water, cooking oil, butter, basic spices
   - Tiny amounts of pantry staples

4. Assign a shopping category to each item

CATEGORIES (use exactly one):
Produce, Protein, Dairy, Bakery, Pantry, Frozen, Condiments, Spices

CATEGORY NOTES:
- Eggs → Protein (NOT Dairy)
- Dried pasta, noodles, rice, grains → Pantry
- Breadcrumbs, panko → Pantry
- Fresh pasta → Refrigerated/Dairy

Input ingredients:
${ingredientsJson}

Return a clean, merged shopping list as JSON:
{
  "items": [
    { "name": "Carrots", "displayQuantity": "4 medium", "category": "Produce" },
    { "name": "Iceberg Lettuce", "displayQuantity": "1 small head", "category": "Produce" },
    { "name": "Chicken Breast", "displayQuantity": "450g (2 pieces)", "category": "Protein" }
  ]
}`;

  const response = await genAI.models.generateContent({
    model: 'gemini-3-flash-preview',
    contents: prompt,
    config: {
      responseMimeType: 'application/json',
      temperature: 0.2,
      maxOutputTokens: 16000, // Increased to avoid truncation
      thinkingConfig: {
        thinkingLevel: ThinkingLevel.MEDIUM,
      },
    }
  });

  const text = response.text?.trim() || '{}';

  try {
    // Use extractAndParseJSON with repair logic for truncated responses
    const result = extractAndParseJSON(text, 'items') as unknown as GroceryPolishResponse;
    return result.items || [];
  } catch (parseError) {
    console.error(`[GroceryPolish] Batch parse error:`, parseError);
    console.error(`[GroceryPolish] Response: ${text.slice(0, 200)}...`);
    // Return fallback for this batch
    return batch.map(i => ({
      name: i.name,
      displayQuantity: `${i.quantity} ${i.unit}`.trim() || '1',
      category: 'Pantry'
    }));
  }
}

/**
 * Polish a grocery list using Gemini to convert to practical shopping quantities.
 * Processes in batches to avoid response truncation.
 */
export async function polishGroceryList(
  apiKey: string,
  ingredients: GroceryIngredient[],
  pantryItems: PantryItem[] = [],
  onProgress?: (progress: GroceryPolishProgress) => void
): Promise<GroceryPolishResponse> {
  const startTime = Date.now();
  console.log(`[GroceryPolish] Starting polish for ${ingredients.length} ingredients (pantry: ${pantryItems.length} items)`);

  const genAI = new GoogleGenAI({ apiKey });

  // Split into batches
  const batches: GroceryIngredient[][] = [];
  for (let i = 0; i < ingredients.length; i += POLISH_BATCH_SIZE) {
    batches.push(ingredients.slice(i, i + POLISH_BATCH_SIZE));
  }

  console.log(`[GroceryPolish] Processing ${batches.length} batches of ~${POLISH_BATCH_SIZE} items`);

  // Process all batches (could parallelize, but sequential is safer for rate limits)
  const allItems: PolishedGroceryItem[] = [];
  for (let i = 0; i < batches.length; i++) {
    const batchNum = i + 1;

    // Report progress
    onProgress?.({
      phase: 'polishing',
      currentBatch: batchNum,
      totalBatches: batches.length,
      message: `Polishing batch ${batchNum}/${batches.length}`
    });

    try {
      console.log(`[GroceryPolish] Processing batch ${batchNum}/${batches.length}`);
      const batchItems = await polishBatch(genAI, batches[i], pantryItems);
      allItems.push(...batchItems);
    } catch (error) {
      console.error(`[GroceryPolish] Batch ${batchNum} failed:`, error);
      // Add fallback items for failed batch
      allItems.push(...batches[i].map(ing => ({
        name: ing.name,
        displayQuantity: `${ing.quantity} ${ing.unit}`.trim() || '1',
        category: 'Pantry'
      })));
    }
  }

  // Final merge pass - have Gemini review the complete list for cross-batch duplicates
  console.log(`[GroceryPolish] Running final merge pass on ${allItems.length} items`);
  onProgress?.({
    phase: 'merging',
    currentBatch: batches.length,
    totalBatches: batches.length,
    message: 'Final merge check...'
  });

  const mergedItems = await finalMergePass(genAI, allItems);

  const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
  console.log(`[GroceryPolish] Polished ${ingredients.length} ingredients → ${mergedItems.length} items in ${elapsed}s`);

  onProgress?.({
    phase: 'complete',
    currentBatch: batches.length,
    totalBatches: batches.length,
    message: 'Polish complete'
  });

  return { items: mergedItems };
}

/**
 * Final merge pass - review the complete polished list for any duplicates
 * that may have ended up in different batches.
 */
async function finalMergePass(
  genAI: GoogleGenAI,
  items: PolishedGroceryItem[]
): Promise<PolishedGroceryItem[]> {
  if (items.length <= 1) return items;

  const prompt = `Review this shopping list and merge any duplicate or similar items.

CURRENT LIST:
${JSON.stringify(items, null, 2)}

YOUR JOB:
1. WHEN TO MERGE:
   - Exact duplicates: "Carrots" + "Carrots" → merge
   - Generic + specific: "Potatoes" + "Russet Potatoes" → merge into "Russet Potatoes"
   - Same item, different sizes: "1 large onion" + "2 medium onions" → merge

2. WHEN TO KEEP SEPARATE (DO NOT MERGE):
   - Two DIFFERENT specific varieties: "Russet Potatoes" + "Red Potatoes" → keep separate
   - Different mushroom types: "Button Mushrooms" + "Shiitake Mushrooms" → keep separate
   - Different pepper colors when both specified: "Red Bell Peppers" + "Green Bell Peppers" → keep separate
   - Specialty vs common: "Shiitake" stays separate from generic "Mushrooms"

3. When merging NUMERIC quantities:
   - Combine weights: "500g" + "250g" → "750g"
   - Combine counts: "2" + "3" → "5"

4. When merging NON-NUMERIC quantities (sizes):
   - Convert sizes to equivalent counts: 1 large ≈ 2 medium
   - "1 large potato" + "2 medium potatoes" → "4 medium potatoes"
   - Normalize to medium size when combining different sizes

5. Naming rules:
   - Keep the specific variety name when merging generic + specific
   - If one item has no variety and the other does, use the specific name

6. Return the COMPLETE list - don't drop any items

Respond with JSON:
{
  "items": [
    { "name": "...", "displayQuantity": "...", "category": "..." }
  ]
}`;

  try {
    const response = await genAI.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        responseMimeType: 'application/json',
        temperature: 0.1, // Very low for consistency
        maxOutputTokens: 65536, // Max tokens to prevent truncation on large lists
        thinkingConfig: {
          thinkingLevel: ThinkingLevel.MEDIUM,
        },
      }
    });

    const text = response.text?.trim() || '{}';
    const result = extractAndParseJSON(text, 'items') as unknown as GroceryPolishResponse;

    if (result.items && result.items.length > 0) {
      console.log(`[GroceryPolish] Final merge: ${items.length} → ${result.items.length} items`);
      return result.items;
    }
  } catch (error) {
    console.error('[GroceryPolish] Final merge failed, returning unmerged list:', error);
  }

  return items; // Fallback to unmerged list
}

// ============================================================================
// Pantry Categorization
// ============================================================================

/**
 * Categorize shopping items for pantry using Gemini AI.
 * Uses JSON response mode for fast single-call categorization.
 */
export async function categorizePantryItems(
  apiKey: string,
  items: ShoppingItemForPantry[],
  onProgress?: (progress: PantryCategorizeProgress) => void
): Promise<PantryCategorizeResponse> {
  const startTime = Date.now();
  console.log(`[PantryCategorize] Starting categorization for ${items.length} items`);

  onProgress?.({
    phase: 'categorizing',
    current: 0,
    total: items.length,
    message: 'Analyzing items...'
  });

  if (!apiKey) {
    console.error('[PantryCategorize] No API key provided');
    throw new Error('Gemini API key is required');
  }

  const ai = new GoogleGenAI({ apiKey });

  const prompt = `Categorize these shopping items for a home pantry. Return a JSON object with an "items" array.

INPUT ITEMS:
${items.map(item => `- ID: ${item.id}, Name: "${item.name}", Quantity: "${item.polishedDisplayQuantity}"`).join('\n')}

RULES - You MUST follow these exactly:

CATEGORY - Assign based on what the item IS:
- "PRODUCE" = Fresh vegetables, fruits, herbs (basil, parsley, chives, green onions, lettuce, tomatoes, carrots)
- "PROTEIN" = Fish, meat, poultry, seafood, tofu, EGGS (salmon, chicken, beef, shrimp, eggs)
- "DAIRY" = Cheese, milk, yogurt, butter, cream (mozzarella, parmesan, cheddar) - NOTE: Eggs are NOT dairy!
- "DRY_GOODS" = Bread, pasta, rice, flour, canned goods, cereals
- "SPICE" = Dried spices and seasonings (paprika, cumin, oregano)
- "OILS" = Cooking oils and vinegars (olive oil, vegetable oil, sesame oil, balsamic vinegar)
- "CONDIMENT" = Sauces, honey, mustard, soy sauce, ketchup
- "FROZEN" = Anything frozen (frozen peas, frozen berries)
- "OTHER" = Only if nothing else fits

TRACKING STYLE:
- "STOCK_LEVEL" with stockLevel="FULL" for: oils, spices, condiments, flour, sugar, rice
- "PRECISE" with stockLevel=null for: produce, proteins, dairy, frozen
- For EGGS: use "PRECISE" with unit="COUNT" - convert cartons to individual eggs (1 carton = 12 eggs)

QUANTITY/UNIT - IMPORTANT for portioned items:
- When an item has BOTH a count and weight (e.g. "2 chicken breasts (450g)"), PREFER THE COUNT:
  - "2 chicken breasts (450g)" → quantity: 2, unit: "COUNT" (NOT 450 GRAMS)
  - "4 pork chops (600g)" → quantity: 4, unit: "COUNT" (NOT 600 GRAMS)
  - "6 salmon fillets" → quantity: 6, unit: "COUNT"
- Use COUNT for: individual pieces (breasts, chops, fillets, steaks, thighs, drumsticks, sausages)
- Use GRAMS for: ground/minced meat, bulk items without piece count, or when only weight is given

EXPIRY DAYS - Set based on category:
- Fresh herbs (basil, parsley, chives, cilantro): 4
- Other produce: 7
- Fish/seafood: 3
- Poultry: 4
- Red meat: 5
- Eggs: 21
- Dairy/cheese: 10
- Bread: 5
- Oils, spices, condiments, dry goods: null (shelf stable)
- Frozen: null

PERISHABLE: true for PRODUCE, PROTEIN, DAIRY, FROZEN. false otherwise.

EXAMPLE OUTPUT:
{
  "items": [
    {"id": 1, "name": "Salmon Fillets", "quantity": 2, "unit": "COUNT", "category": "PROTEIN", "trackingStyle": "PRECISE", "stockLevel": null, "expiryDays": 3, "perishable": true},
    {"id": 2, "name": "Chicken Breasts", "quantity": 4, "unit": "COUNT", "category": "PROTEIN", "trackingStyle": "PRECISE", "stockLevel": null, "expiryDays": 4, "perishable": true},
    {"id": 3, "name": "Fresh Basil", "quantity": 1, "unit": "BUNCH", "category": "PRODUCE", "trackingStyle": "PRECISE", "stockLevel": null, "expiryDays": 4, "perishable": true},
    {"id": 4, "name": "Olive Oil", "quantity": 500, "unit": "MILLILITERS", "category": "OILS", "trackingStyle": "STOCK_LEVEL", "stockLevel": "FULL", "expiryDays": null, "perishable": false},
    {"id": 5, "name": "Ground Beef", "quantity": 500, "unit": "GRAMS", "category": "PROTEIN", "trackingStyle": "PRECISE", "stockLevel": null, "expiryDays": 5, "perishable": true},
    {"id": 6, "name": "Eggs", "quantity": 12, "unit": "COUNT", "category": "PROTEIN", "trackingStyle": "PRECISE", "stockLevel": null, "expiryDays": 21, "perishable": true}
  ]
}

Now categorize ALL ${items.length} input items. Use the EXACT id values from the input.`;

  try {
    console.log(`[PantryCategorize] Making Gemini API call...`);
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        responseMimeType: 'application/json',
        temperature: 0.2,
        maxOutputTokens: 16000,
        thinkingConfig: {
          thinkingLevel: ThinkingLevel.MEDIUM,
        },
      }
    });

    const text = response.text?.trim() || '{}';
    console.log(`[PantryCategorize] Got response (${text.length} chars)`);

    const result = extractAndParseJSON(text, 'items') as unknown as PantryCategorizeResponse;

    if (!result.items || result.items.length === 0) {
      console.warn('[PantryCategorize] No items in response, using fallback');
      return { items: items.map(createFallbackItem) };
    }

    // Validate and clean up results
    const categorizedItems: CategorizedPantryItem[] = result.items.map(item => ({
      id: Number(item.id),
      name: String(item.name || ''),
      quantity: Number(item.quantity) || 1,
      unit: String(item.unit || 'UNITS'),
      category: String(item.category || 'OTHER'),
      trackingStyle: String(item.trackingStyle || 'PRECISE'),
      stockLevel: item.trackingStyle === 'STOCK_LEVEL' ? (item.stockLevel || 'FULL') : null,
      expiryDays: item.expiryDays != null ? Number(item.expiryDays) : null,
      perishable: Boolean(item.perishable)
    }));

    // Check for missing items and add fallbacks
    const processedIds = new Set(categorizedItems.map(i => i.id));
    for (const item of items) {
      if (!processedIds.has(item.id)) {
        console.warn(`[PantryCategorize] Item ${item.id} (${item.name}) not in response, adding fallback`);
        categorizedItems.push(createFallbackItem(item));
      }
    }

    const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`[PantryCategorize] Completed: ${categorizedItems.length} items in ${elapsed}s`);

    onProgress?.({
      phase: 'complete',
      current: categorizedItems.length,
      total: items.length,
      message: 'Categorization complete'
    });

    return { items: categorizedItems };
  } catch (error) {
    console.error('[PantryCategorize] Error:', error);
    // Return fallback categorization for all items
    return { items: items.map(createFallbackItem) };
  }
}

/**
 * Create a fallback categorization for an item when AI fails
 */
function createFallbackItem(item: ShoppingItemForPantry): CategorizedPantryItem {
  // Parse quantity from display string
  const { quantity, unit } = parseQuantityFromDisplay(item.polishedDisplayQuantity);

  // Map shopping category to pantry category
  const category = mapShoppingCategory(item.shoppingCategory);

  // Determine tracking style and perishability
  const isPerishable = ['PRODUCE', 'PROTEIN', 'DAIRY'].includes(category);
  const trackingStyle = ['SPICE', 'OILS', 'CONDIMENT'].includes(category) ? 'STOCK_LEVEL' : 'PRECISE';

  return {
    id: item.id,
    name: item.name,
    quantity,
    unit,
    category,
    trackingStyle,
    stockLevel: trackingStyle === 'STOCK_LEVEL' ? 'FULL' : null,
    expiryDays: isPerishable ? 7 : null,
    perishable: isPerishable
  };
}

/**
 * Parse quantity and unit from display string
 */
function parseQuantityFromDisplay(display: string): { quantity: number; unit: string } {
  if (!display) return { quantity: 1, unit: 'UNITS' };

  const trimmed = display.trim();

  // Pattern: "500g" or "250ml"
  const weightMatch = trimmed.match(/^(\d+(?:\.\d+)?)\s*(g|kg|ml|l|L)$/i);
  if (weightMatch) {
    let qty = parseFloat(weightMatch[1]);
    const unitStr = weightMatch[2].toLowerCase();
    if (unitStr === 'kg') { qty *= 1000; return { quantity: qty, unit: 'GRAMS' }; }
    if (unitStr === 'l') { qty *= 1000; return { quantity: qty, unit: 'MILLILITERS' }; }
    if (unitStr === 'g') return { quantity: qty, unit: 'GRAMS' };
    if (unitStr === 'ml') return { quantity: qty, unit: 'MILLILITERS' };
  }

  // Pattern: "2 pieces", "3 medium"
  const countMatch = trimmed.match(/^(\d+(?:\.\d+)?)\s+(.+)$/);
  if (countMatch) {
    const qty = parseFloat(countMatch[1]);
    const unitPart = countMatch[2].toLowerCase();
    if (unitPart.includes('bunch')) return { quantity: qty, unit: 'BUNCH' };
    if (unitPart.includes('piece') || unitPart.includes('fillet')) return { quantity: qty, unit: 'PIECES' };
    return { quantity: qty, unit: 'UNITS' };
  }

  // Pattern: Just a number
  const numMatch = trimmed.match(/^(\d+(?:\.\d+)?)$/);
  if (numMatch) {
    return { quantity: parseFloat(numMatch[1]), unit: 'UNITS' };
  }

  return { quantity: 1, unit: 'UNITS' };
}

/**
 * Map shopping category to pantry category
 */
function mapShoppingCategory(shoppingCategory: string): string {
  const cat = (shoppingCategory || '').toLowerCase();

  if (cat.includes('produce') || cat.includes('vegetable') || cat.includes('fruit')) return 'PRODUCE';
  if (cat.includes('protein') || cat.includes('meat') || cat.includes('seafood') || cat.includes('fish')) return 'PROTEIN';
  if (cat.includes('dairy') || cat.includes('milk') || cat.includes('cheese')) return 'DAIRY';
  if (cat.includes('pantry') || cat.includes('dry') || cat.includes('grain') || cat.includes('pasta')) return 'DRY_GOODS';
  if (cat.includes('spice') || cat.includes('herb') || cat.includes('seasoning')) return 'SPICE';
  if (cat.includes('oil') || cat.includes('vinegar')) return 'OILS';
  if (cat.includes('condiment') || cat.includes('sauce')) return 'CONDIMENT';
  if (cat.includes('frozen')) return 'FROZEN';
  if (cat.includes('bakery') || cat.includes('bread')) return 'DRY_GOODS';

  return 'OTHER';
}

// ============================================================================
// Ingredient Substitution
// ============================================================================

/**
 * Process an ingredient substitution using Gemini AI.
 * Handles updating recipe names and adjusting quantities when ingredients are swapped.
 *
 * Examples:
 * - "Salmon" → "Tilapia" in "Honey Garlic Salmon" → "Honey Garlic Tilapia"
 * - "Fresh Thyme" → "Dried Thyme" → quantity reduced to 1/3
 * - "Fresh Corn" → "Frozen Corn" → name updated, quantity unchanged
 */
export async function processSubstitution(
  apiKey: string,
  request: SubstitutionRequest
): Promise<SubstitutionResponse> {
  console.log(`[Substitution] Processing: ${request.originalIngredient.name} → ${request.newIngredientName} in "${request.recipeName}"`);

  const ai = new GoogleGenAI({ apiKey });

  // Format steps for the prompt
  const stepsText = request.steps.map((step, i) =>
    `Step ${i + 1}: ${step.title}\n${step.substeps.map(s => `  - ${s}`).join('\n')}`
  ).join('\n\n');

  const preparationText = request.originalIngredient.preparation
    ? ` (preparation: "${request.originalIngredient.preparation}")`
    : '';

  const prompt = `You are a culinary expert helping with ingredient substitutions in recipes.

TASK: A cook is substituting an ingredient in a recipe. You must:
1. Determine if the recipe NAME should be updated
2. CONVERT the ingredient quantity appropriately for the substitution
3. UPDATE the preparation style if needed (or set to null if not applicable)
4. UPDATE any recipe instructions that reference the original ingredient
5. Provide any helpful cooking notes

RECIPE: "${request.recipeName}"
ORIGINAL INGREDIENT: ${request.originalIngredient.quantity} ${request.originalIngredient.unit} ${request.originalIngredient.name}${preparationText}
NEW INGREDIENT: ${request.newIngredientName}

RECIPE STEPS:
${stepsText}

CRITICAL - DETECT SUBSTITUTION TYPE FROM INGREDIENT NAMES:
- Look at BOTH the original name ("${request.originalIngredient.name}") and new name ("${request.newIngredientName}")
- "Fresh X" → "Dried X" = fresh-to-dried substitution (use 1/3 amount, change unit)
- "Dried X" → "Fresh X" = dried-to-fresh substitution (use 3x amount)
- Check for words like "fresh", "dried", "frozen", "canned", "ground", "powdered" in the names

QUANTITY CONVERSION RULES:

For FRESH HERBS → DRIED HERBS (e.g., "Fresh Basil" → "Dried Basil"):
- Use 1/3 the amount
- Convert imprecise units: 1 handful fresh ≈ 1 tbsp dried, 1 bunch fresh ≈ 2 tbsp dried
- Change unit to tbsp or tsp (dried herbs are never measured in handfuls/bunches)

For DRIED HERBS → FRESH HERBS:
- Use 3x the amount
- 1 tsp dried ≈ 1 tbsp fresh

For FRESH GARLIC → GARLIC POWDER: 1 clove ≈ 1/4 tsp powder
For FRESH GINGER → GROUND GINGER: 1 tbsp fresh ≈ 1/4 tsp ground
For ONION → ONION POWDER: 1 medium onion ≈ 1 tbsp powder

For PROTEIN SWAPS (salmon↔tilapia, chicken↔pork): Keep same weight
For FRESH↔FROZEN vegetables: Same quantity

PREPARATION STYLE UPDATES:
- The "preparation" field describes how to prep the ingredient (e.g., "torn", "minced", "diced")
- For fresh→dried herb substitutions: set preparation to null (dried herbs don't need tearing/chopping)
- For other substitutions: update preparation if it no longer applies, or keep it if still relevant
- Examples:
  - Fresh basil "torn" → Dried basil: preparation = null
  - Fresh garlic "minced" → Garlic powder: preparation = null
  - Chicken breast "diced" → Pork tenderloin "diced": preparation = "diced" (unchanged)

RECIPE NAME UPDATES:
- Update ONLY if the original ingredient appears in the recipe name
- "Honey Garlic Salmon" + (Salmon → Tilapia) = "Honey Garlic Tilapia"
- "Classic Tomato Pasta" + (Fresh Basil → Dried Basil) = "Classic Tomato Pasta" (unchanged - basil not in name)

RECIPE STEP UPDATES:
- Update any steps that mention the original ingredient to use the new ingredient
- Adjust preparation instructions appropriately:
  - Fresh herbs "torn" or "chopped" → Dried herbs "crumbled" or just added directly
  - Fresh garlic "minced" → Garlic powder just "added" or "sprinkled"
  - If a step says "garnish with fresh basil", change to appropriate dried herb instruction
- Keep step structure (title and substeps) the same, just update the text
- Return ALL steps, even unchanged ones

Return JSON (you MUST convert quantities for herb substitutions):
{
  "updatedRecipeName": "recipe name (same if ingredient not in name)",
  "updatedIngredient": {
    "name": "the new ingredient name exactly as provided",
    "quantity": 1.0,
    "unit": "tbsp",
    "preparation": null
  },
  "updatedSteps": [
    {"title": "Step title", "substeps": ["substep 1", "substep 2"]}
  ],
  "notes": "cooking tips for this substitution, or null"
}`;

  try {
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        responseMimeType: 'application/json',
        temperature: 0.2,
        maxOutputTokens: 4000,  // Increased for full recipe steps
        thinkingConfig: {
          thinkingLevel: ThinkingLevel.LOW,
        },
      }
    });

    const text = response.text?.trim() || '{}';
    console.log(`[Substitution] Got response: ${text.slice(0, 300)}`);

    const result = JSON.parse(text) as SubstitutionResponse;

    // Validate the response
    if (!result.updatedRecipeName || !result.updatedIngredient || !result.updatedSteps) {
      throw new Error('Invalid response structure');
    }

    console.log(`[Substitution] Result: "${result.updatedRecipeName}", ${result.updatedIngredient.quantity} ${result.updatedIngredient.unit} ${result.updatedIngredient.name}, ${result.updatedSteps.length} steps`);

    return result;
  } catch (error) {
    console.error('[Substitution] Error:', error);
    // Fallback: return original values with steps unchanged
    return {
      updatedRecipeName: request.recipeName,
      updatedIngredient: {
        name: request.newIngredientName,
        quantity: request.originalIngredient.quantity,
        unit: request.originalIngredient.unit,
        preparation: request.originalIngredient.preparation,
      },
      updatedSteps: request.steps,  // Return original steps unchanged
      notes: null,
    };
  }
}

/**
 * Process a free-form recipe customization request using Gemini.
 * Handles requests like "make it vegetarian", "use jarred pesto", "make it spicier", etc.
 */
export async function customizeRecipe(
  apiKey: string,
  request: RecipeCustomizationRequest
): Promise<RecipeCustomizationResponse> {
  console.log(`[Customization] Processing: "${request.customizationRequest}" for "${request.recipeName}"`);

  const ai = new GoogleGenAI({ apiKey });

  // Format ingredients for the prompt
  const ingredientsText = request.ingredients.map(ing => {
    const qty = ing.quantity !== null ? `${ing.quantity} ${ing.unit}` : ing.unit;
    const prep = ing.preparation ? ` (${ing.preparation})` : '';
    return `- ${qty} ${ing.ingredientName}${prep}`;
  }).join('\n');

  // Format steps for the prompt
  const stepsText = request.steps.map((step, i) =>
    `Step ${i + 1}: ${step.title}\n${step.substeps.map(s => `  - ${s}`).join('\n')}`
  ).join('\n\n');

  // Include previous requests for context in refine loop
  const previousContext = request.previousRequests && request.previousRequests.length > 0
    ? `\nPREVIOUS CUSTOMIZATION REQUESTS (for context):\n${request.previousRequests.map((r, i) => `${i + 1}. "${r}"`).join('\n')}\n`
    : '';

  const prompt = `You are a professional culinary consultant specializing in recipe adaptation.

TASK: Customize a recipe based on the user's request. You must:
1. Interpret the user's natural language request
2. Determine which ingredients need to be ADDED, REMOVED, or MODIFIED
3. Update the recipe steps to reflect these changes
4. Update the recipe name and description if the main ingredient or cooking style changes
5. Provide a brief summary of what you changed

RECIPE: "${request.recipeName}"

CURRENT DESCRIPTION:
${request.description || 'No description provided'}

CURRENT INGREDIENTS:
${ingredientsText}

CURRENT STEPS:
${stepsText}
${previousContext}
USER'S CUSTOMIZATION REQUEST: "${request.customizationRequest}"

CUSTOMIZATION GUIDELINES:

For DIETARY CHANGES (vegetarian, vegan, gluten-free):
- Identify ingredients that violate the diet
- Suggest appropriate substitutes with converted quantities
- Update recipe name to reflect the change (e.g., "Vegetarian Pad Thai")

For INGREDIENT SUBSTITUTIONS (use jarred pesto, canned tomatoes):
- Remove ingredients being replaced
- Add the substitute ingredient with appropriate quantity
- Consider if other ingredients should be adjusted
- Update steps to reflect simpler/different preparation

For FLAVOR ADJUSTMENTS (make it spicier, less salty):
- Modify quantities of existing seasonings
- Add new spices/seasonings if needed
- Note the changes in the summary

For SIMPLIFICATION (reduce prep time, fewer ingredients):
- Identify ingredients that can be omitted or combined
- Suggest pre-made alternatives where appropriate
- Update steps to be simpler

RESPONSE RULES:
- Only include ingredients in "ingredientsToAdd" if they are NEW (not in original list)
- Only include ingredients in "ingredientsToRemove" if they should be completely removed
- Only include ingredients in "ingredientsToModify" if quantity/unit/preparation changes
- If an ingredient is being REPLACED, put the old one in "ingredientsToRemove" and the new one in "ingredientsToAdd"
- Keep original ingredients unchanged if they don't need modification
- Return ALL steps (even unchanged ones) in "updatedSteps"
- The "changesSummary" should be 1-2 sentences explaining what was done
- Use "notes" for any cooking tips related to the changes

Return JSON:
{
  "updatedRecipeName": "New recipe name or original if unchanged",
  "updatedDescription": "Updated recipe description reflecting the changes",
  "ingredientsToAdd": [
    {"ingredientName": "name", "quantity": 1.0, "unit": "cup", "preparation": "diced or null"}
  ],
  "ingredientsToRemove": ["ingredient name 1", "ingredient name 2"],
  "ingredientsToModify": [
    {"originalName": "original ingredient name", "newName": "new name or null", "newQuantity": 2.0, "newUnit": "tbsp", "newPreparation": "minced or null"}
  ],
  "updatedSteps": [
    {"title": "Step title", "substeps": ["substep 1", "substep 2"]}
  ],
  "changesSummary": "Brief description of changes made",
  "notes": "Optional cooking tips or null"
}`;

  try {
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        responseMimeType: 'application/json',
        temperature: 0.3,
        maxOutputTokens: 8000,  // Larger for full recipe with all steps
        thinkingConfig: {
          thinkingLevel: ThinkingLevel.MEDIUM,
        },
      }
    });

    const text = response.text?.trim() || '{}';
    console.log(`[Customization] Got response: ${text.slice(0, 500)}...`);

    const result = JSON.parse(text) as RecipeCustomizationResponse;

    // Validate the response
    if (!result.updatedRecipeName || !result.updatedSteps || !result.changesSummary) {
      throw new Error('Invalid response structure');
    }

    // Ensure arrays exist even if empty
    result.ingredientsToAdd = result.ingredientsToAdd || [];
    result.ingredientsToRemove = result.ingredientsToRemove || [];
    result.ingredientsToModify = result.ingredientsToModify || [];
    // Use original description as fallback if Gemini didn't provide updated one
    result.updatedDescription = result.updatedDescription || request.description || '';

    console.log(`[Customization] Result: "${result.updatedRecipeName}", +${result.ingredientsToAdd.length} -${result.ingredientsToRemove.length} ~${result.ingredientsToModify.length}`);

    return result;
  } catch (error) {
    console.error('[Customization] Error:', error);
    throw error;  // Let the route handler deal with the error
  }
}

// ============================================================================
// Shopping List Update (for Recipe Customization)
// ============================================================================

/**
 * Update a shopping list after recipe customization using Gemini AI.
 * Handles adding, removing, and modifying ingredients intelligently.
 */
export async function updateShoppingListForCustomization(
  apiKey: string,
  currentItems: PolishedGroceryItem[],
  ingredientsToAdd: RecipeIngredient[],
  ingredientsToRemove: RecipeIngredient[],
  ingredientsToModify: ModifiedIngredient[],
  recipeName: string
): Promise<{ items: PolishedGroceryItem[] }> {
  console.log(`[ShoppingUpdate] Updating shopping list for "${recipeName}"`);
  console.log(`[ShoppingUpdate] Current items: ${currentItems.length}, Add: ${ingredientsToAdd.length}, Remove: ${ingredientsToRemove.length}, Modify: ${ingredientsToModify.length}`);

  if (!apiKey) {
    throw new Error('Gemini API key is required');
  }

  // If no changes, return current items unchanged
  if (ingredientsToAdd.length === 0 && ingredientsToRemove.length === 0 && ingredientsToModify.length === 0) {
    console.log('[ShoppingUpdate] No changes to apply');
    return { items: currentItems };
  }

  const ai = new GoogleGenAI({ apiKey });

  // Build the changes description
  const changesDescription = [];

  if (ingredientsToRemove.length > 0) {
    changesDescription.push(`REMOVE from "${recipeName}":\n${ingredientsToRemove.map(i =>
      `  - ${i.quantity} ${i.unit} ${i.ingredientName}`.trim()
    ).join('\n')}`);
  }

  if (ingredientsToAdd.length > 0) {
    changesDescription.push(`ADD from "${recipeName}":\n${ingredientsToAdd.map(i =>
      `  - ${i.quantity} ${i.unit} ${i.ingredientName}${i.preparation ? ` (${i.preparation})` : ''}`.trim()
    ).join('\n')}`);
  }

  if (ingredientsToModify.length > 0) {
    changesDescription.push(`MODIFY:\n${ingredientsToModify.map(m => {
      const changes = [];
      if (m.newName) changes.push(`name: "${m.originalName}" → "${m.newName}"`);
      if (m.newQuantity !== null) changes.push(`qty: ${m.newQuantity}`);
      if (m.newUnit) changes.push(`unit: ${m.newUnit}`);
      return `  - ${m.originalName}: ${changes.join(', ')}`;
    }).join('\n')}`);
  }

  const prompt = `You are updating an existing shopping list after a recipe was customized.

CURRENT SHOPPING LIST:
${JSON.stringify(currentItems, null, 2)}

CHANGES TO APPLY:
${changesDescription.join('\n\n')}

YOUR TASK:

1. For REMOVED ingredients:
   - Find the matching shopping item (use semantic matching: "shrimp" matches "Shrimp (peeled and deveined)")
   - SUBTRACT the removed quantity from the shopping item
   - If the quantity reaches 0 or below, REMOVE the item entirely
   - Example: "Chicken Thighs: 450g (4 pieces)" minus "2 pieces chicken thighs" = "Chicken Thighs: 225g (2 pieces)"

2. For ADDED ingredients:
   - Check if a similar item already exists (semantic match)
   - If exists: ADD the quantities together
   - If new: Create a polished entry
   - Use the SAME formatting as existing items

3. For MODIFIED ingredients:
   - Find and update the matching item's name/quantity as specified

4. KEEP all unchanged items EXACTLY as they are (same name, displayQuantity, category)

FORMATTING RULES (same as grocery polish):
- Use METRIC units: grams for protein (not pounds), ml for liquids
- Protein format: "450g (2 pieces)" or "500g"
- Produce format: "2 medium carrots", "1 small head lettuce"
- Proper capitalization: "Chicken Thighs" not "chicken thighs"
- Round weights to end in 0 or 5: 340g → 350g, 227g → 225g
- NO cups, tablespoons, teaspoons in displayQuantity

CATEGORIES (use exactly one):
Produce, Protein, Dairy, Bakery, Pantry, Frozen, Condiments, Spices

Return the COMPLETE updated shopping list as JSON:
{
  "items": [
    { "name": "...", "displayQuantity": "...", "category": "..." },
    ...
  ]
}`;

  try {
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: prompt,
      config: {
        responseMimeType: 'application/json',
        temperature: 0.2,
        maxOutputTokens: 16000,
        thinkingConfig: {
          thinkingLevel: ThinkingLevel.MEDIUM,
        },
      }
    });

    const text = response.text?.trim() || '{}';
    console.log('[ShoppingUpdate] Got response, parsing...');
    console.log('[ShoppingUpdate] Raw response (first 500 chars):', text.substring(0, 500));

    // Parse the response - handle both {"items": [...]} and bare [...] formats
    let result: { items: PolishedGroceryItem[] };

    // Check if Gemini returned a bare array instead of {"items": [...]}
    if (text.startsWith('[')) {
      try {
        const parsed = JSON.parse(text);
        if (Array.isArray(parsed)) {
          console.log('[ShoppingUpdate] Response was bare array, wrapping');
          result = { items: parsed };
        } else {
          throw new Error('Unexpected non-array starting with [');
        }
      } catch (e) {
        console.log('[ShoppingUpdate] Bare array parse failed, trying extractAndParseJSON');
        result = extractAndParseJSON(text, 'items') as { items: PolishedGroceryItem[] };
      }
    } else {
      result = extractAndParseJSON(text, 'items') as { items: PolishedGroceryItem[] };
    }

    if (!result.items || !Array.isArray(result.items)) {
      console.error('[ShoppingUpdate] Invalid response structure:', JSON.stringify(result).substring(0, 200));
      throw new Error('Invalid response structure - missing items array');
    }

    console.log(`[ShoppingUpdate] Updated: ${currentItems.length} → ${result.items.length} items`);

    return result;
  } catch (error) {
    console.error('[ShoppingUpdate] Error:', error);
    throw error;
  }
}
