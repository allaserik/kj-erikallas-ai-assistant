package com.erikallas.ndl.nutrition.dashboard;

import com.erikallas.ndl.auth.user.UserService;
import com.erikallas.ndl.common.api.dto.ApiSuccess;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for nutrition dashboard data.
 * 
 * Why separate controller? - Isolates HTTP concerns (request/response handling)
 * from business logic - Other endpoints could use the same service (e.g.,
 * weekly endpoint uses same service + filters) - Easier to add caching headers,
 * documentation, etc.
 */
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/nutrition/dashboard")
public class NutritionDashboardController {

    private final UserService userService;
    private final NutritionDashboardService nutritionDashboardService;
    private final NutritionTrendService nutritionTrendService;

    public NutritionDashboardController(UserService userService, NutritionDashboardService nutritionDashboardService,
            NutritionTrendService nutritionTrendService) {
        this.userService = userService;
        this.nutritionDashboardService = nutritionDashboardService;
        this.nutritionTrendService = nutritionTrendService;
    }

    /**
     * GET /api/nutrition/dashboard/summary
     * 
     * Returns today's nutrition summary with macros and status. Only accessible to
     * authenticated users (gets their own data).
     */
    @GetMapping("/summary")
    public ApiSuccess<NutritionSummaryModels.DailySummary> getTodaysSummary(JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        var summary = nutritionDashboardService.getDailySummary(user.getId());
        return ApiSuccess.of(summary);
    }

    /**
     * GET
     * /api/nutrition/dashboard/trends/daily?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD
     * 
     * Returns daily caloric deficit/surplus trends for a date range. Used for
     * visualizing daily trends over weeks or months.
     */
    @GetMapping("/trends/daily")
    public ApiSuccess<List<NutritionTrendService.DailyTrendPoint>> getDailyTrends(@RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate, JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        var trends = nutritionTrendService.getDailyTrends(user.getId(), startDate, endDate);
        return ApiSuccess.of(trends);
    }

    /**
     * GET
     * /api/nutrition/dashboard/trends/weekly?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD
     * 
     * Returns weekly aggregated trends with total consumed vs target for each week.
     */
    @GetMapping("/trends/weekly")
    public ApiSuccess<List<NutritionTrendService.WeeklyTrendPoint>> getWeeklyTrends(@RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate, JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        var trends = nutritionTrendService.getWeeklyTrends(user.getId(), startDate, endDate);
        return ApiSuccess.of(trends);
    }

    /**
     * GET
     * /api/nutrition/dashboard/trends/monthly?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD
     * 
     * Returns monthly aggregated trends with total consumed vs target for each
     * month.
     */
    @GetMapping("/trends/monthly")
    public ApiSuccess<List<NutritionTrendService.MonthlySummaryPoint>> getMonthlySummaries(
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate, JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        var summaries = nutritionTrendService.getMonthlySummaries(user.getId(), startDate, endDate);
        return ApiSuccess.of(summaries);
    }
}
