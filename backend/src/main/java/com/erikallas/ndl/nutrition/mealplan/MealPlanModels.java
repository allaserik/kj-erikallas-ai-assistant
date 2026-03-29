package com.erikallas.ndl.nutrition.mealplan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class MealPlanModels {

        public record GenerateCommand(String duration, LocalDate startDate) {
        }

        public record IngredientItem(UUID id, String label, String unit, BigDecimal quantity, BigDecimal caloriesKcal,
                        BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatsG) {
        }

        public record PreparationStep(int stepNumber, String step, String description) {
        }

        public record MealItemResponse(UUID id, String mealType, UUID recipeId, String recipeTitle, String cuisine,
                        Integer timeMinutes, String difficultyLevel, String summary, String source, String img,
                        BigDecimal caloriesKcal, BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatsG,
                        List<IngredientItem> ingredients, List<PreparationStep> preparation) {
        }

        public record MealPlanDayResponse(LocalDate date, List<MealItemResponse> meals) {
        }

        public record GenerateResponse(UUID mealPlanId, String periodType, LocalDate startDate, LocalDate endDate,
                        int totalMeals, List<MealPlanDayResponse> days) {
        }

        private MealPlanModels() {
        }
}
