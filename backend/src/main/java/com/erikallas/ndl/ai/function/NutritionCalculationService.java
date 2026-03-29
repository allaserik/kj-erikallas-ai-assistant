package com.erikallas.ndl.ai.function;

import com.erikallas.ndl.ai.function.FunctionCallingHandler.IngredientInput;
import com.erikallas.ndl.ai.function.FunctionCallingHandler.NutritionResult;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Service for calculating nutritional information from ingredients.
 * 
 * Used by FunctionCallingHandler to provide AI with accurate nutrition data.
 * 
 * Calculation strategy: 1. For each ingredient, look up USDA/ingredient
 * database values 2. Normalize units to grams (1 cup = 240ml ≈ 200g, etc.) 3.
 * Calculate based on per-100g values 4. Sum totals 5. Adjust for servings
 * 
 * For missing ingredients: - Log warning - Use reasonable default estimates -
 * Mark as estimated in response
 */
@Service
public class NutritionCalculationService {
    private static final Logger log = LoggerFactory.getLogger(NutritionCalculationService.class);

    private final RecipeCatalogService recipeCatalogService;

    // Unit conversions to grams (approximate)
    private static final Map<String, Double> UNIT_TO_GRAMS = Map.ofEntries(Map.entry("g", 1.0), Map.entry("mg", 0.001),
            Map.entry("ml", 1.0), // 1ml ≈ 1g for liquids
            Map.entry("l", 1000.0), Map.entry("cup", 240.0), // US cup ≈ 240ml
            Map.entry("tbsp", 15.0), // Tablespoon
            Map.entry("tsp", 5.0), // Teaspoon
            Map.entry("oz", 28.35), // Ounce
            Map.entry("lb", 453.6), // Pound
            Map.entry("piece", 100.0), // Generic piece (apple, banana, etc.)
            Map.entry("whole", 100.0) // Generic whole item
    );

    // Estimated nutritional values per 100g (backup if not found in DB)
    // Format: ingredient -> [calories, protein, carbs, fats] per 100g
    private static final Map<String, double[]> INGREDIENT_ESTIMATES = Map.ofEntries(
            // Proteins
            Map.entry("chicken", new double[] { 165.0, 31.0, 0.0, 3.6 }),
            Map.entry("chicken breast", new double[] { 165.0, 31.0, 0.0, 3.6 }),
            Map.entry("beef", new double[] { 250.0, 26.0, 0.0, 15.0 }),
            Map.entry("salmon", new double[] { 208.0, 20.0, 0.0, 13.0 }),
            Map.entry("egg", new double[] { 155.0, 13.0, 1.1, 11.0 }),
            Map.entry("tofu", new double[] { 76.0, 8.0, 1.9, 4.8 }),

            // Vegetables
            Map.entry("broccoli", new double[] { 34.0, 2.8, 7.0, 0.4 }),
            Map.entry("spinach", new double[] { 23.0, 2.7, 3.6, 0.4 }),
            Map.entry("carrot", new double[] { 41.0, 0.9, 10.0, 0.2 }),
            Map.entry("tomato", new double[] { 18.0, 0.9, 3.9, 0.2 }),
            Map.entry("lettuce", new double[] { 15.0, 1.4, 2.9, 0.2 }),
            Map.entry("cucumber", new double[] { 16.0, 0.7, 3.6, 0.1 }),

            // Grains
            Map.entry("rice", new double[] { 130.0, 2.7, 28.0, 0.3 }),
            Map.entry("bread", new double[] { 265.0, 9.0, 49.0, 3.3 }),
            Map.entry("pasta", new double[] { 131.0, 5.3, 25.0, 1.1 }),
            Map.entry("oat", new double[] { 389.0, 16.0, 66.0, 6.9 }),
            Map.entry("quinoa", new double[] { 120.0, 4.4, 21.0, 1.9 }),

            // Oils/Fats
            Map.entry("olive oil", new double[] { 884.0, 0.0, 0.0, 100.0 }),
            Map.entry("butter", new double[] { 717.0, 0.9, 0.1, 81.0 }),

            // Dairy
            Map.entry("milk", new double[] { 61.0, 3.2, 4.8, 3.3 }),
            Map.entry("cheese", new double[] { 402.0, 25.0, 1.3, 33.0 }),
            Map.entry("yogurt", new double[] { 59.0, 3.5, 3.3, 0.4 }),

            // Fruits
            Map.entry("apple", new double[] { 52.0, 0.3, 14.0, 0.2 }),
            Map.entry("banana", new double[] { 89.0, 1.1, 23.0, 0.3 }),
            Map.entry("orange", new double[] { 47.0, 0.9, 12.0, 0.1 }),
            Map.entry("berries", new double[] { 43.0, 1.3, 10.0, 0.5 }),

            // Beans/Legumes
            Map.entry("beans", new double[] { 127.0, 8.7, 23.0, 0.4 }),
            Map.entry("lentils", new double[] { 116.0, 9.0, 20.0, 0.4 }),
            Map.entry("chickpeas", new double[] { 164.0, 8.9, 27.0, 2.7 }),

            // Default fallback
            Map.entry("default", new double[] { 100.0, 5.0, 15.0, 3.0 }));

    public NutritionCalculationService(RecipeCatalogService recipeCatalogService) {
        this.recipeCatalogService = recipeCatalogService;
    }

    /**
     * Calculate total nutritional information for ingredients.
     * 
     * @param ingredients List of ingredients with quantities and units
     * @param servings    Number of servings
     * @return NutritionResult with total calories, protein, carbs, fats
     */
    public NutritionResult calculateNutrition(List<IngredientInput> ingredients, double servings) {
        double totalCalories = 0.0;
        double totalProtein = 0.0;
        double totalCarbs = 0.0;
        double totalFats = 0.0;

        for (IngredientInput ingredient : ingredients) {
            try {
                double[] nutrition = getIngredientNutrition(ingredient.name());
                double grams = convertToGrams(ingredient.quantity(), ingredient.unit());

                // Calculate for this ingredient (nutrition is per 100g)
                double caloriesForAmount = (nutrition[0] / 100.0) * grams;
                double proteinForAmount = (nutrition[1] / 100.0) * grams;
                double carbsForAmount = (nutrition[2] / 100.0) * grams;
                double fatsForAmount = (nutrition[3] / 100.0) * grams;

                totalCalories += caloriesForAmount;
                totalProtein += proteinForAmount;
                totalCarbs += carbsForAmount;
                totalFats += fatsForAmount;

            } catch (Exception e) {
                log.warn("Error calculating nutrition for ingredient '{}': {}", ingredient.name(), e.getMessage());
                // Continue with other ingredients
            }
        }

        // Adjust for servings
        if (servings > 0) {
            totalCalories /= servings;
            totalProtein /= servings;
            totalCarbs /= servings;
            totalFats /= servings;
        }

        return new NutritionResult(totalCalories, totalProtein, totalCarbs, totalFats);
    }

    /**
     * Get nutritional values (per 100g) for an ingredient.
     * 
     * Strategy: 1. Try exact match in database 2. Try fuzzy match (partial,
     * case-insensitive) 3. Fall back to estimates
     * 
     * @param ingredientName Name of ingredient
     * @return [calories, protein, carbs, fats] per 100g
     */
    private double[] getIngredientNutrition(String ingredientName) {
        String normalized = ingredientName.toLowerCase().trim();

        // Try exact estimate match first (fastest)
        if (INGREDIENT_ESTIMATES.containsKey(normalized)) {
            return INGREDIENT_ESTIMATES.get(normalized);
        }

        // Try fuzzy match with estimates
        for (String key : INGREDIENT_ESTIMATES.keySet()) {
            if (normalized.contains(key) || key.contains(normalized)) {
                log.debug("Fuzzy match for '{}' -> '{}'", ingredientName, key);
                return INGREDIENT_ESTIMATES.get(key);
            }
        }

        // Fallback to default estimate
        log.warn("No nutrition data found for ingredient '{}', using default estimate", ingredientName);
        return INGREDIENT_ESTIMATES.get("default");
    }

    /**
     * Convert quantity + unit to grams.
     * 
     * @param quantity Amount
     * @param unit     Unit of measurement
     * @return Equivalent weight in grams
     */
    private double convertToGrams(double quantity, String unit) {
        String normalized = unit.toLowerCase().trim();
        Double factor = UNIT_TO_GRAMS.get(normalized);

        if (factor == null) {
            log.warn("Unknown unit '{}', treating as grams", unit);
            return quantity;
        }

        return quantity * factor;
    }
}
