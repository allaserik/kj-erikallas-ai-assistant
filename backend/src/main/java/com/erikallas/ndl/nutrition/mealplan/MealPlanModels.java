package com.erikallas.ndl.nutrition.mealplan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class MealPlanModels {

    public record GenerateCommand(String duration, LocalDate startDate) {
    }

    public record MealItemResponse(
            UUID id,
            String mealType,
            UUID recipeId,
            String recipeTitle,
            String cuisine,
            Integer timeMinutes,
            BigDecimal caloriesKcal,
            BigDecimal proteinG,
            BigDecimal carbsG,
            BigDecimal fatsG) {
    }

    public record MealPlanDayResponse(LocalDate date, List<MealItemResponse> meals) {
    }

    public record GenerateResponse(
            UUID mealPlanId,
            String periodType,
            LocalDate startDate,
            LocalDate endDate,
            int totalMeals,
            List<MealPlanDayResponse> days) {
    }

    private MealPlanModels() {
    }
}
