package com.erikallas.ndl.nutrition.dashboard;

import com.erikallas.ndl.nutrition.meallog.MealIntakeLogService;
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

    private final MealIntakeLogService mealIntakeLogService;
    private final NutritionPreferencesService preferencesService;

    public NutritionDashboardService(MealIntakeLogService mealIntakeLogService,
            NutritionPreferencesService preferencesService) {
        this.mealIntakeLogService = mealIntakeLogService;
        this.preferencesService = preferencesService;
    }

    /**
     * Get today's nutrition summary.
     * 
     * Flow: 1. Fetch meals actually logged/consumed for today 2. Sum nutrition from
     * logged meals 3. Get user targets 4. Compare and calculate percentages 5.
     * Return formatted summary
     */
    @Transactional(readOnly = true)
    public NutritionSummaryModels.DailySummary getDailySummary(UUID userId) {
        LocalDate today = LocalDate.now();

        try {
            // Step 1: Get meals logged for today (actual consumption)
            var todaysMeals = mealIntakeLogService.getMealsForDay(userId, today);

            // Step 2: Sum today's meals nutrition
            AggregatedNutrition totalNutrition = aggregateMealLogs(todaysMeals);

            // Step 3: Get user targets
            var preferences = preferencesService.getOrCreateWithPrefill(userId);

            // Step 4 & 5: Compare and format
            return buildSummary(today, totalNutrition, preferences);

        } catch (Exception e) {
            logger.warn("Error calculating nutrition summary for user {}", userId, e);
            return createEmptySummary(today);
        }
    }

    /**
     * Aggregate nutrition from meal logs.
     * 
     * Sums up all nutrition values from logged meals. Uses snapshots stored at log
     * time (not live recipe values) to preserve historical accuracy.
     */
    private AggregatedNutrition aggregateMealLogs(
            java.util.List<com.erikallas.ndl.nutrition.meallog.MealIntakeLogEntity> meals) {
        BigDecimal totalCalories = BigDecimal.ZERO;
        BigDecimal totalProtein = BigDecimal.ZERO;
        BigDecimal totalCarbs = BigDecimal.ZERO;
        BigDecimal totalFats = BigDecimal.ZERO;
        int mealCount = 0;

        for (var meal : meals) {
            if (meal.getCaloriesKcal() != null) {
                totalCalories = totalCalories.add(meal.getCaloriesKcal());
            }
            if (meal.getProteinG() != null) {
                totalProtein = totalProtein.add(meal.getProteinG());
            }
            if (meal.getCarbsG() != null) {
                totalCarbs = totalCarbs.add(meal.getCarbsG());
            }
            if (meal.getFatsG() != null) {
                totalFats = totalFats.add(meal.getFatsG());
            }
            mealCount++;
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
                "No meals logged yet. Start logging your meals to track your nutrition.", "generated");
    }

    /**
     * Internal record to hold aggregated totals. Keeps aggregation logic clean.
     */
    private record AggregatedNutrition(BigDecimal caloriesKcal, BigDecimal proteinG, BigDecimal carbsG,
            BigDecimal fatsG, int mealCount) {
    }
}
