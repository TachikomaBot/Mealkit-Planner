---
# Mealkit-Planner-92jc
title: AI-powered recipe customization via text input
status: in-progress
type: feature
priority: high
created_at: 2026-01-18T02:14:03Z
updated_at: 2026-01-18T02:35:17Z
---

Add a floating action button on the recipe detail page that opens a text input allowing users to describe how they'd like to customize the recipe. The customization is processed by Gemini (gemini-3-flash-preview) which shows a preview of proposed changes before applying them.

## Use Cases

**Ingredient Substitution (Availability/Cost)**
User has "Basil Pesto Pasta Primavera" which calls for fresh basil, pine nuts, garlic, parmesan, and lemon juice to make pesto from scratch. In January in Edmonton, Canada, fresh basil is expensive/hard to find. User requests: "Use a jar of premade pesto instead of making it from scratch"

**Dietary - Vegetarian**
User requests: "Make this vegetarian" - AI removes meat, suggests plant-based protein alternatives

**Dietary - Add Protein**
User requests: "Add chicken to this dish" - AI adds chicken to ingredients, updates instructions for cooking it

**Dietary - Gluten Free**
User requests: "Make this gluten free" - AI substitutes pasta for gluten-free pasta, checks sauces for gluten, etc.

**Preference - Kid Friendly**
User requests: "Make this more kid-friendly" - AI might reduce spice levels, simplify ingredients, adjust portions

**Any Free-Form Customization**
The text input accepts any natural language description of how the user wants to modify the recipe.

## UI Flow (Multi-turn with Preview)

1. **Trigger**: User taps floating action button (chat icon, bottom-right of recipe page)
2. **Input**: Dialog/sheet with text input appears - user describes desired customization
3. **Preview**: AI returns proposed changes, displayed as:
   - Ingredients to ADD (green)
   - Ingredients to REMOVE (red)
   - Summary of instruction changes
   - Updated recipe name (if changed)
4. **User Action**: Three options:
   - **Apply**: Commit changes to recipe and grocery list
   - **Refine**: Re-opens text input so user can provide additional instructions (e.g., "Actually, keep the parmesan")
   - **Cancel**: Discard proposed changes
5. **Refine Loop**: User can refine multiple times until satisfied, then Apply

## AI Behavior

The AI should:
1. Interpret the user's customization request
2. Update recipe ingredients appropriately (add/remove/substitute)
3. Decide intelligently which ingredients to keep vs remove (e.g., garlic and parmesan might still be useful even when using jarred pesto)
4. Update recipe instructions to reflect changes
5. Update recipe name if the change is significant enough to warrant it
6. On Apply: Sync changes to the grocery shopping list (add new items, remove items no longer needed)

## Requirements

- Floating action button (FAB) with chat icon on RecipeDetailScreen (bottom-right)
- FAB should be HIDDEN (not just disabled) after shopping list is marked complete
- FAB uses scroll-based alpha: fully opaque at top, fades to ~0.4 alpha as user scrolls into instructions section
- Text input dialog/sheet for user to describe desired customization
- Preview screen showing proposed changes with Apply/Refine/Cancel actions
- Gemini API call to process customization
- Updates recipe ingredients, instructions, and name if appropriate
- Updates grocery shopping list on Apply

## Technical Notes

- Use existing AI ingredient substitution feature as template (see GroceryPolishWorker and related code)
- Use same Gemini model: gemini-3-flash-preview
- This feature is more flexible than ingredient substitution (free-form text vs single ingredient swap)
- Need to check shopping completion status to control FAB visibility
- Preview UI similar to pantry confirmation screens pattern
- Scroll-based FAB alpha: track LazyColumn scroll state, interpolate alpha from 1.0 (top) to 0.4 (scrolled into content)

## Checklist

- [x] Add FAB with chat icon to RecipeDetailScreen
- [x] Implement scroll-based alpha fade for FAB
- [x] Add shopping completion check to hide FAB
- [x] Create text input dialog for customization request
- [x] Implement Gemini API call for recipe customization (API endpoint + repository)
- [x] Create preview screen showing proposed changes (adds/removes/modifications)
- [x] Implement Apply action (update recipe in database)
- [x] Implement Refine action (re-open input with context)
- [x] Implement Cancel action
- [ ] Add backend endpoint for customize-recipe (TODO: backend implementation)
- [ ] Test with various customization scenarios