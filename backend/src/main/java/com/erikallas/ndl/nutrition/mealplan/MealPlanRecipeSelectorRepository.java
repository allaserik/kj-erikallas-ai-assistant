package com.erikallas.ndl.nutrition.mealplan;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface MealPlanRecipeSelectorRepository {
    List<RecipeCandidate> findCandidates(
            String mealType,
            List<String> preferredCuisines,
            List<String> dietaryTags,
            List<String> blockedIngredientTerms,
            Set<UUID> excludedRecipeIds,
            int limit);

    record RecipeCandidate(
            UUID id,
            String title,
            String meal,
            String cuisine,
            int timeMinutes,
            BigDecimal caloriesKcal,
            BigDecimal proteinG,
            BigDecimal carbsG,
            BigDecimal fatsG) {
    }
}
