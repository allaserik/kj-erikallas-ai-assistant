package com.erikallas.ndl.nutrition.mealplan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealPlanQueryService {

    private final MealPlanRepository mealPlanRepository;
    private final NamedParameterJdbcTemplate jdbc;

    public MealPlanQueryService(MealPlanRepository mealPlanRepository, NamedParameterJdbcTemplate jdbc) {
        this.mealPlanRepository = mealPlanRepository;
        this.jdbc = jdbc;
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
                    m.position_index,
                    COALESCE(r.title, 'Unknown recipe') AS recipe_title,
                    r.cuisine,
                    r.time_minutes,
                    COALESCE(r.calories_kcal, 0) AS calories_kcal,
                    COALESCE(r.protein_g, 0) AS protein_g,
                    COALESCE(r.carbs_g, 0) AS carbs_g,
                    COALESCE(r.fats_g, 0) AS fats_g
                FROM meal_plan_meals m
                LEFT JOIN recipes r ON r.id = m.recipe_id
                WHERE m.meal_plan_id = :mealPlanId
                ORDER BY m.day_date ASC, m.position_index ASC
                """, Map.of("mealPlanId", plan.getId()), (rs, rowNum) -> new MealItemRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("day_date", LocalDate.class),
                        rs.getString("meal_type"),
                        rs.getObject("recipe_id", UUID.class),
                        rs.getString("recipe_title"),
                        rs.getString("cuisine"),
                        rs.getObject("time_minutes", Integer.class),
                        rs.getBigDecimal("calories_kcal"),
                        rs.getBigDecimal("protein_g"),
                        rs.getBigDecimal("carbs_g"),
                        rs.getBigDecimal("fats_g")));

        Map<LocalDate, List<MealPlanModels.MealItemResponse>> dayToMeals = new LinkedHashMap<>();
        for (LocalDate day = plan.getStartDate(); !day.isAfter(plan.getEndDate()); day = day.plusDays(1)) {
            dayToMeals.put(day, new ArrayList<>());
        }
        for (MealItemRow row : rows) {
            List<MealPlanModels.MealItemResponse> meals = dayToMeals.computeIfAbsent(row.dayDate(),
                    ignored -> new ArrayList<>());
            meals.add(new MealPlanModels.MealItemResponse(
                    row.id(),
                    row.mealType(),
                    row.recipeId(),
                    row.recipeTitle(),
                    row.cuisine(),
                    row.timeMinutes(),
                    row.caloriesKcal(),
                    row.proteinG(),
                    row.carbsG(),
                    row.fatsG()));
        }

        List<MealPlanModels.MealPlanDayResponse> days = dayToMeals.entrySet().stream()
                .map(entry -> new MealPlanModels.MealPlanDayResponse(entry.getKey(), entry.getValue()))
                .toList();

        int totalMeals = days.stream().mapToInt(d -> d.meals().size()).sum();
        return new MealPlanModels.GenerateResponse(
                plan.getId(),
                plan.getPeriodType(),
                plan.getStartDate(),
                plan.getEndDate(),
                totalMeals,
                days);
    }

    private record MealItemRow(
            UUID id,
            LocalDate dayDate,
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
}
