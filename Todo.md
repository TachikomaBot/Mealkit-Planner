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

---

## High Priority

### Pantry Sync with Shopping & Cooking
- [ ] Auto-add ingredients to pantry after completing grocery shopping
  - Find/restore the "mark shopping completed" button (user can't see it now)
  - Show confirmation screen before adding items
  - Allow user to edit quantities on confirmation screen
- [ ] Auto-deduct ingredients from pantry after making a recipe
  - Show confirmation screen before deducting
  - Allow user to edit deductions to match actual usage
- [ ] User should be able to edit items from confirmation screen to align with actual stock levels

### Ingredient Substitution
- [ ] Long-press on grocery list item to substitute ingredient
- [ ] Propagate substitution back to the recipe(s) using that ingredient
  - Example: fresh corn → frozen corn should update recipe instructions

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
