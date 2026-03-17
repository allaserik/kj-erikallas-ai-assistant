package com.erikallas.ndl.nutrition.dashboard;

import com.erikallas.ndl.nutrition.meallog.MealIntakeLogService;
import com.erikallas.ndl.nutrition.preferences.NutritionPreferencesService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for calculating nutrition trends (daily deficit/surplus over time).
 * 
 * Enables visualization of caloric deficit/surplus on daily, weekly, and
 * monthly basis. Used for trend charts and progress tracking.
 */
@Service
public class NutritionTrendService {

    private static final Logger logger = LoggerFactory.getLogger(NutritionTrendService.class);

    private final MealIntakeLogService mealIntakeLogService;
    private final NutritionPreferencesService preferencesService;

    public NutritionTrendService(MealIntakeLogService mealIntakeLogService,
            NutritionPreferencesService preferencesService) {
        this.mealIntakeLogService = mealIntakeLogService;
        this.preferencesService = preferencesService;
    }

    /**
     * Get daily caloric trends for a date range.
     * 
     * @param userId    User to calculate trends for
     * @param startDate Start of range (inclusive)
     * @param endDate   End of range (inclusive)
     * @return List of daily trend points sorted by date
     */
    @Transactional(readOnly = true)
    public List<DailyTrendPoint> getDailyTrends(UUID userId, LocalDate startDate, LocalDate endDate) {
        // Get user's calorie target
        var prefs = preferencesService.getOrCreateWithPrefill(userId);
        int targetCalories = prefs.calorieTargetKcal();

        // Fetch all meals in range
        var meals = mealIntakeLogService.getMealsForDateRange(userId, startDate, endDate);

        // Group meals by date and calculate daily totals
        Map<LocalDate, BigDecimal> dailyTotals = new TreeMap<>();
        for (var meal : meals) {
            LocalDate mealDate = meal.getConsumedDate();
            BigDecimal calories = meal.getCaloriesKcal() != null ? meal.getCaloriesKcal() : BigDecimal.ZERO;
            dailyTotals.put(mealDate, dailyTotals.getOrDefault(mealDate, BigDecimal.ZERO).add(calories));
        }

        // For dates with no meals, add zero entries
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dailyTotals.putIfAbsent(date, BigDecimal.ZERO);
        }

        // Convert to trend points with surplus/deficit calculations
        List<DailyTrendPoint> trends = new ArrayList<>();
        for (var entry : dailyTotals.entrySet()) {
            LocalDate date = entry.getKey();
            BigDecimal consumed = entry.getValue();
            BigDecimal target = BigDecimal.valueOf(targetCalories);
            BigDecimal deficit = target.subtract(consumed); // Positive = deficit, Negative = surplus

            trends.add(new DailyTrendPoint(date, consumed.intValue(), targetCalories, deficit.intValue()));
        }

        logger.debug("Calculated daily trends for user {} from {} to {}: {} days", userId, startDate, endDate,
                trends.size());
        return trends;
    }

    /**
     * Get weekly summary trends.
     * 
     * Each week starting Monday, shows total consumed vs target, and average daily
     * deficit/surplus.
     */
    @Transactional(readOnly = true)
    public List<WeeklyTrendPoint> getWeeklyTrends(UUID userId, LocalDate startDate, LocalDate endDate) {
        var dailyTrends = getDailyTrends(userId, startDate, endDate);

        Map<String, List<DailyTrendPoint>> weekGroups = new TreeMap<>();
        for (var daily : dailyTrends) {
            // Group by ISO week (Monday start)
            String weekKey = daily.date().toString().substring(0, 7); // YYYY-MM format, then map to week
            LocalDate monday = getMondayOfWeek(daily.date());
            String key = monday + "|" + daily.date().getYear() + "-W" + getWeekNumber(daily.date());

            weekGroups.putIfAbsent(key, new ArrayList<>());
            weekGroups.get(key).add(daily);
        }

        List<WeeklyTrendPoint> weekly = new ArrayList<>();
        for (var entry : weekGroups.entrySet()) {
            var dailyPoints = entry.getValue();
            if (dailyPoints.isEmpty())
                continue;

            LocalDate weekStart = dailyPoints.get(0).date();
            LocalDate weekEnd = dailyPoints.get(dailyPoints.size() - 1).date();

            // Sum totals for the week
            int totalConsumed = dailyPoints.stream().mapToInt(DailyTrendPoint::consumed).sum();
            int totalTarget = dailyPoints.stream().mapToInt(DailyTrendPoint::target).sum();
            int avgDeficit = (int) dailyPoints.stream().mapToInt(DailyTrendPoint::deficit).average().orElse(0);

            weekly.add(new WeeklyTrendPoint(weekStart, weekEnd, totalConsumed, totalTarget, avgDeficit,
                    dailyPoints.size()));
        }

        return weekly;
    }

    /**
     * Get monthly summary trends.
     * 
     * One entry per month showing total consumed vs target.
     */
    @Transactional(readOnly = true)
    public List<MonthlySummaryPoint> getMonthlySummaries(UUID userId, LocalDate startDate, LocalDate endDate) {
        var dailyTrends = getDailyTrends(userId, startDate, endDate);

        Map<String, List<DailyTrendPoint>> monthGroups = new TreeMap<>();
        for (var daily : dailyTrends) {
            String monthKey = daily.date().toString().substring(0, 7); // YYYY-MM
            monthGroups.putIfAbsent(monthKey, new ArrayList<>());
            monthGroups.get(monthKey).add(daily);
        }

        List<MonthlySummaryPoint> monthly = new ArrayList<>();
        for (var entry : monthGroups.entrySet()) {
            var dailyPoints = entry.getValue();
            if (dailyPoints.isEmpty())
                continue;

            LocalDate monthStart = dailyPoints.get(0).date();
            LocalDate monthEnd = dailyPoints.get(dailyPoints.size() - 1).date();

            int totalConsumed = dailyPoints.stream().mapToInt(DailyTrendPoint::consumed).sum();
            int totalTarget = dailyPoints.stream().mapToInt(DailyTrendPoint::target).sum();
            int deficit = totalTarget - totalConsumed;

            monthly.add(new MonthlySummaryPoint(monthStart, monthEnd, totalConsumed, totalTarget, deficit,
                    dailyPoints.size()));
        }

        return monthly;
    }

    // Helper to get Monday of a given week
    private LocalDate getMondayOfWeek(LocalDate date) {
        return date.minus(date.getDayOfWeek().getValue() - 1, ChronoUnit.DAYS);
    }

    // Helper to get ISO week number
    private int getWeekNumber(LocalDate date) {
        return date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /**
     * Daily trend point: one entry per day with consumed calories and deficit.
     */
    public record DailyTrendPoint(LocalDate date, int consumed, int target, int deficit) {
        // deficit = target - consumed
        // positive deficit = under target
        // negative deficit = over target
    }

    /**
     * Weekly trend point: aggregates a full week.
     */
    public record WeeklyTrendPoint(LocalDate weekStart, LocalDate weekEnd, int totalConsumed, int totalTarget,
            int avgDailyDeficit, int daysLogged) {
    }

    /**
     * Monthly summary point: aggregates full month.
     */
    public record MonthlySummaryPoint(LocalDate monthStart, LocalDate monthEnd, int totalConsumed, int totalTarget,
            int totalDeficit, int daysLogged) {
    }
}
