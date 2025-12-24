package com.mealplanner.domain.usecase

import com.mealplanner.domain.model.*
import com.mealplanner.domain.repository.MealPlanRepository
import com.mealplanner.domain.repository.PantryRepository
import com.mealplanner.domain.repository.ShoppingRepository
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Use case to load sample data for testing the app without Gemini API
 */
class LoadSampleDataUseCase @Inject constructor(
    private val mealPlanRepository: MealPlanRepository,
    private val pantryRepository: PantryRepository,
    private val shoppingRepository: ShoppingRepository
) {

    suspend fun loadSampleData(): Result<SampleDataResult> {
        return try {
            // Clear existing data first
            pantryRepository.clearAll()

            // Load sample pantry items
            val pantryItemsAdded = loadSamplePantryItems()

            // Load sample meal plan
            val mealPlanId = loadSampleMealPlan()

            // Generate shopping list for the meal plan
            shoppingRepository.generateShoppingList(mealPlanId)

            Result.success(
                SampleDataResult(
                    pantryItemsAdded = pantryItemsAdded,
                    mealPlanCreated = true,
                    recipesAdded = sampleRecipes.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load common pantry staples without clearing existing data or creating meal plans.
     * Skips items that already exist in the pantry (by name, case-insensitive).
     * Useful for testing meal generation with a populated pantry.
     */
    suspend fun loadPantryStaples(): Result<PantryStaplesResult> {
        return try {
            // Get existing item names to avoid duplicates
            val existingNames = pantryRepository.getAllItems()
                .map { it.name.lowercase() }
                .toSet()

            // Helper to create pantry items with smart tracking style
            fun createPantryStaple(
                name: String,
                quantity: Double,
                unit: PantryUnit,
                category: PantryCategory,
                perishable: Boolean = false,
                expiryDate: LocalDate? = null,
                stockLevel: StockLevel = StockLevel.PLENTY
            ): PantryItem {
                val trackingStyle = PantryItem.smartTrackingStyle(name, category)
                return PantryItem(
                    name = name,
                    quantityInitial = quantity,
                    quantityRemaining = quantity,
                    unit = unit,
                    category = category,
                    trackingStyle = trackingStyle,
                    stockLevel = stockLevel,
                    perishable = perishable,
                    expiryDate = expiryDate,
                    dateAdded = LocalDateTime.now(),
                    lastUpdated = LocalDateTime.now()
                )
            }

            val items = listOf(
                // Oils (STOCK_LEVEL tracking)
                createPantryStaple("Olive Oil", 500.0, PantryUnit.MILLILITERS, PantryCategory.OILS),
                createPantryStaple("Vegetable Oil", 1000.0, PantryUnit.MILLILITERS, PantryCategory.OILS),
                // Butter is dairy - needs PRECISE tracking
                createPantryStaple("Butter", 250.0, PantryUnit.GRAMS, PantryCategory.DAIRY,
                    perishable = true, expiryDate = LocalDate.now().plusDays(30)),
                // Dried Herbs & Spices (STOCK_LEVEL tracking)
                createPantryStaple("Salt", 500.0, PantryUnit.GRAMS, PantryCategory.SPICE),
                createPantryStaple("Black Pepper", 100.0, PantryUnit.GRAMS, PantryCategory.SPICE),
                createPantryStaple("White Pepper", 50.0, PantryUnit.GRAMS, PantryCategory.SPICE),
                createPantryStaple("Five Spice", 50.0, PantryUnit.GRAMS, PantryCategory.SPICE),
                createPantryStaple("Dried Oregano", 30.0, PantryUnit.GRAMS, PantryCategory.SPICE),
                createPantryStaple("Dried Basil", 30.0, PantryUnit.GRAMS, PantryCategory.SPICE),
                createPantryStaple("Dried Thyme", 30.0, PantryUnit.GRAMS, PantryCategory.SPICE),
                createPantryStaple("Dried Rosemary", 30.0, PantryUnit.GRAMS, PantryCategory.SPICE),
                // Dry Goods - bulk items (STOCK_LEVEL tracking)
                createPantryStaple("Sugar", 1000.0, PantryUnit.GRAMS, PantryCategory.DRY_GOODS),
                createPantryStaple("All-Purpose Flour", 2000.0, PantryUnit.GRAMS, PantryCategory.DRY_GOODS),
                createPantryStaple("Rice", 2000.0, PantryUnit.GRAMS, PantryCategory.DRY_GOODS),
                createPantryStaple("Pasta", 500.0, PantryUnit.GRAMS, PantryCategory.DRY_GOODS),
                // Canned Goods (COUNT tracking - discrete units)
                createPantryStaple("Canned Tomatoes", 4.0, PantryUnit.UNITS, PantryCategory.DRY_GOODS),
                createPantryStaple("Chicken Broth", 2.0, PantryUnit.UNITS, PantryCategory.DRY_GOODS),
                // Condiments (STOCK_LEVEL tracking)
                createPantryStaple("Soy Sauce", 300.0, PantryUnit.MILLILITERS, PantryCategory.CONDIMENT),
                createPantryStaple("Vinegar", 500.0, PantryUnit.MILLILITERS, PantryCategory.CONDIMENT),
                createPantryStaple("Honey", 350.0, PantryUnit.GRAMS, PantryCategory.CONDIMENT),
                // Root Vegetables (PRECISE tracking, but long shelf life)
                createPantryStaple("Potato", 6.0, PantryUnit.UNITS, PantryCategory.PRODUCE,
                    perishable = true, expiryDate = LocalDate.now().plusDays(21)),
                createPantryStaple("Onion", 6.0, PantryUnit.UNITS, PantryCategory.PRODUCE,
                    perishable = true, expiryDate = LocalDate.now().plusDays(21)),
                createPantryStaple("Garlic", 4.0, PantryUnit.UNITS, PantryCategory.PRODUCE,
                    perishable = true, expiryDate = LocalDate.now().plusDays(14)),
                createPantryStaple("Carrot", 6.0, PantryUnit.UNITS, PantryCategory.PRODUCE,
                    perishable = true, expiryDate = LocalDate.now().plusDays(21)),
                createPantryStaple("Shallot", 4.0, PantryUnit.UNITS, PantryCategory.PRODUCE,
                    perishable = true, expiryDate = LocalDate.now().plusDays(14))
            )

            // Only add items that don't already exist
            val newItems = items.filter { it.name.lowercase() !in existingNames }

            newItems.forEach { item ->
                pantryRepository.addItem(item)
            }

            Result.success(PantryStaplesResult(itemsAdded = newItems.size))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun loadSamplePantryItems(): Int {
        val items = listOf(
            PantryItem(
                name = "Olive Oil",
                quantityInitial = 500.0,
                quantityRemaining = 350.0,
                unit = PantryUnit.MILLILITERS,
                category = PantryCategory.CONDIMENT,
                perishable = false,
                dateAdded = LocalDateTime.now().minusDays(30),
                lastUpdated = LocalDateTime.now()
            ),
            PantryItem(
                name = "Salt",
                quantityInitial = 500.0,
                quantityRemaining = 400.0,
                unit = PantryUnit.GRAMS,
                category = PantryCategory.SPICE,
                perishable = false,
                dateAdded = LocalDateTime.now().minusDays(60),
                lastUpdated = LocalDateTime.now()
            ),
            PantryItem(
                name = "Black Pepper",
                quantityInitial = 100.0,
                quantityRemaining = 75.0,
                unit = PantryUnit.GRAMS,
                category = PantryCategory.SPICE,
                perishable = false,
                dateAdded = LocalDateTime.now().minusDays(45),
                lastUpdated = LocalDateTime.now()
            ),
            PantryItem(
                name = "Garlic",
                quantityInitial = 3.0,
                quantityRemaining = 2.0,
                unit = PantryUnit.UNITS,
                category = PantryCategory.PRODUCE,
                perishable = true,
                expiryDate = LocalDate.now().plusDays(10),
                dateAdded = LocalDateTime.now().minusDays(7),
                lastUpdated = LocalDateTime.now()
            ),
            PantryItem(
                name = "Onion",
                quantityInitial = 5.0,
                quantityRemaining = 3.0,
                unit = PantryUnit.UNITS,
                category = PantryCategory.PRODUCE,
                perishable = true,
                expiryDate = LocalDate.now().plusDays(14),
                dateAdded = LocalDateTime.now().minusDays(5),
                lastUpdated = LocalDateTime.now()
            ),
            PantryItem(
                name = "Chicken Breast",
                quantityInitial = 1000.0,
                quantityRemaining = 500.0,
                unit = PantryUnit.GRAMS,
                category = PantryCategory.PROTEIN,
                perishable = true,
                expiryDate = LocalDate.now().plusDays(3), // Expiring soon!
                dateAdded = LocalDateTime.now().minusDays(2),
                lastUpdated = LocalDateTime.now()
            ),
            PantryItem(
                name = "Rice",
                quantityInitial = 2000.0,
                quantityRemaining = 1500.0,
                unit = PantryUnit.GRAMS,
                category = PantryCategory.DRY_GOODS,
                perishable = false,
                dateAdded = LocalDateTime.now().minusDays(20),
                lastUpdated = LocalDateTime.now()
            ),
            PantryItem(
                name = "Pasta",
                quantityInitial = 500.0,
                quantityRemaining = 100.0, // Low stock!
                unit = PantryUnit.GRAMS,
                category = PantryCategory.DRY_GOODS,
                perishable = false,
                dateAdded = LocalDateTime.now().minusDays(30),
                lastUpdated = LocalDateTime.now()
            ),
            PantryItem(
                name = "Milk",
                quantityInitial = 1000.0,
                quantityRemaining = 400.0,
                unit = PantryUnit.MILLILITERS,
                category = PantryCategory.DAIRY,
                perishable = true,
                expiryDate = LocalDate.now().plusDays(5),
                dateAdded = LocalDateTime.now().minusDays(3),
                lastUpdated = LocalDateTime.now()
            ),
            PantryItem(
                name = "Eggs",
                quantityInitial = 12.0,
                quantityRemaining = 8.0,
                unit = PantryUnit.UNITS,
                category = PantryCategory.DAIRY,
                perishable = true,
                expiryDate = LocalDate.now().plusDays(14),
                dateAdded = LocalDateTime.now().minusDays(5),
                lastUpdated = LocalDateTime.now()
            ),
            PantryItem(
                name = "Butter",
                quantityInitial = 250.0,
                quantityRemaining = 150.0,
                unit = PantryUnit.GRAMS,
                category = PantryCategory.DAIRY,
                perishable = true,
                expiryDate = LocalDate.now().plusDays(21),
                dateAdded = LocalDateTime.now().minusDays(10),
                lastUpdated = LocalDateTime.now()
            ),
            PantryItem(
                name = "Tomatoes",
                quantityInitial = 6.0,
                quantityRemaining = 4.0,
                unit = PantryUnit.UNITS,
                category = PantryCategory.PRODUCE,
                perishable = true,
                expiryDate = LocalDate.now().plusDays(5),
                dateAdded = LocalDateTime.now().minusDays(2),
                lastUpdated = LocalDateTime.now()
            ),
            PantryItem(
                name = "Canned Tomatoes",
                quantityInitial = 3.0,
                quantityRemaining = 2.0,
                unit = PantryUnit.UNITS,
                category = PantryCategory.DRY_GOODS,
                perishable = false,
                dateAdded = LocalDateTime.now().minusDays(60),
                lastUpdated = LocalDateTime.now()
            ),
            PantryItem(
                name = "Soy Sauce",
                quantityInitial = 300.0,
                quantityRemaining = 200.0,
                unit = PantryUnit.MILLILITERS,
                category = PantryCategory.CONDIMENT,
                perishable = false,
                dateAdded = LocalDateTime.now().minusDays(45),
                lastUpdated = LocalDateTime.now()
            ),
            PantryItem(
                name = "Parmesan Cheese",
                quantityInitial = 200.0,
                quantityRemaining = 50.0, // Low stock!
                unit = PantryUnit.GRAMS,
                category = PantryCategory.DAIRY,
                perishable = true,
                expiryDate = LocalDate.now().plusDays(30),
                dateAdded = LocalDateTime.now().minusDays(14),
                lastUpdated = LocalDateTime.now()
            )
        )

        items.forEach { item ->
            pantryRepository.addItem(item)
        }

        return items.size
    }

    private suspend fun loadSampleMealPlan(): Long {
        return mealPlanRepository.saveMealPlan(sampleRecipes)
    }

    companion object {
        val sampleRecipes = listOf(
            Recipe(
                id = "sample-1",
                name = "Garlic Butter Chicken",
                description = "Juicy pan-seared chicken breast with a rich garlic butter sauce. Simple yet elegant, this dish comes together in under 30 minutes.",
                servings = 4,
                prepTimeMinutes = 10,
                cookTimeMinutes = 20,
                ingredients = listOf(
                    RecipeIngredient("Chicken Breast", 4.0, "pieces", "boneless, skinless"),
                    RecipeIngredient("Butter", 60.0, "g"),
                    RecipeIngredient("Garlic", 4.0, "cloves", "minced"),
                    RecipeIngredient("Olive Oil", 2.0, "tbsp"),
                    RecipeIngredient("Salt", 1.0, "tsp"),
                    RecipeIngredient("Black Pepper", 0.5, "tsp"),
                    RecipeIngredient("Fresh Parsley", 2.0, "tbsp", "chopped")
                ),
                steps = listOf(
                    CookingStep("Prepare the chicken", listOf(
                        "Pat chicken breasts dry with paper towels",
                        "Season both sides with salt and pepper"
                    )),
                    CookingStep("Sear the chicken", listOf(
                        "Heat olive oil in a large skillet over medium-high heat",
                        "Add chicken and cook 6-7 minutes per side until golden and cooked through",
                        "Remove chicken and set aside"
                    )),
                    CookingStep("Make the garlic butter sauce", listOf(
                        "Reduce heat to medium-low",
                        "Add butter to the same pan",
                        "Add minced garlic and cook for 1 minute until fragrant"
                    )),
                    CookingStep("Serve", listOf(
                        "Return chicken to the pan and spoon sauce over it",
                        "Garnish with fresh parsley and serve immediately"
                    ))
                ),
                tags = listOf("Chicken", "Quick", "Easy", "Dinner", "Keto-Friendly")
            ),
            Recipe(
                id = "sample-2",
                name = "Classic Tomato Pasta",
                description = "A simple but delicious pasta with a fresh tomato sauce. Perfect for busy weeknights when you want something comforting and quick.",
                servings = 4,
                prepTimeMinutes = 10,
                cookTimeMinutes = 25,
                ingredients = listOf(
                    RecipeIngredient("Pasta", 400.0, "g", "spaghetti or penne"),
                    RecipeIngredient("Canned Tomatoes", 2.0, "cans", "400g each, crushed"),
                    RecipeIngredient("Garlic", 3.0, "cloves", "minced"),
                    RecipeIngredient("Olive Oil", 3.0, "tbsp"),
                    RecipeIngredient("Onion", 1.0, "medium", "diced"),
                    RecipeIngredient("Salt", 1.0, "tsp"),
                    RecipeIngredient("Black Pepper", 0.5, "tsp"),
                    RecipeIngredient("Parmesan Cheese", 50.0, "g", "grated"),
                    RecipeIngredient("Fresh Basil", 1.0, "handful", "torn")
                ),
                steps = listOf(
                    CookingStep("Cook the pasta", listOf(
                        "Bring a large pot of salted water to boil",
                        "Cook pasta according to package directions",
                        "Reserve 1 cup pasta water before draining"
                    )),
                    CookingStep("Make the sauce", listOf(
                        "Heat olive oil in a large pan over medium heat",
                        "Sauté onion until soft, about 5 minutes",
                        "Add garlic and cook 1 minute",
                        "Add crushed tomatoes, salt, and pepper",
                        "Simmer for 15 minutes"
                    )),
                    CookingStep("Combine and serve", listOf(
                        "Add drained pasta to the sauce",
                        "Toss well, adding pasta water if needed",
                        "Top with parmesan and fresh basil"
                    ))
                ),
                tags = listOf("Pasta", "Italian", "Vegetarian", "Quick", "Dinner")
            ),
            Recipe(
                id = "sample-3",
                name = "Fried Rice",
                description = "Restaurant-style fried rice that's even better than takeout. The secret is using day-old rice and high heat.",
                servings = 4,
                prepTimeMinutes = 15,
                cookTimeMinutes = 10,
                ingredients = listOf(
                    RecipeIngredient("Rice", 600.0, "g", "cooked, preferably day-old"),
                    RecipeIngredient("Eggs", 3.0, "large"),
                    RecipeIngredient("Soy Sauce", 3.0, "tbsp"),
                    RecipeIngredient("Vegetable Oil", 3.0, "tbsp"),
                    RecipeIngredient("Garlic", 2.0, "cloves", "minced"),
                    RecipeIngredient("Green Onions", 4.0, "stalks", "sliced"),
                    RecipeIngredient("Frozen Peas", 100.0, "g"),
                    RecipeIngredient("Salt", 0.5, "tsp"),
                    RecipeIngredient("Sesame Oil", 1.0, "tsp")
                ),
                steps = listOf(
                    CookingStep("Prep ingredients", listOf(
                        "Beat eggs in a bowl",
                        "Slice green onions, separating white and green parts",
                        "Break up any clumps in the cold rice"
                    )),
                    CookingStep("Cook eggs", listOf(
                        "Heat 1 tbsp oil in a wok over high heat",
                        "Add beaten eggs and scramble until just set",
                        "Remove and set aside"
                    )),
                    CookingStep("Stir-fry rice", listOf(
                        "Add remaining oil to the hot wok",
                        "Add garlic and white parts of green onions, stir-fry 30 seconds",
                        "Add rice and stir-fry for 3-4 minutes until heated through",
                        "Add peas and cook 1 minute"
                    )),
                    CookingStep("Finish", listOf(
                        "Add soy sauce and toss well",
                        "Return eggs to wok and break into pieces",
                        "Drizzle with sesame oil",
                        "Top with green onion tops and serve"
                    ))
                ),
                tags = listOf("Asian", "Rice", "Quick", "Dinner", "Eggs")
            ),
            Recipe(
                id = "sample-4",
                name = "Caprese Salad",
                description = "A refreshing Italian salad showcasing ripe tomatoes, fresh mozzarella, and fragrant basil. Simple perfection.",
                servings = 2,
                prepTimeMinutes = 10,
                cookTimeMinutes = 0,
                ingredients = listOf(
                    RecipeIngredient("Tomatoes", 3.0, "large", "ripe, sliced"),
                    RecipeIngredient("Fresh Mozzarella", 250.0, "g", "sliced"),
                    RecipeIngredient("Fresh Basil", 1.0, "bunch"),
                    RecipeIngredient("Olive Oil", 3.0, "tbsp", "extra virgin"),
                    RecipeIngredient("Balsamic Vinegar", 1.0, "tbsp"),
                    RecipeIngredient("Salt", 0.5, "tsp"),
                    RecipeIngredient("Black Pepper", 0.25, "tsp")
                ),
                steps = listOf(
                    CookingStep("Arrange the salad", listOf(
                        "Alternate slices of tomato and mozzarella on a serving plate",
                        "Tuck basil leaves between the slices"
                    )),
                    CookingStep("Season and serve", listOf(
                        "Drizzle with olive oil and balsamic vinegar",
                        "Season with salt and freshly cracked black pepper",
                        "Serve immediately at room temperature"
                    ))
                ),
                tags = listOf("Italian", "Salad", "Vegetarian", "No-Cook", "Summer")
            ),
            Recipe(
                id = "sample-5",
                name = "Scrambled Eggs on Toast",
                description = "Perfectly creamy scrambled eggs served on buttery toast. A breakfast classic done right.",
                servings = 2,
                prepTimeMinutes = 5,
                cookTimeMinutes = 8,
                ingredients = listOf(
                    RecipeIngredient("Eggs", 4.0, "large"),
                    RecipeIngredient("Butter", 30.0, "g"),
                    RecipeIngredient("Milk", 2.0, "tbsp"),
                    RecipeIngredient("Salt", 0.25, "tsp"),
                    RecipeIngredient("Black Pepper", 0.125, "tsp"),
                    RecipeIngredient("Bread", 4.0, "slices", "for toasting"),
                    RecipeIngredient("Chives", 1.0, "tbsp", "chopped, optional")
                ),
                steps = listOf(
                    CookingStep("Prepare eggs", listOf(
                        "Crack eggs into a bowl",
                        "Add milk, salt, and pepper",
                        "Beat until well combined"
                    )),
                    CookingStep("Toast bread", listOf(
                        "Toast bread slices to your preference",
                        "Butter while still warm"
                    )),
                    CookingStep("Scramble eggs", listOf(
                        "Melt butter in a non-stick pan over medium-low heat",
                        "Add egg mixture",
                        "Stir gently with a spatula, pushing eggs from edges to center",
                        "Remove from heat while still slightly wet - they'll continue cooking"
                    )),
                    CookingStep("Serve", listOf(
                        "Pile eggs onto buttered toast",
                        "Garnish with chives if using",
                        "Serve immediately"
                    ))
                ),
                tags = listOf("Breakfast", "Eggs", "Quick", "Vegetarian", "Easy")
            ),
            Recipe(
                id = "sample-6",
                name = "Honey Garlic Salmon",
                description = "Glazed salmon fillets with a sweet and savory honey garlic sauce. Elegant enough for dinner parties, easy enough for weeknights.",
                servings = 4,
                prepTimeMinutes = 10,
                cookTimeMinutes = 15,
                ingredients = listOf(
                    RecipeIngredient("Salmon Fillets", 4.0, "pieces", "about 6oz each"),
                    RecipeIngredient("Honey", 60.0, "ml"),
                    RecipeIngredient("Soy Sauce", 60.0, "ml"),
                    RecipeIngredient("Garlic", 4.0, "cloves", "minced"),
                    RecipeIngredient("Olive Oil", 2.0, "tbsp"),
                    RecipeIngredient("Lemon Juice", 1.0, "tbsp"),
                    RecipeIngredient("Salt", 0.5, "tsp"),
                    RecipeIngredient("Black Pepper", 0.25, "tsp")
                ),
                steps = listOf(
                    CookingStep("Make the glaze", listOf(
                        "Whisk together honey, soy sauce, garlic, and lemon juice",
                        "Set aside"
                    )),
                    CookingStep("Prepare salmon", listOf(
                        "Pat salmon dry and season with salt and pepper",
                        "Heat oil in an oven-safe skillet over medium-high heat"
                    )),
                    CookingStep("Cook salmon", listOf(
                        "Sear salmon skin-side up for 3 minutes until golden",
                        "Flip and pour glaze over the fish",
                        "Transfer to 400°F oven for 8-10 minutes"
                    )),
                    CookingStep("Serve", listOf(
                        "Spoon extra glaze from pan over salmon",
                        "Serve with rice or vegetables"
                    ))
                ),
                tags = listOf("Seafood", "Fish", "Healthy", "Dinner", "Quick")
            )
        )
    }
}

data class SampleDataResult(
    val pantryItemsAdded: Int,
    val mealPlanCreated: Boolean,
    val recipesAdded: Int
)

data class PantryStaplesResult(
    val itemsAdded: Int
)
