/**
 * Recipe Dataset Import Script
 *
 * Parses the Food.com recipes CSV and imports into the app database.
 * Creates a canonical ingredient database with standardized units.
 */

import * as fs from 'fs';
import * as readline from 'readline';
import { normalizeIngredientName, detectCategory } from '../src/db/ingredients.js';

// ============================================================================
// Types
// ============================================================================

export interface ParsedIngredient {
  quantity: number | null;
  unit: string | null;
  name: string;
  preparation: string | null;
  raw: string;
}

export interface CanonicalIngredient {
  id?: number;
  name: string;                    // Normalized name: "chicken breast"
  category: string;                // "protein", "produce", "dairy", etc.
  defaultUnit: string;             // Standard unit: "g", "ml", "pcs"
  conversionToMetric: number;      // e.g., 1 cup flour = 120g, so 120
  aliases: string[];               // Alternative names
}

export interface ImportedRecipe {
  id?: number;
  sourceId: number;                // Original Food.com ID
  name: string;
  description: string;
  servings: number;
  servingSizeGrams: number | null;
  prepTimeMinutes: number | null;
  cookTimeMinutes: number | null;
  totalTimeMinutes: number | null;
  ingredients: ParsedIngredient[];
  steps: string[];
  tags: string[];
  searchTerms: string[];
  category: string | null;         // "dinner", "breakfast", "dessert", etc.
  cuisines: string[];              // "italian", "mexican", "asian", etc.
  dietaryFlags: string[];          // "vegetarian", "vegan", "gluten-free", etc.
}

// ============================================================================
// Parsing Utilities
// ============================================================================

/**
 * Parse Python-style list string: ['item1', 'item2'] or ["item1", "item2"]
 */
export function parsePythonList(str: string): string[] {
  if (!str || str === 'NA' || str === 'character(0)') return [];

  // Remove outer brackets and whitespace
  str = str.trim();
  if (!str.startsWith('[')) return [];

  const inner = str.slice(1, -1).trim();
  if (!inner) return [];

  const items: string[] = [];
  let current = '';
  let inQuotes = false;
  let quoteChar = '';
  let i = 0;

  while (i < inner.length) {
    const char = inner[i];

    if (!inQuotes && (char === '"' || char === "'")) {
      inQuotes = true;
      quoteChar = char;
      i++;
      continue;
    }

    if (inQuotes && char === quoteChar) {
      // Check for escaped quote
      if (i + 1 < inner.length && inner[i + 1] === quoteChar) {
        current += char;
        i += 2;
        continue;
      }
      inQuotes = false;
      items.push(current.trim());
      current = '';
      i++;
      // Skip to next comma or end
      while (i < inner.length && inner[i] !== ',') i++;
      i++; // Skip comma
      continue;
    }

    if (inQuotes) {
      current += char;
    }
    i++;
  }

  return items.filter(item => item.length > 0);
}

/**
 * Parse Python-style set string: {'item1', 'item2'}
 */
export function parsePythonSet(str: string): string[] {
  if (!str || str === 'NA') return [];
  str = str.trim();
  if (!str.startsWith('{')) return [];

  // Convert to list format and parse
  return parsePythonList('[' + str.slice(1, -1) + ']');
}

/**
 * Parse an ingredient string like "4 cups water" or "1 (14 ounce) can diced tomatoes"
 */
export function parseIngredientString(raw: string): ParsedIngredient {
  const original = raw.trim();
  let str = original;

  // Handle empty or minimal strings
  if (!str || str.length < 2) {
    return { quantity: null, unit: null, name: str || '', preparation: null, raw: original };
  }

  // Extract preparation notes (after comma)
  let preparation: string | null = null;
  const commaIdx = str.indexOf(',');
  if (commaIdx > 0) {
    preparation = str.slice(commaIdx + 1).trim();
    str = str.slice(0, commaIdx).trim();
  }

  // Pattern for quantity: handles "1", "1/2", "1 1/2", "1-2", etc.
  const qtyPattern = /^(\d+(?:\s*[-\/]\s*\d+)?(?:\s+\d+\/\d+)?)\s*/;
  const qtyMatch = str.match(qtyPattern);

  let quantity: number | null = null;
  if (qtyMatch) {
    quantity = parseQuantity(qtyMatch[1]);
    str = str.slice(qtyMatch[0].length);
  }

  // Check for parenthetical size info: "(14 ounce) can"
  const parenPattern = /^\((\d+(?:\s*[-\/]\s*\d+)?)\s*(\w+)\)\s*/;
  const parenMatch = str.match(parenPattern);

  let unit: string | null = null;

  if (parenMatch) {
    // e.g., "(14 ounce) can diced tomatoes" -> 14 oz can
    // e.g., "2 (8 ounce) bottles clam juice" -> 16 oz clam juice
    const parenQty = parseQuantity(parenMatch[1]);
    const parenUnit = parenMatch[2].toLowerCase();
    str = str.slice(parenMatch[0].length);

    // Get the container type (can, bottle, package, etc.) - handles plural forms
    const containerPattern = /^(cans?|bottles?|packages?|bags?|boxes?|jars?)\s+/i;
    const containerMatch = str.match(containerPattern);

    if (containerMatch) {
      // Combine: quantity * parenQty parenUnit
      if (quantity && parenQty) {
        quantity = quantity * parenQty;
      } else if (parenQty) {
        quantity = parenQty;
      }
      unit = parenUnit;
      str = str.slice(containerMatch[0].length);
    } else {
      unit = parenUnit;
    }
  } else {
    // Standard unit pattern
    const unitPattern = /^(cups?|tablespoons?|tbsps?|teaspoons?|tsps?|ounces?|oz|pounds?|lbs?|grams?|g|kilograms?|kg|milliliters?|ml|liters?|l|cloves?|stalks?|heads?|bunche?s?|pieces?|pcs?|slices?|large|medium|small|whole|pinch|dash|sprigs?|leaves?|cans?|bottles?|packages?|bags?|boxes?|jars?)\s+/i;
    const unitMatch = str.match(unitPattern);

    if (unitMatch) {
      unit = normalizeUnitName(unitMatch[1]);
      str = str.slice(unitMatch[0].length);
    }
  }

  // Remaining string is the ingredient name - normalize it
  const rawName = str.trim().toLowerCase();
  const name = normalizeIngredientName(rawName);

  return {
    quantity,
    unit,
    name,
    preparation,
    raw: original
  };
}

/**
 * Parse quantity string like "1", "1/2", "1 1/2", "1-2"
 */
function parseQuantity(str: string): number {
  str = str.trim();

  // Handle range: "1-2" -> take average
  if (str.includes('-') && !str.includes('/')) {
    const parts = str.split('-').map(s => parseQuantity(s.trim()));
    return (parts[0] + parts[1]) / 2;
  }

  // Handle mixed number: "1 1/2"
  const mixedMatch = str.match(/^(\d+)\s+(\d+)\/(\d+)$/);
  if (mixedMatch) {
    const whole = parseInt(mixedMatch[1]);
    const num = parseInt(mixedMatch[2]);
    const denom = parseInt(mixedMatch[3]);
    return whole + num / denom;
  }

  // Handle fraction: "1/2"
  const fracMatch = str.match(/^(\d+)\/(\d+)$/);
  if (fracMatch) {
    return parseInt(fracMatch[1]) / parseInt(fracMatch[2]);
  }

  // Plain number
  return parseFloat(str) || 0;
}

/**
 * Normalize unit names to standard forms
 */
function normalizeUnitName(unit: string): string {
  const lower = unit.toLowerCase();

  const mappings: Record<string, string> = {
    'cup': 'cup', 'cups': 'cup',
    'tablespoon': 'tbsp', 'tablespoons': 'tbsp', 'tbsp': 'tbsp', 'tbsps': 'tbsp',
    'teaspoon': 'tsp', 'teaspoons': 'tsp', 'tsp': 'tsp', 'tsps': 'tsp',
    'ounce': 'oz', 'ounces': 'oz', 'oz': 'oz',
    'pound': 'lb', 'pounds': 'lb', 'lb': 'lb', 'lbs': 'lb',
    'gram': 'g', 'grams': 'g', 'g': 'g',
    'kilogram': 'kg', 'kilograms': 'kg', 'kg': 'kg',
    'milliliter': 'ml', 'milliliters': 'ml', 'ml': 'ml',
    'liter': 'l', 'liters': 'l', 'l': 'l',
    'clove': 'clove', 'cloves': 'clove',
    'stalk': 'stalk', 'stalks': 'stalk',
    'head': 'head', 'heads': 'head',
    'bunch': 'bunch', 'bunches': 'bunch',
    'piece': 'pcs', 'pieces': 'pcs', 'pc': 'pcs', 'pcs': 'pcs',
    'slice': 'slice', 'slices': 'slice',
    'can': 'can', 'cans': 'can',
    'bottle': 'bottle', 'bottles': 'bottle',
    'package': 'package', 'packages': 'package',
    'bag': 'bag', 'bags': 'bag',
    'box': 'box', 'boxes': 'box',
    'jar': 'jar', 'jars': 'jar',
    'sprig': 'sprig', 'sprigs': 'sprig',
    'leaf': 'leaf', 'leaves': 'leaf',
    'large': 'large', 'medium': 'medium', 'small': 'small',
    'whole': 'whole', 'pinch': 'pinch', 'dash': 'dash',
  };

  return mappings[lower] || lower;
}

/**
 * Parse serving size string like "1 (155 g)" -> 155
 */
export function parseServingSize(str: string): number | null {
  if (!str || str === 'NA') return null;

  const match = str.match(/\((\d+)\s*g\)/);
  if (match) {
    return parseInt(match[1]);
  }
  return null;
}

// categorizeIngredient is imported from ingredients.ts as detectCategory

/**
 * Extract cuisine tags from recipe tags
 */
export function extractCuisines(tags: string[]): string[] {
  const cuisineKeywords = [
    'italian', 'mexican', 'chinese', 'japanese', 'korean', 'thai', 'vietnamese',
    'indian', 'greek', 'french', 'spanish', 'mediterranean', 'middle-eastern',
    'moroccan', 'caribbean', 'brazilian', 'peruvian', 'german', 'british',
    'irish', 'american', 'southern', 'cajun', 'tex-mex', 'asian', 'european',
    'african', 'hawaiian', 'australian', 'scandinavian', 'polish', 'russian'
  ];

  return tags.filter(tag =>
    cuisineKeywords.some(cuisine => tag.toLowerCase().includes(cuisine))
  );
}

/**
 * Extract dietary flags from recipe tags
 */
export function extractDietaryFlags(tags: string[]): string[] {
  const dietaryKeywords = [
    'vegetarian', 'vegan', 'gluten-free', 'dairy-free', 'low-carb', 'low-fat',
    'low-sodium', 'low-calorie', 'keto', 'paleo', 'whole30', 'diabetic',
    'heart-healthy', 'high-protein', 'high-fiber', 'kosher', 'halal'
  ];

  return tags.filter(tag =>
    dietaryKeywords.some(diet => tag.toLowerCase().includes(diet))
  );
}

/**
 * Extract meal category from tags
 */
export function extractCategory(tags: string[], searchTerms: string[]): string | null {
  const allTerms = [...tags, ...searchTerms].map(t => t.toLowerCase());

  if (allTerms.some(t => t.includes('breakfast') || t.includes('brunch'))) return 'breakfast';
  if (allTerms.some(t => t.includes('lunch'))) return 'lunch';
  if (allTerms.some(t => t.includes('dinner') || t.includes('main-dish'))) return 'dinner';
  if (allTerms.some(t => t.includes('dessert') || t.includes('sweet'))) return 'dessert';
  if (allTerms.some(t => t.includes('appetizer') || t.includes('snack'))) return 'appetizer';
  if (allTerms.some(t => t.includes('side'))) return 'side';
  if (allTerms.some(t => t.includes('soup') || t.includes('stew'))) return 'soup';
  if (allTerms.some(t => t.includes('salad'))) return 'salad';
  if (allTerms.some(t => t.includes('beverage') || t.includes('drink'))) return 'beverage';

  return null;
}

/**
 * Extract time estimate from tags like "30-minutes-or-less", "60-minutes-or-less"
 */
export function extractTimeFromTags(tags: string[]): number | null {
  for (const tag of tags) {
    const match = tag.match(/^(\d+)-minutes-or-less$/);
    if (match) {
      return parseInt(match[1]);
    }
  }
  // Check for 4-hours+ or similar long cook times
  if (tags.some(t => t.includes('4-hours') || t.includes('crock-pot') || t.includes('slow-cooker'))) {
    return 240;
  }
  return null;
}

/**
 * Decode unicode escape sequences like \u0027 → '
 */
export function decodeUnicodeEscapes(str: string): string {
  return str.replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => {
    return String.fromCharCode(parseInt(hex, 16));
  });
}

/**
 * Check if recipe is a side dish or component (not a complete meal)
 */
export function isComponentRecipe(tags: string[], name: string): boolean {
  const lower = name.toLowerCase();
  const tagSet = new Set(tags.map(t => t.toLowerCase()));

  // Explicit side dish tags
  if (tagSet.has('side-dishes') || tagSet.has('side') || tagSet.has('side dish')) {
    return true;
  }

  // Sauces, dressings, marinades
  if (tagSet.has('sauces') || tagSet.has('marinades') || tagSet.has('dressings') ||
      tagSet.has('condiments') || tagSet.has('spreads')) {
    return true;
  }

  // Name-based detection for common sides
  const sidePatterns = [
    /^(mashed|roasted|baked|steamed|grilled)\s+(potatoes?|carrots?|broccoli|asparagus|vegetables?)$/i,
    /^(garlic|butter|herb)\s+(rice|bread|noodles)$/i,
    /^coleslaw$/i,
    /^(french|sweet potato)\s+fries$/i,
    /^cornbread$/i,
    /^(dinner|bread)\s+rolls?$/i,
  ];

  for (const pattern of sidePatterns) {
    if (pattern.test(lower)) return true;
  }

  return false;
}

/**
 * Select the most relevant tags (max 4) from the full tag list
 * Prioritizes: cuisine, cooking method, dish type, dietary
 */
export function selectRelevantTags(tags: string[], cuisines: string[], dietaryFlags: string[]): string[] {
  const selected: string[] = [];
  const selectedLower = new Set<string>(); // Track lowercase for deduplication

  // Helper to add tag if not duplicate (case-insensitive + semantic)
  const addTag = (tag: string): boolean => {
    const lower = tag.toLowerCase();
    // Exact duplicate check (case-insensitive)
    if (selectedLower.has(lower)) return false;
    // Semantic duplicates: "veggie-burgers" implies "vegetarian"
    if (lower.includes('veggie') && selectedLower.has('vegetarian')) return false;
    if (lower === 'vegetarian' && [...selectedLower].some(t => t.includes('veggie'))) return false;
    if (lower.includes('vegan') && selectedLower.has('vegetarian')) return false;
    // Only one "low-X" tag (low-fat, low-carb, etc. are redundant together)
    if (lower.startsWith('low-') && [...selectedLower].some(t => t.startsWith('low-'))) return false;
    // Substring duplicates: "low-calorie" vs "low-calorie-cooking"
    for (const existing of selectedLower) {
      if (lower.includes(existing) || existing.includes(lower)) return false;
    }
    selected.push(tag);
    selectedLower.add(lower);
    return true;
  };

  // Priority 1: Add first cuisine (if any)
  if (cuisines.length > 0) {
    addTag(cuisines[0]);
  }

  // Priority 2: Add first dietary flag (if any)
  if (dietaryFlags.length > 0 && selected.length < 4) {
    addTag(dietaryFlags[0]);
  }

  // Priority 3: Cooking method/style tags
  const methodTags = ['quick', 'easy', 'one-pot', 'sheet-pan', 'grilling', 'stir-fry',
                      'slow-cooker', 'pressure-cooker', 'weeknight', 'comfort-food'];
  for (const tag of tags) {
    if (selected.length >= 4) break;
    const lower = tag.toLowerCase();
    if (methodTags.some(m => lower.includes(m))) {
      addTag(tag);
    }
  }

  // Priority 4: Dish type tags
  const dishTags = ['soup', 'stew', 'salad', 'pasta', 'sandwich', 'burger', 'taco',
                    'curry', 'stir-fry', 'casserole', 'bowl'];
  for (const tag of tags) {
    if (selected.length >= 4) break;
    const lower = tag.toLowerCase();
    if (dishTags.some(d => lower.includes(d))) {
      addTag(tag);
    }
  }

  // Fill remaining with any other tags (excluding meta tags and time tags)
  const excludeMeta = ['time-to-make', 'course', 'main-ingredient', 'preparation',
                       'occasion', 'cuisine', 'dietary', 'taste-mood', 'equipment',
                       'number-of-servings', 'main-dish', 'minutes-or-less'];
  for (const tag of tags) {
    if (selected.length >= 4) break;
    const lower = tag.toLowerCase();
    if (!excludeMeta.some(m => lower.includes(m))) {
      addTag(tag);
    }
  }

  return selected.slice(0, 4);
}

// ============================================================================
// CSV Parsing
// ============================================================================

interface CSVRow {
  id: string;
  name: string;
  description: string;
  ingredients: string;
  ingredients_raw_str: string;
  serving_size: string;
  servings: string;
  steps: string;
  tags: string;
  search_terms: string;
}

/**
 * Parse a CSV line handling quoted fields with embedded commas
 */
function parseCSVLine(line: string): string[] {
  const fields: string[] = [];
  let current = '';
  let inQuotes = false;

  for (let i = 0; i < line.length; i++) {
    const char = line[i];

    if (char === '"') {
      if (inQuotes && i + 1 < line.length && line[i + 1] === '"') {
        // Escaped quote
        current += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (char === ',' && !inQuotes) {
      fields.push(current);
      current = '';
    } else {
      current += char;
    }
  }
  fields.push(current);

  return fields;
}

/**
 * Stream parse the CSV file
 */
export async function* parseRecipeCSV(filePath: string): AsyncGenerator<ImportedRecipe> {
  const fileStream = fs.createReadStream(filePath, { encoding: 'utf-8' });
  const rl = readline.createInterface({
    input: fileStream,
    crlfDelay: Infinity
  });

  let isHeader = true;
  let headers: string[] = [];
  let currentLine = '';
  let lineCount = 0;

  for await (const line of rl) {
    // Handle multi-line records (fields with embedded newlines)
    currentLine += (currentLine ? '\n' : '') + line;

    // Check if we have a complete record (balanced quotes)
    const quoteCount = (currentLine.match(/"/g) || []).length;
    if (quoteCount % 2 !== 0) {
      continue; // Incomplete record, keep reading
    }

    if (isHeader) {
      headers = parseCSVLine(currentLine);
      isHeader = false;
      currentLine = '';
      continue;
    }

    try {
      const fields = parseCSVLine(currentLine);

      const row: CSVRow = {
        id: fields[0] || '',
        name: fields[1] || '',
        description: fields[2] || '',
        ingredients: fields[3] || '',
        ingredients_raw_str: fields[4] || '',
        serving_size: fields[5] || '',
        servings: fields[6] || '',
        steps: fields[7] || '',
        tags: fields[8] || '',
        search_terms: fields[9] || ''
      };

      // Parse ingredients
      const ingredientNames = parsePythonList(row.ingredients);
      const ingredientRawStrings = parsePythonList(row.ingredients_raw_str);

      // Parse each ingredient string and decode unicode
      const ingredients: ParsedIngredient[] = ingredientRawStrings.map(raw => {
        const parsed = parseIngredientString(raw);
        // Decode unicode escapes in ingredient names
        parsed.name = decodeUnicodeEscapes(parsed.name);
        parsed.raw = decodeUnicodeEscapes(parsed.raw);
        if (parsed.preparation) {
          parsed.preparation = decodeUnicodeEscapes(parsed.preparation);
        }
        return parsed;
      });

      // Parse other fields
      const allTags = parsePythonList(row.tags);
      const searchTerms = parsePythonSet(row.search_terms);
      const steps = parsePythonList(row.steps).map(s => decodeUnicodeEscapes(s));

      // Extract structured data from tags
      const cuisines = extractCuisines(allTags);
      const dietaryFlags = extractDietaryFlags(allTags);
      const category = extractCategory(allTags, searchTerms);

      // Decode unicode in name and description
      const name = decodeUnicodeEscapes(row.name);
      const description = decodeUnicodeEscapes(row.description);

      // Extract time from tags (e.g., "30-minutes-or-less")
      const totalTimeMinutes = extractTimeFromTags(allTags);

      // Select only the 4 most relevant tags
      const tags = selectRelevantTags(allTags, cuisines, dietaryFlags);

      const recipe: ImportedRecipe = {
        sourceId: parseInt(row.id) || 0,
        name,
        description,
        servings: parseInt(row.servings) || 4,
        servingSizeGrams: parseServingSize(row.serving_size),
        prepTimeMinutes: totalTimeMinutes ? Math.round(totalTimeMinutes * 0.3) : null,
        cookTimeMinutes: totalTimeMinutes ? Math.round(totalTimeMinutes * 0.7) : null,
        totalTimeMinutes,
        ingredients,
        steps,
        tags,
        searchTerms,
        category,
        cuisines,
        dietaryFlags
      };

      lineCount++;
      yield recipe;

    } catch (error) {
      console.error(`Error parsing line ${lineCount}:`, error);
    }

    currentLine = '';
  }

  console.log(`Parsed ${lineCount} recipes`);
}

// ============================================================================
// Database Import
// ============================================================================

/**
 * Import recipes into the database
 */
export async function importRecipes(
  filePath: string,
  options: {
    limit?: number;
    onProgress?: (count: number, recipe: ImportedRecipe) => void;
    filter?: (recipe: ImportedRecipe) => boolean;
  } = {}
): Promise<{
  recipeCount: number;
  ingredientCount: number;
  recipes: ImportedRecipe[];
  ingredientStats: Map<string, { count: number; units: Set<string>; categories: Set<string> }>;
}> {
  const { limit, onProgress, filter } = options;

  // Track unique ingredients
  const ingredientMap = new Map<string, { count: number; units: Set<string>; categories: Set<string> }>();

  let recipeCount = 0;
  const recipes: ImportedRecipe[] = [];

  for await (const recipe of parseRecipeCSV(filePath)) {
    // Apply filter if provided
    if (filter && !filter(recipe)) continue;

    // Track ingredients
    for (const ing of recipe.ingredients) {
      if (!ing.name) continue;

      const existing = ingredientMap.get(ing.name);
      if (existing) {
        existing.count++;
        if (ing.unit) existing.units.add(ing.unit);
      } else {
        ingredientMap.set(ing.name, {
          count: 1,
          units: new Set(ing.unit ? [ing.unit] : []),
          categories: new Set([detectCategory(ing.name)])
        });
      }
    }

    recipes.push(recipe);
    recipeCount++;

    if (onProgress) {
      onProgress(recipeCount, recipe);
    }

    // Check limit
    if (limit && recipeCount >= limit) break;

    // Progress logging
    if (recipeCount % 10000 === 0) {
      console.log(`Processed ${recipeCount} recipes, ${ingredientMap.size} unique ingredients`);
    }
  }

  console.log(`\nImport complete:`);
  console.log(`  Recipes: ${recipeCount}`);
  console.log(`  Unique ingredients: ${ingredientMap.size}`);

  // Log top ingredients by frequency
  const topIngredients = [...ingredientMap.entries()]
    .sort((a, b) => b[1].count - a[1].count)
    .slice(0, 20);

  console.log(`\nTop 20 ingredients:`);
  topIngredients.forEach(([name, data], i) => {
    console.log(`  ${i + 1}. ${name} (${data.count} recipes, units: ${[...data.units].join(', ') || 'none'})`);
  });

  return {
    recipeCount,
    ingredientCount: ingredientMap.size,
    recipes,
    ingredientStats: ingredientMap
  };
}

/**
 * Export recipes to JSON file
 */
export async function exportToJSON(
  recipes: ImportedRecipe[],
  outputPath: string
): Promise<void> {
  const data = JSON.stringify(recipes, null, 2);
  fs.writeFileSync(outputPath, data, 'utf-8');
  console.log(`Exported ${recipes.length} recipes to ${outputPath}`);
}

// ============================================================================
// CLI Entry Point
// ============================================================================

async function main() {
  const args = process.argv.slice(2);

  // Parse arguments
  let filePath = 'recipes_w_search_terms.csv';
  let limit: number | undefined;
  let outputPath: string | undefined;
  let categoryFilter: string | undefined;

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--output' || arg === '-o') {
      outputPath = args[++i];
    } else if (arg === '--limit' || arg === '-l') {
      limit = parseInt(args[++i]);
    } else if (arg === '--category' || arg === '-c') {
      categoryFilter = args[++i];
    } else if (!arg.startsWith('-')) {
      if (!filePath || filePath === 'recipes_w_search_terms.csv') {
        filePath = arg;
      } else if (!limit) {
        limit = parseInt(arg);
      }
    }
  }

  console.log(`Importing recipes from: ${filePath}`);
  if (limit) console.log(`Limit: ${limit} recipes`);
  if (categoryFilter) console.log(`Category filter: ${categoryFilter}`);
  if (outputPath) console.log(`Output: ${outputPath}`);

  const startTime = Date.now();

  // Build comprehensive filter
  const recipeFilter = (recipe: ImportedRecipe): boolean => {
    // Category filter (e.g., "dinner")
    if (categoryFilter && recipe.category !== categoryFilter) {
      return false;
    }

    // Exclude side dishes (category detected from original tags)
    if (recipe.category === 'side') {
      return false;
    }

    // Filter servings: 2-8 is a reasonable range for home cooking
    if (recipe.servings < 2 || recipe.servings > 8) {
      return false;
    }

    // Require at least 3 ingredients (filters out ultra-simple components)
    if (recipe.ingredients.length < 3) {
      return false;
    }

    // Require at least 2 steps (filters out assembly-only recipes)
    if (recipe.steps.length < 2) {
      return false;
    }

    // Exclude sauce-only, dressing, or marinade recipes by name
    const lowerName = recipe.name.toLowerCase();
    if (/^(.*\s)?(sauce|dressing|marinade|glaze|rub|seasoning)(\s.*)?$/i.test(lowerName) &&
        !lowerName.includes('with')) {
      return false;
    }

    return true;
  };

  const result = await importRecipes(filePath, {
    limit,
    filter: recipeFilter,
    onProgress: (count, _recipe) => {
      if (count % 50000 === 0) {
        console.log(`Progress: ${count} recipes...`);
      }
    }
  });

  const elapsed = ((Date.now() - startTime) / 1000).toFixed(2);
  console.log(`\nCompleted in ${elapsed}s`);

  // Export to JSON if output path specified
  if (outputPath && result.recipes.length > 0) {
    await exportToJSON(result.recipes, outputPath);
  }
}

// Run directly
main().catch(console.error);
