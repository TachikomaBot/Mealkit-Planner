/**
 * Canonical Ingredient Database
 *
 * Provides normalized ingredient names, unit conversions, and categorization.
 * This is the source of truth for ingredient matching across recipes, pantry, and shopping list.
 */

// ============================================================================
// Types
// ============================================================================

export interface CanonicalIngredient {
  id: string;                      // Canonical ID: "chicken-breast"
  name: string;                    // Display name: "chicken breast"
  pluralName: string;              // Plural form: "chicken breasts"
  category: IngredientCategory;
  subcategory?: string;            // More specific: "poultry" under "protein"
  defaultUnit: MetricUnit;         // Standard unit for storage: "g", "ml", "pcs"
  shelfStable: boolean;            // Can be stored at room temp
  perishableDays: number | null;   // Days until expiry (null if shelf-stable)
  aliases: string[];               // Alternative names for matching
  unitConversions: UnitConversion[]; // How to convert from common units
}

export type IngredientCategory =
  | 'protein'
  | 'produce'
  | 'dairy'
  | 'grains'
  | 'canned'
  | 'condiment'
  | 'spice'
  | 'baking'
  | 'frozen'
  | 'beverage'
  | 'other';

export type MetricUnit = 'g' | 'ml' | 'pcs';

export interface UnitConversion {
  fromUnit: string;    // e.g., "cup", "tbsp", "oz"
  toMetric: number;    // How many metric units (g or ml)
  metricUnit: MetricUnit;
}

// ============================================================================
// Normalization Rules
// ============================================================================

/**
 * Normalize an ingredient name to its canonical form
 */
export function normalizeIngredientName(name: string): string {
  let normalized = name.toLowerCase().trim();

  // Remove common preparation words at the end
  normalized = normalized
    .replace(/,?\s*(fresh|dried|ground|chopped|diced|minced|sliced|shredded|grated|crushed|whole|raw|cooked|frozen|canned|packed|firmly packed|loosely packed|lightly packed)\s*$/gi, '')
    .replace(/,?\s*(to taste|for garnish|optional|as needed|divided|or more|or less)\s*$/gi, '')
    .trim();

  // Apply specific normalizations
  for (const [pattern, replacement] of normalizationRules) {
    if (pattern.test(normalized)) {
      normalized = replacement;
      break;
    }
  }

  return normalized;
}

/**
 * Normalization rules: [pattern to match, canonical name]
 */
const normalizationRules: [RegExp, string][] = [
  // Eggs
  [/^eggs?$/, 'eggs'],
  [/^egg yolks?$/, 'egg yolks'],
  [/^egg whites?$/, 'egg whites'],
  [/^large eggs?$/, 'eggs'],
  [/^medium eggs?$/, 'eggs'],
  [/^small eggs?$/, 'eggs'],

  // Garlic
  [/^garlic cloves?$/, 'garlic'],
  [/^cloves? (?:of )?garlic$/, 'garlic'],
  [/^fresh garlic$/, 'garlic'],
  [/^garlic$/, 'garlic'],

  // Onions
  [/^yellow onions?$/, 'yellow onion'],
  [/^white onions?$/, 'white onion'],
  [/^red onions?$/, 'red onion'],
  [/^sweet onions?$/, 'sweet onion'],
  [/^onions?$/, 'yellow onion'], // Default to yellow

  // Flour
  [/^all[- ]purpose flour$/, 'all-purpose flour'],
  [/^plain flour$/, 'all-purpose flour'],
  [/^flour$/, 'all-purpose flour'],
  [/^bread flour$/, 'bread flour'],
  [/^cake flour$/, 'cake flour'],
  [/^whole wheat flour$/, 'whole wheat flour'],
  [/^self[- ]rising flour$/, 'self-rising flour'],

  // Sugar
  [/^granulated sugar$/, 'sugar'],
  [/^white sugar$/, 'sugar'],
  [/^caster sugar$/, 'sugar'],
  [/^sugar$/, 'sugar'],
  [/^brown sugar$/, 'brown sugar'],
  [/^light brown sugar$/, 'brown sugar'],
  [/^dark brown sugar$/, 'dark brown sugar'],
  [/^powdered sugar$/, 'powdered sugar'],
  [/^confectioner'?s sugar$/, 'powdered sugar'],
  [/^icing sugar$/, 'powdered sugar'],

  // Salt & Pepper
  [/^kosher salt$/, 'salt'],
  [/^sea salt$/, 'salt'],
  [/^table salt$/, 'salt'],
  [/^salt$/, 'salt'],
  [/^salt and pepper$/, 'salt'], // Split into separate ingredients
  [/^salt & pepper$/, 'salt'],
  [/^black pepper$/, 'black pepper'],
  [/^ground black pepper$/, 'black pepper'],
  [/^freshly ground black pepper$/, 'black pepper'],
  [/^pepper$/, 'black pepper'],
  [/^white pepper$/, 'white pepper'],

  // Butter
  [/^unsalted butter$/, 'butter'],
  [/^salted butter$/, 'butter'],
  [/^butter$/, 'butter'],
  [/^margarine$/, 'margarine'],

  // Milk & Cream
  [/^whole milk$/, 'milk'],
  [/^2% milk$/, 'milk'],
  [/^skim milk$/, 'skim milk'],
  [/^low[- ]fat milk$/, 'low-fat milk'],
  [/^milk$/, 'milk'],
  [/^heavy cream$/, 'heavy cream'],
  [/^whipping cream$/, 'heavy cream'],
  [/^heavy whipping cream$/, 'heavy cream'],
  [/^light cream$/, 'light cream'],
  [/^half[- ]and[- ]half$/, 'half-and-half'],
  [/^sour cream$/, 'sour cream'],

  // Oils
  [/^extra[- ]virgin olive oil$/, 'olive oil'],
  [/^olive oil$/, 'olive oil'],
  [/^vegetable oil$/, 'vegetable oil'],
  [/^canola oil$/, 'canola oil'],
  [/^coconut oil$/, 'coconut oil'],
  [/^sesame oil$/, 'sesame oil'],

  // Chicken
  [/^boneless[,]? skinless chicken breasts?$/, 'chicken breast'],
  [/^chicken breasts?$/, 'chicken breast'],
  [/^boneless[,]? skinless chicken thighs?$/, 'chicken thighs'],
  [/^chicken thighs?$/, 'chicken thighs'],
  [/^chicken drumsticks?$/, 'chicken drumsticks'],
  [/^chicken wings?$/, 'chicken wings'],
  [/^whole chicken$/, 'whole chicken'],

  // Beef
  [/^ground beef$/, 'ground beef'],
  [/^lean ground beef$/, 'ground beef'],
  [/^beef stew meat$/, 'beef stew meat'],
  [/^beef chuck$/, 'beef chuck'],
  [/^sirloin steak$/, 'sirloin steak'],
  [/^ribeye steak$/, 'ribeye steak'],
  [/^flank steak$/, 'flank steak'],

  // Canned tomatoes
  [/^canned diced tomatoes$/, 'canned diced tomatoes'],
  [/^diced tomatoes$/, 'canned diced tomatoes'],
  [/^crushed tomatoes$/, 'canned crushed tomatoes'],
  [/^tomato sauce$/, 'tomato sauce'],
  [/^tomato paste$/, 'tomato paste'],

  // Canned beans
  [/^canned black beans$/, 'canned black beans'],
  [/^black beans$/, 'canned black beans'],
  [/^canned kidney beans$/, 'canned kidney beans'],
  [/^kidney beans$/, 'canned kidney beans'],
  [/^canned chickpeas$/, 'canned chickpeas'],
  [/^chickpeas$/, 'canned chickpeas'],
  [/^garbanzo beans$/, 'canned chickpeas'],
  [/^canned white beans$/, 'canned white beans'],
  [/^cannellini beans$/, 'canned white beans'],

  // Fresh herbs
  [/^fresh parsley$/, 'fresh parsley'],
  [/^parsley$/, 'fresh parsley'],
  [/^flat[- ]leaf parsley$/, 'fresh parsley'],
  [/^italian parsley$/, 'fresh parsley'],
  [/^fresh cilantro$/, 'fresh cilantro'],
  [/^cilantro$/, 'fresh cilantro'],
  [/^coriander leaves?$/, 'fresh cilantro'],
  [/^fresh basil$/, 'fresh basil'],
  [/^basil leaves?$/, 'fresh basil'],
  [/^fresh thyme$/, 'fresh thyme'],
  [/^thyme$/, 'fresh thyme'],
  [/^fresh rosemary$/, 'fresh rosemary'],
  [/^rosemary$/, 'fresh rosemary'],
  [/^fresh dill$/, 'fresh dill'],
  [/^dill$/, 'fresh dill'],
  [/^fresh mint$/, 'fresh mint'],
  [/^mint leaves?$/, 'fresh mint'],

  // Common dried spices
  [/^ground cumin$/, 'cumin'],
  [/^cumin$/, 'cumin'],
  [/^ground cinnamon$/, 'cinnamon'],
  [/^cinnamon$/, 'cinnamon'],
  [/^ground paprika$/, 'paprika'],
  [/^paprika$/, 'paprika'],
  [/^smoked paprika$/, 'smoked paprika'],
  [/^ground cayenne pepper$/, 'cayenne pepper'],
  [/^cayenne pepper$/, 'cayenne pepper'],
  [/^cayenne$/, 'cayenne pepper'],
  [/^dried oregano$/, 'oregano'],
  [/^oregano$/, 'oregano'],
  [/^dried basil$/, 'dried basil'],
  [/^dried thyme$/, 'dried thyme'],

  // Vinegars & sauces
  [/^balsamic vinegar$/, 'balsamic vinegar'],
  [/^white wine vinegar$/, 'white wine vinegar'],
  [/^red wine vinegar$/, 'red wine vinegar'],
  [/^apple cider vinegar$/, 'apple cider vinegar'],
  [/^rice vinegar$/, 'rice vinegar'],
  [/^soy sauce$/, 'soy sauce'],
  [/^low[- ]sodium soy sauce$/, 'soy sauce'],
  [/^worcestershire sauce$/, 'worcestershire sauce'],

  // Vanilla
  [/^vanilla extract$/, 'vanilla extract'],
  [/^pure vanilla extract$/, 'vanilla extract'],
  [/^vanilla$/, 'vanilla extract'],

  // Cheese
  [/^shredded cheddar cheese$/, 'cheddar cheese'],
  [/^cheddar cheese$/, 'cheddar cheese'],
  [/^shredded mozzarella cheese$/, 'mozzarella cheese'],
  [/^mozzarella cheese$/, 'mozzarella cheese'],
  [/^parmesan cheese$/, 'parmesan cheese'],
  [/^parmigiano[- ]reggiano$/, 'parmesan cheese'],
  [/^grated parmesan$/, 'parmesan cheese'],
  [/^cream cheese$/, 'cream cheese'],
  [/^feta cheese$/, 'feta cheese'],
  [/^goat cheese$/, 'goat cheese'],

  // Rice & grains
  [/^long[- ]grain rice$/, 'long-grain rice'],
  [/^white rice$/, 'long-grain rice'],
  [/^rice$/, 'long-grain rice'],
  [/^jasmine rice$/, 'jasmine rice'],
  [/^basmati rice$/, 'basmati rice'],
  [/^brown rice$/, 'brown rice'],
  [/^quinoa$/, 'quinoa'],

  // Pasta
  [/^spaghetti$/, 'spaghetti'],
  [/^penne$/, 'penne'],
  [/^fusilli$/, 'fusilli'],
  [/^rigatoni$/, 'rigatoni'],
  [/^fettuccine$/, 'fettuccine'],
  [/^linguine$/, 'linguine'],
  [/^elbow macaroni$/, 'elbow macaroni'],
  [/^macaroni$/, 'elbow macaroni'],

  // Bread
  [/^bread crumbs$/, 'bread crumbs'],
  [/^panko bread crumbs$/, 'panko bread crumbs'],
  [/^panko$/, 'panko bread crumbs'],

  // Nuts
  [/^almonds$/, 'almonds'],
  [/^sliced almonds$/, 'almonds'],
  [/^walnuts$/, 'walnuts'],
  [/^pecans$/, 'pecans'],
  [/^cashews$/, 'cashews'],
  [/^peanuts$/, 'peanuts'],
  [/^pine nuts$/, 'pine nuts'],

  // Baking
  [/^baking powder$/, 'baking powder'],
  [/^baking soda$/, 'baking soda'],
  [/^active dry yeast$/, 'active dry yeast'],
  [/^instant yeast$/, 'instant yeast'],
  [/^yeast$/, 'active dry yeast'],
  [/^cornstarch$/, 'cornstarch'],
  [/^corn starch$/, 'cornstarch'],
];

// ============================================================================
// Unit Conversions (to metric)
// ============================================================================

/**
 * Standard unit conversions to grams/milliliters
 * These are approximations and vary by ingredient density
 */
export const unitConversions: Record<string, { toMetric: number; metricUnit: MetricUnit }> = {
  // Volume to ml
  'cup': { toMetric: 240, metricUnit: 'ml' },
  'tbsp': { toMetric: 15, metricUnit: 'ml' },
  'tsp': { toMetric: 5, metricUnit: 'ml' },
  'l': { toMetric: 1000, metricUnit: 'ml' },
  'ml': { toMetric: 1, metricUnit: 'ml' },
  'fl oz': { toMetric: 30, metricUnit: 'ml' },

  // Weight to g
  'lb': { toMetric: 454, metricUnit: 'g' },
  'oz': { toMetric: 28, metricUnit: 'g' },
  'kg': { toMetric: 1000, metricUnit: 'g' },
  'g': { toMetric: 1, metricUnit: 'g' },

  // Approximate conversions (these vary by ingredient)
  'pinch': { toMetric: 0.5, metricUnit: 'g' },
  'dash': { toMetric: 0.5, metricUnit: 'ml' },
};

/**
 * Ingredient-specific density conversions (cups to grams)
 * Used when we need to convert volume to weight for specific ingredients
 */
export const ingredientDensities: Record<string, number> = {
  // Flour (1 cup = ~)
  'all-purpose flour': 120,
  'bread flour': 127,
  'cake flour': 114,
  'whole wheat flour': 128,

  // Sugar
  'sugar': 200,
  'brown sugar': 220,
  'powdered sugar': 120,

  // Dairy
  'butter': 227, // 1 cup
  'milk': 245,
  'heavy cream': 240,
  'sour cream': 230,

  // Oils
  'olive oil': 216,
  'vegetable oil': 218,

  // Rice & grains
  'long-grain rice': 185,
  'basmati rice': 190,
  'brown rice': 195,
  'quinoa': 170,

  // Oats
  'oats': 80,
  'rolled oats': 80,

  // Nuts (chopped)
  'almonds': 95,
  'walnuts': 100,
  'pecans': 100,

  // Cheese (shredded)
  'cheddar cheese': 115,
  'mozzarella cheese': 115,
  'parmesan cheese': 100,

  // Honey/syrups
  'honey': 340,
  'maple syrup': 315,
};

/**
 * Convert a quantity from one unit to metric (g or ml)
 */
export function convertToMetric(
  quantity: number,
  fromUnit: string,
  ingredientName?: string
): { value: number; unit: MetricUnit } | null {
  const normalizedUnit = fromUnit.toLowerCase().trim();

  // Direct conversion if unit is known
  const conversion = unitConversions[normalizedUnit];
  if (conversion) {
    // Check if we need ingredient-specific density conversion
    if (conversion.metricUnit === 'ml' && ingredientName) {
      const density = ingredientDensities[normalizeIngredientName(ingredientName)];
      if (density) {
        // Convert ml to g using density
        const mlValue = quantity * conversion.toMetric;
        const cupEquivalent = mlValue / 240; // Convert to cups
        return { value: Math.round(cupEquivalent * density), unit: 'g' };
      }
    }

    return {
      value: Math.round(quantity * conversion.toMetric),
      unit: conversion.metricUnit
    };
  }

  // Count-based units
  if (['pcs', 'piece', 'pieces', 'whole', 'large', 'medium', 'small', 'clove', 'cloves', 'head', 'heads', 'bunch', 'bunches', 'stalk', 'stalks', 'sprig', 'sprigs', 'leaf', 'leaves', 'slice', 'slices', 'can', 'cans', 'bottle', 'bottles', 'package', 'packages'].includes(normalizedUnit)) {
    return { value: quantity, unit: 'pcs' };
  }

  return null;
}

// ============================================================================
// Category Detection
// ============================================================================

export function detectCategory(name: string): IngredientCategory {
  const lower = normalizeIngredientName(name);

  // Proteins
  if (/chicken|beef|pork|lamb|fish|salmon|tuna|shrimp|prawn|turkey|duck|bacon|sausage|ham|steak|ground|meatball|tofu|tempeh|seitan/.test(lower)) {
    return 'protein';
  }

  // Dairy
  if (/milk|cream|cheese|butter|yogurt|sour cream|eggs?|half-and-half|buttermilk/.test(lower)) {
    return 'dairy';
  }

  // Produce
  if (/onion|garlic|tomato|potato|carrot|celery|pepper|broccoli|spinach|lettuce|cabbage|zucchini|squash|cucumber|mushroom|asparagus|corn|peas|eggplant|cauliflower|kale|chard|apple|banana|orange|lemon|lime|berry|grape|mango|pineapple|peach|pear|cherry|melon|avocado|ginger|jalapeño|bell pepper|green onion|scallion/.test(lower)) {
    return 'produce';
  }

  // Fresh herbs (produce)
  if (/^fresh\s|parsley|cilantro|basil|thyme|rosemary|dill|mint|chives|oregano(?!.*dried)/.test(lower)) {
    return 'produce';
  }

  // Canned goods
  if (/^canned|tomato sauce|tomato paste|broth|stock|coconut milk/.test(lower)) {
    return 'canned';
  }

  // Grains
  if (/flour|rice|pasta|noodle|bread|oat|cereal|quinoa|couscous|barley|spaghetti|penne|fusilli|macaroni|fettuccine|linguine/.test(lower)) {
    return 'grains';
  }

  // Baking
  if (/sugar|baking powder|baking soda|yeast|vanilla|cocoa|chocolate chip|cornstarch|cream of tartar/.test(lower)) {
    return 'baking';
  }

  // Spices (dried)
  if (/cumin|paprika|cinnamon|oregano|cayenne|chili powder|curry|turmeric|nutmeg|allspice|cloves|cardamom|coriander|dried|ground|powder/.test(lower)) {
    return 'spice';
  }

  // Salt & Pepper (spice)
  if (/^salt$|^black pepper$|^white pepper$|^pepper$/.test(lower)) {
    return 'spice';
  }

  // Condiments
  if (/sauce|ketchup|mustard|mayo|mayonnaise|vinegar|oil|dressing|syrup|honey|soy sauce|worcestershire|hot sauce|sriracha|salsa|pesto/.test(lower)) {
    return 'condiment';
  }

  // Frozen
  if (/frozen/.test(lower)) {
    return 'frozen';
  }

  // Beverages
  if (/juice|wine|beer|coffee|tea(?!spoon)/.test(lower)) {
    return 'beverage';
  }

  return 'other';
}

/**
 * Check if an ingredient is shelf-stable
 */
export function isShelfStable(name: string): boolean {
  const category = detectCategory(name);

  // Always perishable
  if (['protein', 'dairy', 'produce'].includes(category)) {
    return false;
  }

  // Always shelf-stable
  if (['canned', 'grains', 'baking', 'spice', 'condiment'].includes(category)) {
    return true;
  }

  // Check specific items
  const lower = normalizeIngredientName(name);
  if (/fresh|raw/.test(lower)) {
    return false;
  }

  return true;
}

/**
 * Estimate shelf life in days
 */
export function estimateShelfLife(name: string): number | null {
  const category = detectCategory(name);
  const lower = normalizeIngredientName(name);

  // Proteins
  if (category === 'protein') {
    if (/bacon|sausage/.test(lower)) return 14;
    return 3; // Most fresh meat
  }

  // Dairy
  if (category === 'dairy') {
    if (/butter/.test(lower)) return 30;
    if (/cheese/.test(lower)) return 21;
    if (/milk/.test(lower)) return 7;
    if (/eggs/.test(lower)) return 21;
    return 7;
  }

  // Produce
  if (category === 'produce') {
    if (/potato|onion|garlic/.test(lower)) return 30;
    if (/apple|orange|lemon|lime/.test(lower)) return 14;
    if (/lettuce|spinach|herbs/.test(lower)) return 5;
    return 7;
  }

  // Shelf-stable items
  return null;
}
