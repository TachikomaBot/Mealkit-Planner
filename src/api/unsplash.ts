/**
 * Unsplash API Integration for recipe images
 */

let accessKey: string | null = null;

export function setUnsplashKey(key: string) {
  accessKey = key;
  localStorage.setItem('unsplash_access_key', key);
}

export function getUnsplashKey(): string | null {
  if (!accessKey) {
    accessKey = localStorage.getItem('unsplash_access_key');
  }
  return accessKey;
}

export function hasUnsplashKey(): boolean {
  return !!getUnsplashKey();
}

interface UnsplashPhoto {
  id: string;
  urls: {
    raw: string;
    full: string;
    regular: string;
    small: string;
    thumb: string;
  };
  alt_description: string | null;
  user: {
    name: string;
    username: string;
  };
}

interface UnsplashSearchResponse {
  total: number;
  total_pages: number;
  results: UnsplashPhoto[];
}

// Cache to avoid repeated API calls for the same search
const imageCache = new Map<string, string>();

export async function getRecipeImage(recipeName: string, tags: string[] = []): Promise<string | null> {
  const key = getUnsplashKey();
  if (!key) {
    return null;
  }

  // Check cache first
  const cacheKey = recipeName.toLowerCase();
  if (imageCache.has(cacheKey)) {
    return imageCache.get(cacheKey)!;
  }

  try {
    // Build search query from recipe name and first tag
    const searchTerms = [recipeName];
    if (tags.length > 0) {
      // Add cuisine tag if present
      const cuisineTags = ['italian', 'mexican', 'asian', 'thai', 'indian', 'mediterranean', 'chinese', 'japanese'];
      const cuisineTag = tags.find(t => cuisineTags.includes(t.toLowerCase()));
      if (cuisineTag) {
        searchTerms.push(cuisineTag);
      }
    }
    searchTerms.push('food'); // Always include food to improve results

    const query = encodeURIComponent(searchTerms.join(' '));
    const response = await fetch(
      `https://api.unsplash.com/search/photos?query=${query}&per_page=1&orientation=landscape`,
      {
        headers: {
          'Authorization': `Client-ID ${key}`,
        },
      }
    );

    if (!response.ok) {
      console.error('Unsplash API error:', response.status);
      return null;
    }

    const data: UnsplashSearchResponse = await response.json();

    if (data.results.length > 0) {
      // Use small size for cards (400px width)
      const imageUrl = data.results[0].urls.small;
      imageCache.set(cacheKey, imageUrl);
      return imageUrl;
    }

    return null;
  } catch (error) {
    console.error('Failed to fetch recipe image:', error);
    return null;
  }
}

// Batch fetch images for multiple recipes
export async function getRecipeImages(
  recipes: { name: string; tags: string[] }[]
): Promise<Map<string, string>> {
  const results = new Map<string, string>();

  // Fetch in parallel with rate limiting (max 5 concurrent)
  const batchSize = 5;
  for (let i = 0; i < recipes.length; i += batchSize) {
    const batch = recipes.slice(i, i + batchSize);
    const promises = batch.map(async (recipe) => {
      const url = await getRecipeImage(recipe.name, recipe.tags);
      if (url) {
        results.set(recipe.name, url);
      }
    });
    await Promise.all(promises);
  }

  return results;
}
