package com.erikallas.ndl.nutrition.mealplan;

import com.erikallas.ndl.auth.user.UserService;
import com.erikallas.ndl.common.api.dto.ApiSuccess;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirement(name = "bearerAuth")
@RestController
public class MealPlanController {

    public static class GenerateMealPlanRequest {
        @NotBlank
        @JsonProperty("duration")
        public String duration;

        @JsonProperty("start_date")
        public LocalDate startDate;
    }

    private final UserService userService;
    private final MealPlanService mealPlanService;
    private final MealPlanManagementService mealPlanManagementService;

    public MealPlanController(UserService userService, MealPlanService mealPlanService,
            MealPlanManagementService mealPlanManagementService) {
        this.userService = userService;
        this.mealPlanService = mealPlanService;
        this.mealPlanManagementService = mealPlanManagementService;
    }

    @PostMapping("/api/meal-plans/generate")
    public ApiSuccess<MealPlanModels.GenerateResponse> generate(@Valid @RequestBody GenerateMealPlanRequest request,
            JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        var result = mealPlanService.generate(user.getId(),
                new MealPlanModels.GenerateCommand(request.duration, request.startDate));
        return ApiSuccess.of(result);
    }

    @GetMapping("/api/meal-plans/{mealPlanId}")
    public ApiSuccess<MealPlanModels.GenerateResponse> getById(@PathVariable("mealPlanId") UUID mealPlanId,
            JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        return ApiSuccess.of(mealPlanService.getById(user.getId(), mealPlanId));
    }

    @GetMapping("/api/meal-plans/latest")
    public ApiSuccess<MealPlanModels.GenerateResponse> getLatest(JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        return ApiSuccess.of(mealPlanService.getLatest(user.getId()));
    }

    /**
     * POST /api/meal-plans/meals/reorder
     * 
     * Reorder meals within the same day.
     * 
     * Request body: { mealIds: [id1, id2, id3, ...] } All meal IDs must be on the
     * same day.
     */
    @PostMapping("/api/meal-plans/meals/reorder")
    public ApiSuccess<String> reorderMeals(@RequestBody ReorderMealsRequest request, JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        mealPlanManagementService.reorderMealsInDay(request.mealIds(), now);
        return ApiSuccess.of("Meals reordered successfully");
    }

    /**
     * POST /api/meal-plans/meals/swap
     * 
     * Swap two meals. Can be on the same day or different days.
     * 
     * Request body: { mealId1: "uuid", mealId2: "uuid" }
     */
    @PostMapping("/api/meal-plans/meals/swap")
    public ApiSuccess<String> swapMeals(@RequestBody SwapMealsRequest request, JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        mealPlanManagementService.swapMeals(request.mealId1(), request.mealId2(), now);
        return ApiSuccess.of("Meals swapped successfully");
    }

    /**
     * POST /api/meal-plans/meals/{mealId}/replace
     * 
     * Replace a meal with a different recipe.
     * 
     * Request body: { recipeId: "uuid" }
     */
    @PostMapping("/api/meal-plans/meals/{mealId}/replace")
    public ApiSuccess<String> replaceMeal(@PathVariable UUID mealId, @RequestBody ReplaceMealRequest request,
            JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        mealPlanManagementService.replaceMeal(mealId, request.recipeId(), now);
        return ApiSuccess.of("Meal replaced successfully");
    }

    // ===== Request DTOs =====

    public record ReorderMealsRequest(List<UUID> mealIds) {
    }

    public record SwapMealsRequest(UUID mealId1, UUID mealId2) {
    }

    public record ReplaceMealRequest(UUID recipeId) {
    }
}
