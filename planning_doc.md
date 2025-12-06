# Meal Kit Replacement App — Planning Document

## Concept

A personal meal planning system that replicates the decision-reduction benefits of meal kit delivery (Goodfood, etc.) while allowing for self-procurement of ingredients. Claude serves as the backend intelligence for recipe generation, shopping list compilation, and pantry management.

**Core difference from meal kits:** Ingredients come from the grocery store in retail quantities, not pre-portioned. This makes pantry state tracking essential — the system must know what's on hand to avoid redundant purchases and to bias recipe selection toward using existing inventory.

---

## Weekly Workflow

### Saturday: Planning & Reconciliation Session

1. **Pantry check-in** — Review ingredients flagged as potentially low, expiring soon, or needed for the coming week. User confirms quantities via quick prompts or image input.

2. **Recipe selection** — Claude proposes 6 default recipes from a pool of 20–24 based on:
   - User preferences and dietary constraints
   - Current pantry state (prioritize using what's on hand)
   - Seasonal availability (what's fresh in Alberta)
   - Variety (no repeats within 3–6 months)

3. **Shopping list generation** — Aggregated list of what's needed, minus what's already in the pantry. Organized by store section (produce, dairy, etc.).

4. **User goes shopping**

5. **Post-shopping intake** — User logs new purchases via image or voice. Claude extracts product details and quantities, updates pantry DB.

### During the Week

- User marks recipes as "cooked" → system calculates ingredient depletion
- Optional: quick corrections if something gets used outside a recipe, spoils, etc.

---

## Data Model

### Ingredient (Pantry Item)

```
{
  id: string
  name: string                    // "all-purpose flour"
  brand: string | null            // "Robin Hood"
  quantity_initial: number        // 2500 (grams)
  quantity_remaining: number      // 1800 (grams)
  unit: string                    // "g" | "ml" | "units" | "bunch"
  category: string                // "dry goods" | "dairy" | "produce" | "protein" | "condiment" | "spice"
  perishable: boolean
  expiry_date: date | null
  date_added: date
  last_updated: date
}
```

### Recipe

```
{
  id: string
  name: string
  description: string
  servings: number
  prep_time_minutes: number
  cook_time_minutes: number
  ingredients: [
    {
      ingredient_name: string     // normalized name for matching
      quantity: number
      unit: string
      preparation: string | null  // "diced", "minced", etc.
    }
  ]
  instructions: string[]          // step-by-step
  tags: string[]                  // "quick", "vegetarian", "high-protein", etc.
  source: "generated" | "imported"
  date_created: date
  times_cooked: number
  last_cooked: date | null
}
```

### Recipe History (for repetition avoidance)

```
{
  id: string
  recipe_id: string
  recipe_name: string             // denormalized for easy reference
  recipe_hash: string             // content hash to detect "same" recipe even if regenerated
  date_cooked: date
  rating: number | null           // 1-5 if rated
  would_make_again: boolean | null
  notes: string | null
}
```

The `recipe_hash` helps detect when Claude generates something essentially identical to a past recipe (same core ingredients + technique) even if phrased differently. Prevents "new" recipes that are really repeats.

### Repetition Window

- Recipes cooked within the last 3 months: **excluded** from generation pool
- Recipes cooked 3–6 months ago: **deprioritized** (can appear, but weighted lower)
- Recipes cooked 6+ months ago: **eligible** for regeneration, especially if highly rated

### Meal Plan

```
{
  id: string
  week_of: date                   // Monday of the week
  recipes: [
    {
      recipe_id: string
      planned_date: date | null   // optional day assignment
      cooked: boolean
      cooked_date: date | null
    }
  ]
  shopping_list_generated: date
}
```

---

## Units & Normalization

All quantities stored in metric base units:
- **Mass:** grams (g)
- **Volume:** milliliters (ml)
- **Discrete items:** units (e.g., "3 eggs", "2 onions")
- **Bunches:** bunch (herbs, green onions — treated as discrete)

Recipes display human-friendly units (250 ml, 1 L, 500 g) but store in base units for calculation.

### Common conversions (for intake)

| Retail unit       | Stored as     |
|-------------------|---------------|
| 1 kg bag flour    | 1000 g        |
| 1 L milk          | 1000 ml       |
| 500 ml cream      | 500 ml        |
| 1 dozen eggs      | 12 units      |
| 1 bunch cilantro  | 1 bunch       |

---

## Intake Methods

Two parallel methods for logging purchased ingredients, optimized for different moments.

### Image Intake

Best for: items with clear packaging, batch logging after shopping, capturing exact product details

When user photographs a purchased item:

1. Claude (or vision model) extracts:
   - Product name
   - Brand
   - Net weight/volume
   - Expiry date if visible

2. System attempts to match to existing pantry item (same product restocked) or creates new entry

3. User confirms or corrects before committing

**Edge cases:**
- Loose produce: user states quantity ("3 onions", "about 500g beef")
- Bulk bin items: user estimates weight
- Items without clear labeling: user provides details manually

### Voice Intake

Best for: unpacking groceries at home, hands-free operation, natural narration flow

User speaks while putting items away:
> "One bag of basmati rice, 2 kilos. A dozen eggs. About 500 grams of ground beef. Two cans of diced tomatoes."

1. **Speech-to-text** via browser/device API (Web Speech API or platform native)

2. **Claude parses the transcript** into structured entries:
   - Ingredient name (normalized)
   - Quantity + unit
   - Brand if mentioned
   - Inferred category for pantry organization

3. **Batch confirmation:** After unpacking, user reviews extracted items on screen, corrects any misheard entries, commits all at once

**Design notes:**
- Forgiving parsing: "a couple of onions" → 2 units, "some chicken thighs" prompts for weight
- Can interleave image and voice: photograph the weird item, narrate the rest
- Expiry dates harder to capture via voice — either skip (estimate later) or prompt "any expiry dates to note?"

### When to Use Which

| Scenario | Recommended |
|----------|-------------|
| Unpacking groceries at home | Voice — hands are full, narrate as you go |
| Single specialty item | Image — capture exact product info |
| Produce / bulk / deli | Voice — no useful packaging to photograph |
| Items with important expiry | Image — capture the date visually |
| Quick top-up (just milk) | Voice — fastest |

---

## Pantry Intelligence

### Depletion Calculation

When a recipe is marked cooked:
- For each ingredient, subtract recipe quantity from `quantity_remaining`
- If result < 0, set to 0 and flag for review (indicates drift)

### Reconciliation Triggers

The Saturday session surfaces items for review:
- **Low stock:** quantity_remaining < 20% of typical purchase size
- **Stale data:** last_updated > 2 weeks ago for perishables
- **Expiring soon:** expiry_date within 5 days
- **Near zero:** quantity_remaining approaching zero

User can confirm ("yes, still have about 200g"), update ("actually finished it"), or skip.

### Recipe Bias

When generating weekly recipes, weight toward:
- Using perishables before expiry
- Consuming items that have been open/partial for a while
- Complementary use (if you have half a cabbage, suggest something that uses cabbage)

---

## Technical Architecture

### Option A: Local-First (PWA / Android with local DB)

```
┌─────────────────────────────────────────┐
│            Mobile App / PWA             │
│  ┌─────────────┐    ┌────────────────┐  │
│  │   SQLite    │    │   Camera /     │  │
│  │  (Pantry,   │    │   Image Input  │  │
│  │  Recipes)   │    │                │  │
│  └─────────────┘    └────────────────┘  │
│           │                  │          │
│           └────────┬─────────┘          │
│                    ▼                    │
│          ┌─────────────────┐            │
│          │  Claude API     │            │
│          │  Integration    │            │
│          └─────────────────┘            │
└─────────────────────────────────────────┘
                     │
                     ▼
          ┌─────────────────┐
          │  Anthropic API  │
          │  (your Max key) │
          └─────────────────┘
```

**Pros:** No server to maintain, data stays on device, works offline (except Claude calls)  
**Cons:** No cross-device sync, backup is manual

### Option B: Lightweight Backend

```
┌─────────────┐         ┌─────────────────┐
│  App / PWA  │ ◄─────► │  Your Server    │
└─────────────┘         │  (API + DB)     │
                        │  - PostgreSQL   │
                        │  - Express/Flask│
                        └────────┬────────┘
                                 │
                                 ▼
                        ┌─────────────────┐
                        │  Anthropic API  │
                        └─────────────────┘
```

**Pros:** Cross-device, easier backup, can add features later (sharing recipes, etc.)  
**Cons:** Hosting cost, more to maintain

### Recommendation

Start with **Option A** (PWA with IndexedDB or SQLite via WASM). Keeps scope small, no backend to deploy. If you move to a new hosting platform later and want cross-device sync, you can migrate to Option B — the data model stays the same.

---

## Claude Integration Points

### 1. Recipe Generation

**Input:** User preferences, pantry state, recent meal history  
**Output:** Structured recipe objects (JSON)

Claude generates recipes on demand, returning structured data the app can store and render. No need to maintain a recipe database externally — Claude *is* the recipe database.

### 2. Shopping List Compilation

**Input:** Selected recipes for the week, current pantry state  
**Output:** Aggregated shopping list with quantities, organized by category

### 3. Image Intake Processing

**Input:** Photo of product  
**Output:** Extracted product details (name, brand, quantity, unit, expiry)

### 4. Pantry Reconciliation

**Input:** List of items flagged for review  
**Output:** Conversational prompts, accepts user corrections, returns updated records

### 5. Conversational Planning

The Saturday session can be a chat interface where user and Claude negotiate the week's meals:

> "I want something quick on Tuesday, we're having guests Friday, and I'd like to use up that butternut squash."

Claude responds with tailored suggestions.

---

## MVP Scope

### Phase 1: Core Loop

- [ ] Basic app shell (PWA or Android)
- [ ] Pantry data model + local persistence
- [ ] Manual ingredient entry (name, quantity, unit)
- [ ] Recipe generation via Claude API
- [ ] Mark recipe as cooked → depletion calc
- [ ] Shopping list generation

### Phase 2: Image Intake

- [ ] Camera integration
- [ ] Send image to Claude API for extraction
- [ ] Review/confirm flow before committing to pantry

### Phase 3: Smart Reconciliation

- [ ] Flag items for review based on rules
- [ ] Saturday check-in flow
- [ ] Expiry tracking + alerts

### Phase 4: Polish

- [ ] Recipe history + favorites
- [ ] Preference learning (Claude remembers what you liked)
- [ ] Seasonal/local ingredient suggestions
- [ ] Export shopping list to notes app / share

---

## Resolved Parameters

| Parameter | Decision |
|-----------|----------|
| Platform | PWA |
| Serving size | 2 (cook once → lunch + dinner) |
| Recipes per week | 6 (covers 12 meals, leaves buffer for eating out) |
| Weekday complexity | ≤30 min (4 recipes) |
| Weekend complexity | 45–60 min (2 recipes) |
| Dietary constraints | None hard-coded; handled via preference learning |
| Shopping | Centralized in-app checklist, no external apps |

---

## Store Context

**Primary:** Real Canadian Superstore — default assumption for availability and pricing  
**Specialty:**
- Italian Centre — European ingredients, specialty items
- H Mart — Asian ingredients when needed

For MVP, assume single-store shopping at Superstore. Recipes should be generatable with ingredients available there. If a recipe calls for something specialty (e.g., gochugaru, nduja), the shopping list can flag it as "Italian Centre" or "H Mart" so user knows to make an extra stop or substitute.

Future enhancement: multi-store optimization ("you're already going to H Mart for gochugaru, here's a recipe that uses other things you can grab there").

---

## Recipe Selection Flow

Modeled on GoodFood's UX: reduce decision fatigue while preserving agency.

### Saturday Planning Session

1. **Claude generates a pool of 20–24 candidate recipes** for the week, considering:
   - Current pantry state (prioritize using what's on hand)
   - Seasonality (what's fresh and in season in Alberta)
   - Preference profile (learned affinities)
   - Recent history (avoid repetition — no repeats within 3–6 months)
   - Complexity split (~14–16 quick weekday options, ~6–8 longer weekend options)

2. **6 recipes are pre-selected as defaults** — Claude's best guess at a good week, balancing variety, pantry use, and preferences (4 weekday, 2 weekend)

3. **User reviews defaults:**
   - Keep as-is, or
   - Swap out any they don't want for alternatives from the remaining pool
   - Can request "show me more options" to regenerate/expand pool if nothing appeals

4. **User confirms final 6** (or adjusts count if that week is unusual)

5. **Shopping list generated** from confirmed recipes

### Design Constraints

**Paradox of choice:** Pool size bounded at 20–24. Enough variety to always find something good, not so many it's overwhelming. Presented in digestible chunks (e.g., "here are your 6 defaults" → "here are alternatives" on demand).

**No repeats:** Recipe history tracked. Same recipe doesn't resurface for 3–6 months unless user explicitly requests it. Variations are fine (e.g., different burger builds count as different recipes).

---

## Seasonality

Recipes should favor what's actually good and available in Edmonton at the time of year.

### Implementation

Claude receives current date and location context. When generating recipes:
- Weight toward seasonal produce (e.g., root vegetables and squash in winter, tomatoes and zucchini in summer)
- Prefer fresh over frozen when seasonal equivalent exists
- Note when something is out of season ("this calls for fresh tomatoes — in December, canned San Marzano will be better")

### Seasonal Calendar (Alberta, rough)

| Season | In Season |
|--------|-----------|
| Winter (Dec–Feb) | Root vegetables, cabbage, squash, greenhouse greens, frozen/preserved |
| Spring (Mar–May) | Asparagus, rhubarb, early greens, greenhouse produce |
| Summer (Jun–Aug) | Peak everything — tomatoes, corn, berries, zucchini, peppers, fresh herbs |
| Fall (Sep–Nov) | Squash, apples, late harvest, root vegetables returning |

Claude will use this as soft guidance, not hard rules. Superstore has global supply chains — you can get tomatoes in January, they're just not great.

---

## In-Store Adaptability

This is a key differentiator from a static meal kit. Real grocery shopping involves:
- Items out of stock
- Different package sizes than expected
- Substitutions (no fresh basil, but there's a potted plant)
- Impulse discoveries ("ooh, duck is on sale")

The app needs a **Shopping Mode** that handles this dynamically.

### Shopping Mode Flow

1. **User enters Shopping Mode** with their generated list

2. **Checklist interface** — items organized by store section, checkable as found

3. **Quantity mismatch handling:**
   - User finds burger buns only in packs of 8, recipe needs 4
   - Tap the item → report actual quantity available
   - Claude responds with options:
     - "Buy 8, I'll add a recipe next week that uses the extra 4"
     - "Here's an alternative: use lettuce wraps instead, no buns needed"
     - "Swap the burger meal for [X] which doesn't need buns"
   - User picks an option, plan updates accordingly

4. **Out of stock handling:**
   - Item unavailable → user marks as "not found"
   - Claude suggests: substitute ingredient, swap recipe, or flag for specialty store

5. **Sale-driven adaptation:**
   - User spots a good deal (especially proteins — chicken thighs, pork shoulder, salmon)
   - Reports it: "Ribeye is 40% off"
   - Claude responds with options:
     - "Swap [planned recipe] for [steak recipe] — here's what to put back and what else to grab"
     - "Add a steak meal, here's the revised list"
   - **Works even late in the trip** — user might be 90% done shopping
   - Claude recalculates: what to return, what to add, revised plan
   - Proteins are the high-value items where sales matter most; produce sales less impactful

6. **Real-time plan mutation:**
   - All changes propagate: shopping list updates, meal plan updates, pantry projections update
   - User sees revised plan before confirming purchases
   - "Put back" items clearly flagged if swap requires returning something already in cart

### Technical Implications

- Shopping Mode requires live Claude API calls from the store (assumes cell connectivity — confirmed OK)
- State must be held in the app during the session; changes committed when user "completes" shopping
- Need to track "in cart" state to know what needs to be put back on swaps

### UX Considerations

- This is a conversation, but a fast one. User is standing in the aisle, not sitting at home.
- Responses need to be terse: options presented as tappable choices, not paragraphs
- Pre-compute likely issues where possible (e.g., flag "burger buns often come in 8-packs" before user leaves home)
- Late-trip swaps need extra clarity: "Put back: X, Y. Grab instead: A, B, C."

---

## Preference Learning System

The goal is a recommendation-algorithm feel: over time, the system learns what you like and tailors suggestions accordingly. Since Claude doesn't retain memory across sessions, preferences must be stored locally and passed as context when generating recipes.

### Preference Data Model

```
{
  // Explicit ratings
  recipe_ratings: [
    {
      recipe_id: string
      rating: 1-5 | "would_make_again" | "never_again"
      date: date
      notes: string | null      // "too spicy", "great for leftovers"
    }
  ]

  // Ingredient-level signals
  ingredient_preferences: {
    loved: string[]             // ["mushrooms", "goat cheese", "lemon"]
    disliked: string[]          // ["cilantro", "blue cheese"]
    allergies: string[]         // hard constraints, not preferences
  }

  // Cuisine / style preferences (learned over time)
  cuisine_affinity: {
    [cuisine: string]: number   // e.g., "thai": 0.8, "mexican": 0.4
    // Scale: 0 = avoid, 0.5 = neutral, 1 = favor
  }

  // Cooking style preferences
  method_affinity: {
    [method: string]: number    // "one-pot": 0.9, "deep-fry": 0.2
  }

  // Texture / flavor profile (optional, advanced)
  flavor_profile: {
    spice_tolerance: "mild" | "medium" | "hot" | "very_hot"
    umami_preference: number    // 0-1
    sweetness_preference: number
    // etc.
  }

  // Implicit signals
  implicit_history: {
    recipes_completed: string[]
    recipes_skipped: string[]   // suggested but never made
    recipes_repeated: string[]  // made more than once = strong signal
  }
}
```

### Signal Collection

**Explicit (high signal, requires user action):**
- After marking a recipe "cooked," prompt for quick rating (optional, skippable)
- "Would you make this again?" — single tap, low friction
- Ingredient flags: "I don't like [X]" when reviewing a recipe

**Implicit (passive, no friction):**
- Completed recipes: weak positive signal
- Repeated recipes: strong positive signal
- Suggested but never selected: weak negative signal
- Substitution requests: "use X instead of Y" → learns dislikes

### How Preferences Inform Generation

When requesting recipes for the week, the app sends Claude:

```
{
  pantry_state: [...],
  recent_meals: [...],          // avoid repetition
  preference_summary: {
    favor_ingredients: ["mushrooms", "lemon", "ginger"],
    avoid_ingredients: ["cilantro", "blue cheese"],
    cuisine_weights: { "thai": 0.8, "italian": 0.7, "mexican": 0.4 },
    spice_tolerance: "medium",
    preferred_methods: ["one-pot", "sheet-pan", "stir-fry"],
    disliked_methods: ["deep-fry"]
  },
  constraints: {
    weekday_max_time: 30,
    weekend_max_time: 60,
    servings: 2
  }
}
```

Claude uses this to weight recipe generation — not as hard filters (except allergies), but as soft biases. A low Mexican affinity doesn't mean never suggest tacos, just less often.

### Cold Start

On first use, no preference data exists. Options:

1. **Onboarding quiz:** "Pick cuisines you enjoy" / "Any ingredients you avoid?" — gets baseline fast
2. **Start neutral:** Generate varied recipes, learn from ratings over the first few weeks
3. **Hybrid:** Minimal onboarding (allergies + strong dislikes only), then learn

Recommendation: **Hybrid**. Ask for hard constraints (allergies) and any strong dislikes upfront, then let the rest emerge organically. Avoids a tedious onboarding flow.

### Preference Decay (Optional, Advanced)

Tastes change. A recipe loved 6 months ago might not reflect current preferences. Could implement:
- Recent ratings weighted more heavily
- "Rediscover" prompt for old favorites occasionally
- Seasonal adjustment (crave different things in winter vs. summer)

This is a Phase 4+ concern. For MVP, treat all ratings equally.

---

## MVP Scope (Revised)

### Phase 1: Core Loop

- [ ] PWA shell with basic navigation
- [ ] IndexedDB setup for local persistence
- [ ] Pantry data model + manual entry
- [ ] Claude API integration (recipe generation)
- [ ] Recipe pool generation (20–24 candidates)
- [ ] Default selection (6 pre-picked) + swap UI
- [ ] Confirm final 6 → generate shopping list
- [ ] Basic checklist view for shopping

### Phase 2: Recipe Execution & Depletion

- [ ] Recipe detail view (instructions, ingredients)
- [ ] Mark recipe as "cooked"
- [ ] Automatic ingredient depletion calculation
- [ ] Drift detection (negative inventory = flag for review)
- [ ] Recipe history tracking (for repetition avoidance)

### Phase 3: Intake Methods

- [ ] **Image intake:** Camera integration, send to Claude for extraction, review/confirm flow
- [ ] **Voice intake:** Speech-to-text capture, Claude parsing, batch confirmation
- [ ] Add to pantry DB from either method

### Phase 4: Shopping Mode (Live Adaptation)

- [ ] Enter Shopping Mode with active list
- [ ] Track "in cart" state for items
- [ ] Report quantity mismatches → Claude suggests alternatives
- [ ] Out-of-stock handling + substitutions
- [ ] Sale-driven swaps (especially proteins)
- [ ] Late-trip adaptation: "put back X, grab Y instead"
- [ ] Real-time plan/list updates
- [ ] Commit changes on "complete shopping"

### Phase 5: Preference Learning

- [ ] Post-cook rating prompt (optional)
- [ ] "Would make again" / "Never again" quick actions
- [ ] Ingredient preference flags
- [ ] Store preference data in local DB
- [ ] Pass preference summary to Claude for generation weighting
- [ ] 3–6 month repetition window enforcement

### Phase 6: Smart Reconciliation

- [ ] Saturday check-in flow
- [ ] Flag low-stock, stale-data, expiring items
- [ ] Conversational confirmation/correction
- [ ] Expiry alerts

### Phase 7: Polish & Iteration

- [ ] Recipe history + favorites
- [ ] Repeat tracking (strong positive signal)
- [ ] Seasonal calendar refinement
- [ ] UI polish, animations
- [ ] Potential: multi-store optimization
- [ ] Potential: voice mode for Shopping Mode (hands-free in store)

---

## Next Steps

1. Confirm we're aligned on scope and flow
2. Scaffold PWA project structure
3. Set up IndexedDB schema matching data models
4. Build basic UI shell (home, pantry, plan, shop views)
5. Implement Claude API integration for recipe generation
6. Iterate from there