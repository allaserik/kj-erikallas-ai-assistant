package com.erikallas.ndl.nutrition.meallog;

import com.erikallas.ndl.common.api.dto.ApiSuccess;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API endpoints for meal intake logging.
 * 
 * Allows users to: - Log meals from meal plans - Log meals from recipes - Log
 * custom meals - View logged meals - Delete meal logs
 */
@RestController
public class MealIntakeLogController {

    private final MealIntakeLogService service;

    public MealIntakeLogController(MealIntakeLogService service) {
        this.service = service;
    }

    /**
     * Log a meal from a recipe.
     * 
     * POST /api/nutrition/meals/log
     * 
     * Request body: { "recipeId": "uuid", "servings": 1.0, "mealType": "breakfast",
     * "consumedTime": "08:30" }
     */
    @PostMapping("/api/nutrition/meals/log")
    public ResponseEntity<ApiSuccess<MealIntakeLogDTO>> logMealFromRecipe(@RequestBody LogMealRequest request,
            Authentication auth) {

        UUID userId = UUID.fromString(auth.getName());
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("UTC"));

        MealIntakeLogEntity logged = service.logMealFromRecipe(userId, request.recipeId(), request.servings(),
                request.mealType(), request.consumedTime(), now);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccess.of(toDTO(logged)));
    }

    /**
     * Log a custom meal.
     * 
     * POST /api/nutrition/meals/log-custom
     * 
     * Request body: { "mealName": "Pasta Carbonara", "mealType": "dinner",
     * "calories": 500, "protein": 25, "carbs": 60, "fats": 20, "notes": "Made with
     * eggs and bacon", "consumedTime": "19:30" }
     */
    @PostMapping("/api/nutrition/meals/log-custom")
    public ResponseEntity<ApiSuccess<MealIntakeLogDTO>> logCustomMeal(@RequestBody LogCustomMealRequest request,
            Authentication auth) {

        UUID userId = UUID.fromString(auth.getName());
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("UTC"));

        MealIntakeLogEntity logged = service.logCustomMeal(userId, request.mealName(), request.mealType(),
                request.calories(), request.protein(), request.carbs(), request.fats(), request.notes(),
                request.consumedTime(), now);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccess.of(toDTO(logged)));
    }

    /**
     * Get all meals logged for a specific date.
     * 
     * GET /api/nutrition/meals/daily?date=2024-03-17
     */
    @GetMapping("/api/nutrition/meals/daily")
    public ResponseEntity<ApiSuccess<List<MealIntakeLogDTO>>> getMealsForDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Authentication auth) {

        UUID userId = UUID.fromString(auth.getName());
        List<MealIntakeLogEntity> meals = service.getMealsForDay(userId, date);

        return ResponseEntity.ok(ApiSuccess.of(meals.stream().map(this::toDTO).toList()));
    }

    /**
     * Get meals logged within a date range.
     * 
     * GET /api/nutrition/meals/range?startDate=2024-03-10&endDate=2024-03-17
     */
    @GetMapping("/api/nutrition/meals/range")
    public ResponseEntity<ApiSuccess<List<MealIntakeLogDTO>>> getMealsForDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, Authentication auth) {

        UUID userId = UUID.fromString(auth.getName());
        List<MealIntakeLogEntity> meals = service.getMealsForDateRange(userId, startDate, endDate);

        return ResponseEntity.ok(ApiSuccess.of(meals.stream().map(this::toDTO).toList()));
    }

    /**
     * Get a specific meal log by ID.
     * 
     * GET /api/nutrition/meals/{id}
     */
    @GetMapping("/api/nutrition/meals/{id}")
    public ResponseEntity<ApiSuccess<MealIntakeLogDTO>> getMealLog(@PathVariable UUID id, Authentication auth) {

        var log = service.getMealLog(id).orElseThrow(() -> new IllegalArgumentException("Meal log not found: " + id));

        return ResponseEntity.ok(ApiSuccess.of(toDTO(log)));
    }

    /**
     * Delete a meal log (soft delete).
     * 
     * DELETE /api/nutrition/meals/{id}
     */
    @DeleteMapping("/api/nutrition/meals/{id}")
    public ResponseEntity<Void> deleteMealLog(@PathVariable UUID id, Authentication auth) {

        // Verify the meal belongs to the authenticated user
        var log = service.getMealLog(id).orElseThrow(() -> new IllegalArgumentException("Meal log not found: " + id));

        UUID userId = UUID.fromString(auth.getName());
        if (!log.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("UTC"));
        service.deleteMealLog(id, now);

        return ResponseEntity.noContent().build();
    }

    // DTO conversion
    private MealIntakeLogDTO toDTO(MealIntakeLogEntity entity) {
        return new MealIntakeLogDTO(entity.getId(), entity.getConsumedDate(), entity.getConsumedTime(),
                entity.getMealType(), entity.getServings(), entity.getCaloriesKcal(), entity.getProteinG(),
                entity.getCarbsG(), entity.getFatsG(), entity.getCustomMealName(), entity.getCustomMealNotes(),
                entity.getRecipeId(), entity.getMealPlanMealId(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    // Request/Response records
    public record LogMealRequest(UUID recipeId, BigDecimal servings, String mealType, LocalTime consumedTime) {
    }

    public record LogCustomMealRequest(String mealName, String mealType, BigDecimal calories, BigDecimal protein,
            BigDecimal carbs, BigDecimal fats, String notes, LocalTime consumedTime) {
    }
}
