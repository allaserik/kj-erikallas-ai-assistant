package com.erikallas.ndl.ai.assistant.functions;

import com.erikallas.ndl.nutrition.mealplan.MealPlanModels;
import com.erikallas.ndl.nutrition.mealplan.MealPlanQueryService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Function handler for get_meal_plan.
 *
 * Fetches the user's latest meal plan and filters meals by the requested period
 * ("today", "tomorrow", "week"). Returns a compact summary without
 * ingredient-level details (those are too token-heavy for the assistant context
 * window). userId is always sourced from the authenticated JWT.
 */
@Component
public class GetMealPlanFunction {

    private final MealPlanQueryService queryService;
    private final ObjectMapper om;

    public GetMealPlanFunction(MealPlanQueryService queryService, ObjectMapper om) {
        this.queryService = queryService;
        this.om = om;
    }

    /**
     * @param userId Authenticated user ID.
     * @param period "today", "tomorrow", or "week".
     */
    public String execute(UUID userId, String period) {
        if (period == null)
            period = "today";

        MealPlanModels.GenerateResponse plan = queryService.getLatest(userId);
        ObjectNode result = om.createObjectNode();

        if (plan == null) {
            result.put("message", "No meal plan found. The user has not generated a meal plan yet.");
            return result.toString();
        }

        result.put("meal_plan_id", plan.mealPlanId().toString());
        result.put("period_type", plan.periodType());
        result.put("plan_start", plan.startDate().toString());
        result.put("plan_end", plan.endDate().toString());
        result.put("period_requested", period);

        LocalDate today = LocalDate.now();
        List<MealPlanModels.MealPlanDayResponse> days = plan.days();

        if ("week".equals(period)) {
            result.put("total_meals_in_plan", plan.totalMeals());
            ArrayNode daysArray = result.putArray("days");
            for (MealPlanModels.MealPlanDayResponse day : days) {
                ObjectNode dayNode = om.createObjectNode();
                dayNode.put("date", day.date().toString());
                dayNode.put("day_of_week", day.date().getDayOfWeek().toString());
                appendMeals(dayNode, day.meals());
                daysArray.add(dayNode);
            }
        } else {
            LocalDate targetDate = "tomorrow".equals(period) ? today.plusDays(1) : today;
            MealPlanModels.MealPlanDayResponse targetDay = days.stream().filter(d -> d.date().equals(targetDate))
                    .findFirst().orElse(null);

            if (targetDay == null) {
                result.put("message", "No meals are planned for " + period + " (" + targetDate
                        + "). The meal plan covers " + plan.startDate() + " to " + plan.endDate() + ".");
                return result.toString();
            }

            result.put("date", targetDay.date().toString());
            result.put("day_of_week", targetDay.date().getDayOfWeek().toString());
            appendMeals(result, targetDay.meals());
        }

        return result.toString();
    }

    private void appendMeals(ObjectNode parent, List<MealPlanModels.MealItemResponse> meals) {
        ArrayNode mealsArray = parent.putArray("meals");
        double totalCal = 0;
        for (MealPlanModels.MealItemResponse meal : meals) {
            ObjectNode m = om.createObjectNode();
            m.put("meal_type", meal.mealType());
            m.put("recipe_name", meal.recipeTitle());
            if (meal.cuisine() != null)
                m.put("cuisine", meal.cuisine());
            if (meal.caloriesKcal() != null) {
                double cal = meal.caloriesKcal().doubleValue();
                m.put("calories_kcal", Math.round(cal));
                totalCal += cal;
            }
            if (meal.proteinG() != null)
                m.put("protein_g", round1(meal.proteinG()));
            if (meal.carbsG() != null)
                m.put("carbs_g", round1(meal.carbsG()));
            if (meal.fatsG() != null)
                m.put("fats_g", round1(meal.fatsG()));
            if (meal.timeMinutes() != null)
                m.put("time_minutes", meal.timeMinutes());
            if (meal.difficultyLevel() != null)
                m.put("difficulty_level", meal.difficultyLevel());
            if (meal.ingredients() != null && !meal.ingredients().isEmpty()) {
                ArrayNode ingArr = m.putArray("ingredients");
                for (MealPlanModels.IngredientItem ing : meal.ingredients()) {
                    ObjectNode i = om.createObjectNode();
                    i.put("label", ing.label());
                    if (ing.quantity() != null)
                        i.put("quantity", ing.quantity().doubleValue());
                    if (ing.unit() != null)
                        i.put("unit", ing.unit());
                    ingArr.add(i);
                }
            }
            if (meal.preparation() != null && !meal.preparation().isEmpty()) {
                ArrayNode stepsArr = m.putArray("preparation_steps");
                for (MealPlanModels.PreparationStep step : meal.preparation()) {
                    ObjectNode s = om.createObjectNode();
                    s.put("step_number", step.stepNumber());
                    s.put("instruction", step.step());
                    if (step.description() != null && !step.description().isBlank())
                        s.put("detail", step.description());
                    stepsArr.add(s);
                }
            }
            mealsArray.add(m);
        }
        parent.put("total_calories_kcal", Math.round(totalCal));
        parent.put("total_meals", meals.size());
    }

    private static double round1(BigDecimal v) {
        return v == null ? 0.0 : Math.round(v.doubleValue() * 10.0) / 10.0;
    }
}
