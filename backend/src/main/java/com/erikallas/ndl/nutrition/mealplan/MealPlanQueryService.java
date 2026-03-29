package com.erikallas.ndl.nutrition.mealplan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogService;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogModels;

@Service
public class MealPlanQueryService {

    private final MealPlanRepository mealPlanRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final RecipeCatalogService recipeCatalogService;

    public MealPlanQueryService(MealPlanRepository mealPlanRepository, NamedParameterJdbcTemplate jdbc,
            RecipeCatalogService recipeCatalogService) {
        this.mealPlanRepository = mealPlanRepository;
        this.jdbc = jdbc;
        this.recipeCatalogService = recipeCatalogService;
    }

    @Transactional(readOnly = true)
    public MealPlanModels.GenerateResponse getById(UUID userId, UUID mealPlanId) {
        MealPlanEntity plan = mealPlanRepository.findByIdAndUserId(mealPlanId, userId).orElse(null);
        if (plan == null) {
            return null;
        }
        return toResponse(plan);
    }

    @Transactional(readOnly = true)
    public MealPlanModels.GenerateResponse getLatest(UUID userId) {
        MealPlanEntity plan = mealPlanRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).orElse(null);
        if (plan == null) {
            return null;
        }
        return toResponse(plan);
    }

    private MealPlanModels.GenerateResponse toResponse(MealPlanEntity plan) {
        List<MealItemRow> rows = jdbc.query("""
                SELECT
                    m.id,
                    m.day_date,
                    m.meal_type,
                    m.recipe_id,
                    m.position_index
                FROM meal_plan_meals m
                WHERE m.meal_plan_id = :mealPlanId
                ORDER BY m.day_date ASC, m.position_index ASC
                """, Map.of("mealPlanId", plan.getId()),
                (rs, rowNum) -> new MealItemRow(rs.getObject("id", UUID.class),
                        rs.getObject("day_date", LocalDate.class), rs.getString("meal_type"),
                        rs.getObject("recipe_id", UUID.class)));

        Map<LocalDate, List<MealPlanModels.MealItemResponse>> dayToMeals = new LinkedHashMap<>();
        for (LocalDate day = plan.getStartDate(); !day.isAfter(plan.getEndDate()); day = day.plusDays(1)) {
            dayToMeals.put(day, new ArrayList<>());
        }
        for (MealItemRow row : rows) {
            List<MealPlanModels.MealItemResponse> meals = dayToMeals.computeIfAbsent(row.dayDate(),
                    ignored -> new ArrayList<>());

            try {
                // Fetch full recipe details from catalog
                RecipeCatalogModels.RecipeDetail recipeDetail = recipeCatalogService.getById(row.recipeId());

                if (recipeDetail != null) {
                    // Convert ingredients to response format
                    List<MealPlanModels.IngredientItem> ingredients = recipeDetail.ingredients().stream()
                            .map(ing -> new MealPlanModels.IngredientItem(ing.id(), ing.label(), ing.unit(),
                                    ing.quantity(), ing.nutrition().caloriesKcal(), ing.nutrition().proteinG(),
                                    ing.nutrition().carbsG(), ing.nutrition().fatsG()))
                            .collect(Collectors.toList());

                    // Convert preparation steps to response format
                    List<MealPlanModels.PreparationStep> preparation = recipeDetail.preparation().stream()
                            .map(step -> new MealPlanModels.PreparationStep(step.stepNumber(), step.step(),
                                    step.description()))
                            .collect(Collectors.toList());

                    var mealItem = new MealPlanModels.MealItemResponse(row.id(), row.mealType(), row.recipeId(),
                            recipeDetail.title(), recipeDetail.cuisine(), recipeDetail.timeMinutes(),
                            recipeDetail.difficultyLevel(), recipeDetail.summary(), recipeDetail.source(),
                            recipeDetail.img(), recipeDetail.nutrition().caloriesKcal(),
                            recipeDetail.nutrition().proteinG(), recipeDetail.nutrition().carbsG(),
                            recipeDetail.nutrition().fatsG(), ingredients, preparation);
                    meals.add(mealItem);
                }
            } catch (Exception e) {
                // Log error but don't fail - allow partial meal plan display
                System.err.println("Could not fetch recipe details for meal " + row.recipeId() + ": " + e.getMessage());
            }
        }

        List<MealPlanModels.MealPlanDayResponse> days = dayToMeals.entrySet().stream()
                .map(entry -> new MealPlanModels.MealPlanDayResponse(entry.getKey(), entry.getValue())).toList();

        int totalMeals = days.stream().mapToInt(d -> d.meals().size()).sum();
        return new MealPlanModels.GenerateResponse(plan.getId(), plan.getPeriodType(), plan.getStartDate(),
                plan.getEndDate(), totalMeals, days);
    }

    private record MealItemRow(UUID id, LocalDate dayDate, String mealType, UUID recipeId) {
    }
}
