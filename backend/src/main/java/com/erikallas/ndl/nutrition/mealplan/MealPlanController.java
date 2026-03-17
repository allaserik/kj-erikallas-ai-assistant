package com.erikallas.ndl.nutrition.mealplan;

import com.erikallas.ndl.auth.user.UserService;
import com.erikallas.ndl.common.api.dto.ApiSuccess;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
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

    public MealPlanController(UserService userService, MealPlanService mealPlanService) {
        this.userService = userService;
        this.mealPlanService = mealPlanService;
    }

    @PostMapping("/api/meal-plans/generate")
    public ApiSuccess<MealPlanModels.GenerateResponse> generate(
            @Valid @RequestBody GenerateMealPlanRequest request,
            JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        var result = mealPlanService.generate(user.getId(),
                new MealPlanModels.GenerateCommand(request.duration, request.startDate));
        return ApiSuccess.of(result);
    }

    @GetMapping("/api/meal-plans/{mealPlanId}")
    public ApiSuccess<MealPlanModels.GenerateResponse> getById(
            @PathVariable("mealPlanId") UUID mealPlanId,
            JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        return ApiSuccess.of(mealPlanService.getById(user.getId(), mealPlanId));
    }

    @GetMapping("/api/meal-plans/latest")
    public ApiSuccess<MealPlanModels.GenerateResponse> getLatest(JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        return ApiSuccess.of(mealPlanService.getLatest(user.getId()));
    }
}
