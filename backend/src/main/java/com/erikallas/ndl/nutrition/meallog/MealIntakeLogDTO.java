package com.erikallas.ndl.nutrition.meallog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for meal intake logs. Represents a logged meal with all relevant
 * information.
 */
public record MealIntakeLogDTO(UUID id, LocalDate consumedDate, LocalTime consumedTime, String mealType,
        BigDecimal servings, BigDecimal caloriesKcal, BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatsG,
        String customMealName, String customMealNotes, UUID recipeId, UUID mealPlanMealId, OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
