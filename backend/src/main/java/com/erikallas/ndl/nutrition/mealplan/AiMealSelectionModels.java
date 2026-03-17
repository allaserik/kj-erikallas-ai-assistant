package com.erikallas.ndl.nutrition.mealplan;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Models for AI-driven meal selection using sequential prompting. Three-step
 * process: 1. Analyze constraints → Nutrition Strategy 2. Design meal skeleton
 * → Meal Structure 3. Select recipes → Recipe Selection
 */
public class AiMealSelectionModels {

    /**
     * Step 1 Output: Nutrition strategy analysis
     */
    public record NutritionStrategy(int targetDailyCalories, BigDecimal targetDailyProteinG,
            BigDecimal targetDailyCarbsG, BigDecimal targetDailyFatsG, List<MealMacroAllocation> mealAllocations,
            String strategySummary) {

        public record MealMacroAllocation(String mealType, int caloriesKcal, BigDecimal proteinGrams,
                BigDecimal carbsGrams, BigDecimal fatsGrams) {
        }
    }

    /**
     * Step 2 Output: Meal structure design
     */
    public record MealStructure(List<MealSlotRequirement> mealSlots, String structureSummary) {

        public record MealSlotRequirement(int slotPosition, String mealType, String preferredCuisineProfile,
                int targetCaloriesKcal, BigDecimal targetProteinG, BigDecimal targetCarbsG, BigDecimal targetFatsG,
                int maxTimeMinutes, String constraints) {
        }
    }

    /**
     * Step 3 Output: Recipe selection results
     */
    public record RecipeSelection(List<MealSelection> selectedMeals, String selectionSummary,
            List<String> alternatives) {

        public record MealSelection(int slotPosition, String mealType, UUID recipeId, BigDecimal calories,
                BigDecimal protein, BigDecimal carbs, BigDecimal fats, String recipeTitle) {
        }
    }

    /**
     * Function schema for calculation functions used in prompts
     */
    public static class FunctionSchema {
        public static final String CALCULATE_NUTRITION = """
                {
                  "name": "calculate_daily_nutrition",
                  "description": "Calculate total nutritional values for selected meals",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "meals": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "properties": {
                            "calories": {"type": "number"},
                            "protein": {"type": "number"},
                            "carbs": {"type": "number"},
                            "fats": {"type": "number"}
                          }
                        }
                      }
                    },
                    "required": ["meals"]
                  }
                }
                """;

        public static final String VALIDATE_MACROS = """
                {
                  "name": "validate_macro_targets",
                  "description": "Verify if meal plan matches user macro targets within tolerance",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "actual_calories": {"type": "number"},
                      "target_calories": {"type": "number"},
                      "actual_protein": {"type": "number"},
                      "target_protein": {"type": "number"},
                      "tolerance_percent": {"type": "number", "default": 10}
                    },
                    "required": ["actual_calories", "target_calories", "actual_protein", "target_protein"]
                  }
                }
                """;
    }

    private AiMealSelectionModels() {
    }
}
