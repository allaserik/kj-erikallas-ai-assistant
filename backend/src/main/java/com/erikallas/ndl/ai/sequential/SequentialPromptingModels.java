package com.erikallas.ndl.ai.sequential;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Data models for sequential meal planning prompting.
 * 
 * Phase 3: Multi-step orchestration - Step 1: Extract preferences from user
 * request - Step 2: Retrieve recipes via semantic search - Step 3: Calculate
 * nutrition for combinations - Step 4: Generate meal plan
 */
public class SequentialPromptingModels {

    /**
     * User's meal planning request parsed for AI processing.
     */
    public record MealPlanningRequest(String query, int numberOfDays, String mealType, Integer calorieTarget,
            Integer proteinTargetG, List<String> dietaryPreferences, List<String> cuisinePreferences,
            List<String> allergies) {
    }

    /**
     * Recipe context with semantic relevance score.
     */
    public record ScoredRecipe(UUID recipeId, String title, String cuisine, String mealType, int timeMinutes,
            double relevanceScore, double caloriesKcal, double proteinG, double carbsG, double fatsG) {
    }

    /**
     * Meal slot in a generated plan.
     */
    public record PlannedMeal(String dayLabel, String mealType, UUID recipeId, String recipeTitle, int servings,
            double caloriesKcal, double proteinG, double carbsG, double fatsG) {
    }

    /**
     * Daily nutrition summary for a meal plan.
     */
    public record DailyNutritionSummary(String dayLabel, double totalCaloriesKcal, double totalProteinG,
            double totalCarbsG, double totalFatsG, List<PlannedMeal> meals) {
    }

    /**
     * Complete generated meal plan response.
     */
    public record MealPlanResponse(String query, int numberOfDays, double totalCalories, double totalProtein,
            List<DailyNutritionSummary> days, String planningNotes) {
    }

    /**
     * Step-by-step planning status for tracking.
     */
    public record PlanningStep(String stepName, String status, String description, Object data) {
    }

    private SequentialPromptingModels() {
    }
}
