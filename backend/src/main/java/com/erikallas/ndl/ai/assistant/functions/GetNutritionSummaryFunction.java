package com.erikallas.ndl.ai.assistant.functions;

import com.erikallas.ndl.nutrition.dashboard.NutritionDashboardService;
import com.erikallas.ndl.nutrition.dashboard.NutritionSummaryModels.DailySummary;
import com.erikallas.ndl.nutrition.dashboard.NutritionSummaryModels.NutrientProgress;
import com.erikallas.ndl.nutrition.dashboard.NutritionTrendService;
import com.erikallas.ndl.nutrition.dashboard.NutritionTrendService.DailyTrendPoint;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Function handler for get_nutrition_summary.
 *
 * Returns calorie and macro intake vs. targets for today or an aggregated 7-day
 * rolling window. All values use standardised units: kcal for energy, g for
 * macros. userId is always sourced from the authenticated JWT.
 */
@Component
public class GetNutritionSummaryFunction {

    private final NutritionDashboardService dashboardService;
    private final NutritionTrendService trendService;
    private final ObjectMapper om;

    public GetNutritionSummaryFunction(NutritionDashboardService dashboardService, NutritionTrendService trendService,
            ObjectMapper om) {
        this.dashboardService = dashboardService;
        this.trendService = trendService;
        this.om = om;
    }

    /**
     * @param userId Authenticated user ID.
     * @param period "today" or "week".
     */
    public String execute(UUID userId, String period) {
        if (period == null)
            period = "today";

        ObjectNode result = om.createObjectNode();

        if ("week".equals(period)) {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(6);
            result.put("period", "week");
            result.put("week_start", start.toString());
            result.put("week_end", end.toString());

            List<DailyTrendPoint> dailyTrends = trendService.getDailyTrends(userId, start, end);

            int daysWithData = (int) dailyTrends.stream().filter(d -> d.consumed() > 0).count();
            int totalConsumed = dailyTrends.stream().mapToInt(DailyTrendPoint::consumed).sum();
            int totalTarget = dailyTrends.stream().mapToInt(DailyTrendPoint::target).sum();
            int avgConsumed = daysWithData > 0 ? totalConsumed / daysWithData : 0;
            int dailyTarget = dailyTrends.isEmpty() ? 0 : dailyTrends.get(0).target();

            result.put("days_with_logged_data", daysWithData);
            result.put("total_calories_kcal", totalConsumed);
            result.put("total_calorie_target_kcal", totalTarget);
            result.put("avg_daily_calories_kcal", avgConsumed);
            result.put("daily_calorie_target_kcal", dailyTarget);
            int weekPct = totalTarget > 0 ? (totalConsumed * 100 / totalTarget) : 0;
            result.put("total_percentage_of_target", weekPct);
            result.put("total_calorie_deficit_or_surplus_kcal", totalConsumed - totalTarget);

            // Daily breakdown for context (calories only to keep tokens low)
            ArrayNode breakdown = result.putArray("daily_breakdown");
            for (DailyTrendPoint day : dailyTrends) {
                ObjectNode dayNode = om.createObjectNode();
                dayNode.put("date", day.date().toString());
                dayNode.put("day_of_week", day.date().getDayOfWeek().toString());
                dayNode.put("calories_kcal", day.consumed());
                dayNode.put("target_kcal", day.target());
                dayNode.put("deficit_or_surplus_kcal", day.consumed() - day.target());
                breakdown.add(dayNode);
            }

        } else {
            // Today — full macro breakdown
            DailySummary summary = dashboardService.getDailySummary(userId);
            result.put("period", "today");
            result.put("date", LocalDate.now().toString());
            result.put("total_meals_logged", summary.totalMeals());

            appendNutrient(result, "calories", summary.calories(), "kcal");
            appendNutrient(result, "protein", summary.protein(), "g");
            appendNutrient(result, "carbs", summary.carbs(), "g");
            appendNutrient(result, "fats", summary.fats(), "g");

            // Overall status hint for the AI
            if (summary.calories() != null) {
                int pct = summary.calories().percentageOfTarget();
                result.put("overall_status", pct < 80 ? "significantly_under_target"
                        : pct < 95 ? "slightly_under_target" : pct <= 105 ? "on_target" : "over_target");
            }
        }

        return result.toString();
    }

    private void appendNutrient(ObjectNode root, String key, NutrientProgress progress, String unit) {
        if (progress == null)
            return;
        ObjectNode n = root.putObject(key);
        n.put("consumed", safeDouble(progress.actual()));
        n.put("target", safeDouble(progress.target()));
        n.put("percentage_of_target", progress.percentageOfTarget());
        n.put("surplus_or_deficit", safeDouble(progress.surplus()));
        n.put("unit", unit);
        n.put("status", progress.status());
    }

    private static double safeDouble(BigDecimal val) {
        return val == null ? 0.0 : val.doubleValue();
    }
}
