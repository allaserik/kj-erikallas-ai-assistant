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

            // --- Weekly calorie trend bar chart ---
            ArrayNode consumedPts = om.createArrayNode();
            ArrayNode targetPts = om.createArrayNode();
            for (DailyTrendPoint day : dailyTrends) {
                String label = day.date().getDayOfWeek().toString().substring(0, 3);
                ObjectNode cp = om.createObjectNode();
                cp.put("x", label);
                cp.put("y", day.consumed());
                consumedPts.add(cp);
                ObjectNode tp = om.createObjectNode();
                tp.put("x", label);
                tp.put("y", day.target());
                targetPts.add(tp);
            }
            ObjectNode weekChart = result.putObject("_chart_data");
            weekChart.put("type", "bar");
            weekChart.put("title", "Calorie Intake vs Target (7 Days)");
            ArrayNode weekSeries = weekChart.putArray("series");
            ObjectNode cs = om.createObjectNode();
            cs.put("label", "Consumed");
            cs.put("unit", "kcal");
            cs.set("data", consumedPts);
            weekSeries.add(cs);
            ObjectNode ts = om.createObjectNode();
            ts.put("label", "Target");
            ts.put("unit", "kcal");
            ts.set("data", targetPts);
            weekSeries.add(ts);

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

            // --- Macro breakdown pie chart ---
            if (summary.protein() != null && summary.carbs() != null && summary.fats() != null) {
                ArrayNode protPts = om.createArrayNode();
                ObjectNode pp = om.createObjectNode();
                pp.put("x", "Protein");
                pp.put("y", safeDouble(summary.protein().actual()));
                protPts.add(pp);
                ArrayNode carbPts = om.createArrayNode();
                ObjectNode cp2 = om.createObjectNode();
                cp2.put("x", "Carbs");
                cp2.put("y", safeDouble(summary.carbs().actual()));
                carbPts.add(cp2);
                ArrayNode fatPts = om.createArrayNode();
                ObjectNode fp = om.createObjectNode();
                fp.put("x", "Fats");
                fp.put("y", safeDouble(summary.fats().actual()));
                fatPts.add(fp);

                ObjectNode todayChart = result.putObject("_chart_data");
                todayChart.put("type", "pie");
                todayChart.put("title", "Macro Breakdown Today");
                ArrayNode todaySeries = todayChart.putArray("series");
                ObjectNode s1 = om.createObjectNode();
                s1.put("label", "Protein");
                s1.put("unit", "g");
                s1.set("data", protPts);
                todaySeries.add(s1);
                ObjectNode s2 = om.createObjectNode();
                s2.put("label", "Carbs");
                s2.put("unit", "g");
                s2.set("data", carbPts);
                todaySeries.add(s2);
                ObjectNode s3 = om.createObjectNode();
                s3.put("label", "Fats");
                s3.put("unit", "g");
                s3.set("data", fatPts);
                todaySeries.add(s3);
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
