package com.erikallas.ndl.nutrition.dashboard;

import com.erikallas.ndl.nutrition.mealplan.MealPlanQueryService;
import com.erikallas.ndl.nutrition.mealplan.MealPlanModels;
import com.erikallas.ndl.nutrition.preferences.NutritionPreferencesService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for aggregating nutrition data into daily summaries for the
 * dashboard.
 * 
 * Key responsibility: Take raw meal/recipe data and transform into
 * human-readable nutrition progress (percentages, surge/deficit, status).
 * 
 * Why separate service? - Can be used by dashboard, by nutrition page, by
 * reports - Calculation logic is centralized and testable - Backend handles all
 * math (frontend just renders)
 */
@Service
public class NutritionDashboardService {

    private static final Logger logger = LoggerFactory.getLogger(NutritionDashboardService.class);

    // Tolerance for "good" status: 95-105% is acceptable
    private static final int TOLERANCE_LOWER = 95;
    private static final int TOLERANCE_UPPER = 105;

    private final MealPlanQueryService mealPlanQueryService;
    private final NutritionPreferencesService preferencesService;

    public NutritionDashboardService(MealPlanQueryService mealPlanQueryService,
            NutritionPreferencesService preferencesService) {
        this.mealPlanQueryService = mealPlanQueryService;
        this.preferencesService = preferencesService;
    }

    /**
     * Get today's nutrition summary.
     * 
     * Flow: 1. Fetch latest meal plan 2. Filter today's meals 3. Sum nutrition from
     * meals 4. Get user targets 5. Compare and calculate percentages 6. Return
     * formatted summary
     */
    @Transactional(readOnly = true)
    public NutritionSummaryModels.DailySummary getDailySummary(UUID userId) {
        LocalDate today = LocalDate.now();

        try {
            // Step 1: Get latest meal plan for user
            var mealPlan = mealPlanQueryService.getLatest(userId);

            if (mealPlan == null) {
                logger.debug("No meal plan found for user {}, returning empty summary", userId);
                return createEmptySummary(today);
            }

            // Step 2 & 3: Extract and sum today's meals
            AggregatedNutrition totalNutrition = aggregateTodaysMeals(mealPlan, today);

            // Step 4: Get user targets
            var preferences = preferencesService.getOrCreateWithPrefill(userId);

            // Step 5 & 6: Compare and format
            return buildSummary(today, totalNutrition, preferences);

        } catch (Exception e) {
            logger.warn("Error calculating nutrition summary for user {}", userId, e);
            return createEmptySummary(today);
        }
    }

    /**
     * Extract and sum only today's meals from the meal plan.
     * 
     * Why keep this separate? - A meal plan might span multiple days, we only want
     * today - Can aggregate by different groupings later (week, etc.) - Easy to
     * test independently - Clear responsibility: just add things up
     */
    private AggregatedNutrition aggregateTodaysMeals(MealPlanModels.GenerateResponse mealPlan, LocalDate targetDate) {
        BigDecimal totalCalories = BigDecimal.ZERO;
        BigDecimal totalProtein = BigDecimal.ZERO;
        BigDecimal totalCarbs = BigDecimal.ZERO;
        BigDecimal totalFats = BigDecimal.ZERO;
        int mealCount = 0;

        // Loop through all days and find today's
        for (var dayPlan : mealPlan.days()) {
            // Only sum meals from the target date
            if (dayPlan.date().isEqual(targetDate)) {
                for (var meal : dayPlan.meals()) {
                    totalCalories = totalCalories.add(meal.caloriesKcal());
                    totalProtein = totalProtein.add(meal.proteinG());
                    totalCarbs = totalCarbs.add(meal.carbsG());
                    totalFats = totalFats.add(meal.fatsG());
                    mealCount++;
                }
            }
        }

        return new AggregatedNutrition(totalCalories, totalProtein, totalCarbs, totalFats, mealCount);
    }

    /**
     * Build the summary with all calculations.
     * 
     * The math: - percentage = (actual / target) * 100 - surplus = actual - target
     * - status = "good" if within tolerance, else "low" or "high"
     */
    private NutritionSummaryModels.DailySummary buildSummary(LocalDate date, AggregatedNutrition actual,
            NutritionPreferencesService.PreferencesResponse targets) {

        // Handle missing targets - use defaults or return neutral summary
        if (targets == null || targets.calorieTargetKcal() == null) {
            logger.debug("No nutrition targets set for user, returning neutral summary");
            BigDecimal zero = BigDecimal.ZERO;
            return new NutritionSummaryModels.DailySummary(date, actual.mealCount(),
                    new NutritionSummaryModels.NutrientProgress("Calories", actual.caloriesKcal(), zero, "kcal", 0,
                            zero, "neutral"),
                    new NutritionSummaryModels.NutrientProgress("Protein", actual.proteinG(), zero, "g", 0, zero,
                            "neutral"),
                    new NutritionSummaryModels.NutrientProgress("Carbs", actual.carbsG(), zero, "g", 0, zero,
                            "neutral"),
                    new NutritionSummaryModels.NutrientProgress("Fats", actual.fatsG(), zero, "g", 0, zero, "neutral"),
                    "Please set your nutrition targets in preferences to track progress", "generated");
        }

        // Calculate each macro's progress
        var caloriesProgress = calculateProgress("Calories", actual.caloriesKcal(),
                BigDecimal.valueOf(targets.calorieTargetKcal()), "kcal");

        var proteinProgress = calculateProgress("Protein", actual.proteinG(), targets.proteinTargetG(), "g");

        var carbsProgress = calculateProgress("Carbs", actual.carbsG(), targets.carbsTargetG(), "g");

        var fatsProgress = calculateProgress("Fats", actual.fatsG(), targets.fatsTargetG(), "g");

        // Build summary text (could be AI-generated later)
        String summary = generateSimpleSummary(caloriesProgress, proteinProgress, carbsProgress, fatsProgress);

        return new NutritionSummaryModels.DailySummary(date, actual.mealCount(), caloriesProgress, proteinProgress,
                carbsProgress, fatsProgress, summary, "generated");
    }

    /**
     * Calculate progress for a single nutrient.
     * 
     * This does the heavy lifting: percentage, surplus, status.
     */
    private NutritionSummaryModels.NutrientProgress calculateProgress(String name, BigDecimal actual, BigDecimal target,
            String unit) {

        // Handle edge case: no target set
        if (target.compareTo(BigDecimal.ZERO) <= 0) {
            return new NutritionSummaryModels.NutrientProgress(name, actual, target, unit, 0, actual, "neutral");
        }

        // Calculate percentage: (actual / target) * 100
        BigDecimal percentage = actual.divide(target, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP);

        // Calculate surplus: actual - target
        BigDecimal surplus = actual.subtract(target);

        // Determine status based on percentage
        // Why these ranges? ±5% gives flexibility while keeping focus on goals
        String status = "low";
        if (percentage.intValue() >= TOLERANCE_LOWER && percentage.intValue() <= TOLERANCE_UPPER) {
            status = "good";
        } else if (percentage.intValue() > TOLERANCE_UPPER) {
            status = "high";
        }

        return new NutritionSummaryModels.NutrientProgress(name, actual, target, unit, percentage.intValue(),
                surplus.setScale(1, RoundingMode.HALF_UP), status);
    }

    /**
     * Generate a simple text summary. Later, this could call AI for insights.
     */
    private String generateSimpleSummary(NutritionSummaryModels.NutrientProgress calories,
            NutritionSummaryModels.NutrientProgress protein, NutritionSummaryModels.NutrientProgress carbs,
            NutritionSummaryModels.NutrientProgress fats) {

        if (calories.actual().compareTo(BigDecimal.ZERO) == 0) {
            return "No meals logged yet";
        }

        // Build summary based on status
        StringBuilder sb = new StringBuilder();
        if (protein.status().equals("good")) {
            sb.append("Great protein! ");
        } else if (protein.status().equals("low")) {
            sb.append("Protein is low. ");
        }

        if (calories.status().equals("high")) {
            sb.append("Calories are higher than target.");
        } else if (calories.status().equals("low")) {
            sb.append("Calories are under target.");
        }

        return sb.length() > 0 ? sb.toString() : "Nutrition on track.";
    }

    private NutritionSummaryModels.DailySummary createEmptySummary(LocalDate date) {
        BigDecimal zero = BigDecimal.ZERO;
        return new NutritionSummaryModels.DailySummary(date, 0,
                new NutritionSummaryModels.NutrientProgress("Calories", zero, zero, "kcal", 0, zero, "neutral"),
                new NutritionSummaryModels.NutrientProgress("Protein", zero, zero, "g", 0, zero, "neutral"),
                new NutritionSummaryModels.NutrientProgress("Carbs", zero, zero, "g", 0, zero, "neutral"),
                new NutritionSummaryModels.NutrientProgress("Fats", zero, zero, "g", 0, zero, "neutral"),
                "No meal plan for today yet", "generated");
    }

    /**
     * Internal record to hold aggregated totals. Keeps aggregation logic clean.
     */
    private record AggregatedNutrition(BigDecimal caloriesKcal, BigDecimal proteinG, BigDecimal carbsG,
            BigDecimal fatsG, int mealCount) {
    }
}
