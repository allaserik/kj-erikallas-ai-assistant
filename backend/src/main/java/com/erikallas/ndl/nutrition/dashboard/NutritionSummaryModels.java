package com.erikallas.ndl.nutrition.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data models for daily nutrition summary on the dashboard.
 * 
 * These models represent the aggregated nutrition data for a specific day,
 * showing actual vs. target for each macro with calculated percentages and
 * status.
 */
public class NutritionSummaryModels {

        /**
         * Represents progress toward a single nutritional target. Used for calories,
         * protein, carbs, and fats.
         * 
         * Example: "Protein: 145g / 150g (97%)"
         */
        public record NutrientProgress(String name, // "Protein"
                        BigDecimal actual, // 145.5
                        BigDecimal target, // 150.0
                        String unit, // "g" or "kcal"
                        int percentageOfTarget, // 97 (0-200 is reasonable)
                        BigDecimal surplus, // -4.5 (negative = deficit, positive = excess)
                        String status // "good", "low", or "high"
        ) {
        }

        /**
         * Complete daily nutrition summary. Represents all macros for one day, ready to
         * display on dashboard.
         * 
         * Why all fields? - date: for filtering/caching on frontend - totalMeals: user
         * context (did they eat anything today?) - Four NutrientProgress records: one
         * for each macro - summary: for AI-driven insights (future phase) - source:
         * indicates if summary is rule-generated or AI-generated
         */
        public record DailySummary(LocalDate date, int totalMeals, NutrientProgress calories, NutrientProgress protein,
                        NutrientProgress carbs, NutrientProgress fats, String summary, // "Great protein! Need more
                                                                                       // carbs."
                        String source // "generated" (rule-based) or "ai" (future AI insights)
        ) {
        }

        private NutritionSummaryModels() {
        }
}
