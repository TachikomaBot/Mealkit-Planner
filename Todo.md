# Meal Planner App - Feedback & Todo List

## Completed
- [x] Enable Gemini thinking mode (MEDIUM) for better recipe/grocery quality
- [x] Fix tab swiping sensitivity - disabled entirely to avoid conflicts with Pantry gestures
- [x] Fix grey bar above bottom navigation (edge-to-edge contrast enforcement)
- [x] Improve Gemini recipe construction prompt with GoodFood-style formatting
  - Step titles: "Mise en Place", "[Action] the [Target]", "Plate Your Dish"
  - Substeps use **bold** markdown for ingredients, times, temperatures
  - Visual cues paired with cooking times ("until golden", "until fragrant")
  - Workflow optimization with "Meanwhile" for parallel tasks
- [x] AI-powered pantry categorization (Gemini) - **COMPLETE**
  - Shopping items are categorized intelligently when completing shopping trip
  - Correct category assignment (e.g., "Salmon Fillets" → PROTEIN, not OTHER)
  - Smart tracking style selection (STOCK_LEVEL for spices/oils, PRECISE for produce)
  - Intelligent expiry estimation based on item type (herbs: 4 days, fish: 3 days, etc.)
  - Fallback to local categorization if AI fails
  - Loading screen with progress indicator during pantry stocking
  - Grocery list becomes read-only after shopping completion
- [x] Pantry deduction confirmation screen - **COMPLETE**
  - Shows when user taps "I Made This!" on recipe detail
  - Review and adjust ingredient deductions before applying
  - Handles STOCK_LEVEL vs COUNT/PRECISE tracking styles differently
  - Half-unit (0.5) support for precise quantity adjustments
  - Long-press minus to skip items, auto-restore on adjustment
- [x] Pantry tab adjusters improved - **COMPLETE**
  - Changed to +/- buttons (matching deduction screen style)
  - Hold-to-repeat functionality when button is held down
  - Repeat speed scales with quantity magnitude (fine-grained for small values, faster for large)

---

## High Priority

### Pantry Sync with Shopping & Cooking
- [x] Auto-add ingredients to pantry after completing grocery shopping
  - ~~Find/restore the "mark shopping completed" button~~ ✓ Working
  - AI-powered categorization via Gemini ✓
  - [x] Show confirmation screen before adding items
  - [x] Allow user to edit quantities and names on confirmation screen
- [x] Auto-deduct ingredients from pantry after making a recipe - **COMPLETE**
  - [x] Show confirmation screen before deducting ("I Made This!" flow)
  - [x] Allow user to edit deductions to match actual usage
  - [x] Different UI for tracking styles:
    - STOCK_LEVEL items (spices, oils): 4-button selector (Out/Low/Some/Plenty)
    - COUNT/PRECISE items (produce, proteins): +/- buttons with 0.5 increment support
  - [x] "Used: X | Unused: Y" badge display format
  - [x] Long-press minus button to skip items (greyed out with "(Skipped)" badge)
  - [x] Auto-restore skipped items when quantity is adjusted
  - [x] Fuzzy matching of recipe ingredients to pantry items

### Ingredient Substitution - **COMPLETE**
- [x] Edit ingredient names on confirmation screen to substitute
- [x] AI-powered substitution propagates back to recipe(s):
  - Updates recipe name if ingredient is in the name (e.g., "Honey Garlic Salmon" → "Honey Garlic Tilapia")
  - Converts quantities appropriately (e.g., fresh herbs → dried uses 1/3 amount)
  - Updates preparation style (e.g., removes "torn" for dried herbs)
  - Updates recipe cooking instructions to reference new ingredient
  - Handles multiple recipes using the same ingredient

---

## Medium Priority

### Recipe Units & Quantities
- [ ] Use spoons/cups in recipes, metric (grams/mL) in shopping list
  - Recipes should be human-friendly (1 cup, 2 tbsp)
  - Shopping list should use metric for precise purchasing
- [ ] Fix Wild Mushroom Risotto liquid ratio (750mL broth for 175g rice seems off)
- [ ] Check grocery list generation prompts - fresh herbs weren't listed last week (user had to use dried)

### Stock Level Tracking
- [ ] Make Low/Some/Plenty the default tracking style (not precise measurements)
- [ ] Enable Low/Some/Plenty for ALL pantry categories (currently only Dry Herbs & Spices, Oils, Condiments)
- [ ] "Check Stock" category should only include items... (clarify requirement)

### Recipe History & Saving
- [ ] Keep history of past recipes (at least 1 month)
- [ ] Allow users to save/favorite recipes for future use
- [ ] Allow selecting saved recipes for weekly meal plan instead of generating new ones

---

## Low Priority / Future

### UI/UX Polish
- [ ] Render markdown **bold** in recipe substeps (currently plain text)
  - Recipe steps now include `**ingredient**`, `**3 to 5 min.**`, `**medium-high**`
  - Need to parse markdown and render with `AnnotatedString` or richtext library
  - File: `RecipeDetailScreen.kt` line ~610
- [ ] Smoother animation for pantry item adjustor modal swipe (feels abrupt)
- [ ] Fix jittery animation when modal expands for expiry date field (on category change)

### Data Management
- [ ] Import/export all user data (for debugging & backup)
  - Preserve history/state across app updates
  - May require deeper architecture work for user management/auth

---

## Notes

- User has been using the app for a week with real shopping and cooking
- 5 out of 6 meals prepared successfully
- Core workflow: generate meals → shop → cook → track pantry
