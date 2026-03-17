package com.erikallas.ndl.nutrition.dashboard;

import com.erikallas.ndl.auth.user.UserService;
import com.erikallas.ndl.common.api.dto.ApiSuccess;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public NutritionDashboardController(UserService userService, NutritionDashboardService nutritionDashboardService) {
        this.userService = userService;
        this.nutritionDashboardService = nutritionDashboardService;
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
}
