package com.erikallas.ndl.nutrition.meallog;

import com.erikallas.ndl.nutrition.recipe.RecipeCatalogModels;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogQueryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing meal intake logs (what users actually consumed).
 * 
 * Key responsibilities: - Log when a user eats a meal (from plan or custom) -
 * Store nutrition snapshot at time of logging - Support custom serving size
 * adjustments - Handle soft deletes
 */
@Service
public class MealIntakeLogService {

    private static final Logger logger = LoggerFactory.getLogger(MealIntakeLogService.class);

    private final MealIntakeLogRepository logRepository;
    private final RecipeCatalogQueryRepository recipeRepository;

    public MealIntakeLogService(MealIntakeLogRepository logRepository, RecipeCatalogQueryRepository recipeRepository) {
        this.logRepository = logRepository;
        this.recipeRepository = recipeRepository;
    }

    /**
     * Log a meal from a meal plan.
     * 
     * @param userId         User consuming the meal
     * @param mealPlanMealId Reference to the planned meal
     * @param servings       Number of servings (e.g., 1.0, 0.5 for half portion)
     * @param consumedTime   Time of consumption (optional)
     * @param now            Current time for timestamps
     * @return Logged meal with calculated nutrition
     */
    @Transactional
    public MealIntakeLogEntity logMealFromPlan(UUID userId, UUID mealPlanMealId, BigDecimal servings,
            LocalTime consumedTime, OffsetDateTime now) {

        // For now, we'll create a basic log without looking up the meal plan meal
        // In a real system, you'd fetch the meal plan meal to get meal_type and
        // recipe_id

        var log = new MealIntakeLogEntity(UUID.randomUUID(), userId, mealPlanMealId, null, // Will be loaded from meal
                                                                                           // plan meal in future
                LocalDate.now(ZoneId.of("UTC")), consumedTime, "MEAL", // Default meal type
                servings != null ? servings : BigDecimal.ONE, null, // Nutrition will be loaded from recipe
                null, null, null, null, null, null, now, now);

        logger.debug("Logging meal from plan: userId={}, mealPlanMealId={}, servings={}", userId, mealPlanMealId,
                servings);

        return logRepository.save(log);
    }

    /**
     * Log a meal from a recipe.
     * 
     * @param userId       User consuming the meal
     * @param recipeId     Recipe being consumed
     * @param servings     Number of servings
     * @param mealType     Type of meal (breakfast, lunch, dinner, snack)
     * @param consumedTime Time of consumption (optional)
     * @param now          Current time for timestamps
     * @return Logged meal with nutrition snapshot
     */
    @Transactional
    public MealIntakeLogEntity logMealFromRecipe(UUID userId, UUID recipeId, BigDecimal servings, String mealType,
            LocalTime consumedTime, OffsetDateTime now) {

        var recipe = recipeRepository.getById(recipeId);
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe not found: " + recipeId);
        }

        // Calculate nutrition for this serving
        BigDecimal serving = servings != null ? servings : BigDecimal.ONE;
        BigDecimal multiplier = serving;

        RecipeCatalogModels.RecipeNutritionSummary nutrition = recipe.nutrition();
        BigDecimal calories = nutrition.caloriesKcal() != null ? nutrition.caloriesKcal().multiply(multiplier) : null;
        BigDecimal protein = nutrition.proteinG() != null ? nutrition.proteinG().multiply(multiplier) : null;
        BigDecimal carbs = nutrition.carbsG() != null ? nutrition.carbsG().multiply(multiplier) : null;
        BigDecimal fats = nutrition.fatsG() != null ? nutrition.fatsG().multiply(multiplier) : null;

        var log = new MealIntakeLogEntity(UUID.randomUUID(), userId, null, // No meal plan association
                recipeId, LocalDate.now(ZoneId.of("UTC")), consumedTime, mealType != null ? mealType : recipe.meal(),
                serving, calories, protein, carbs, fats, null, null, null, now, now);

        logger.debug("Logging meal from recipe: userId={}, recipeId={}, servings={}", userId, recipeId, servings);

        return logRepository.save(log);
    }

    /**
     * Log a custom meal (not from recipe).
     * 
     * @param userId       User consuming the meal
     * @param mealName     Name of the meal
     * @param mealType     Type of meal (breakfast, lunch, dinner, snack)
     * @param calories     Calories in the meal
     * @param protein      Protein in grams
     * @param carbs        Carbs in grams
     * @param fats         Fats in grams
     * @param notes        Optional notes
     * @param consumedTime Time of consumption (optional)
     * @param now          Current time for timestamps
     * @return Logged custom meal
     */
    @Transactional
    public MealIntakeLogEntity logCustomMeal(UUID userId, String mealName, String mealType, BigDecimal calories,
            BigDecimal protein, BigDecimal carbs, BigDecimal fats, String notes, LocalTime consumedTime,
            OffsetDateTime now) {

        var log = new MealIntakeLogEntity(UUID.randomUUID(), userId, null, // No meal plan association
                null, // No recipe
                LocalDate.now(ZoneId.of("UTC")), consumedTime, mealType, BigDecimal.ONE, // Custom meals are 1 serving
                calories, protein, carbs, fats, mealName, notes, null, now, now);

        logger.debug("Logging custom meal: userId={}, mealName={}", userId, mealName);

        return logRepository.save(log);
    }

    /**
     * Get all meals logged for a user on a specific date.
     */
    @Transactional(readOnly = true)
    public List<MealIntakeLogEntity> getMealsForDay(UUID userId, LocalDate date) {
        return logRepository.findByUserIdAndConsumedDate(userId, date);
    }

    /**
     * Get all meals logged for a user within a date range.
     */
    @Transactional(readOnly = true)
    public List<MealIntakeLogEntity> getMealsForDateRange(UUID userId, LocalDate startDate, LocalDate endDate) {
        return logRepository.findByUserIdAndDateRange(userId, startDate, endDate);
    }

    /**
     * Soft delete a meal log.
     */
    @Transactional
    public void deleteMealLog(UUID mealLogId, OffsetDateTime now) {
        var log = logRepository.findById(mealLogId)
                .orElseThrow(() -> new IllegalArgumentException("Meal log not found: " + mealLogId));

        var deletedLog = new MealIntakeLogEntity(log.getId(), log.getUserId(), log.getMealPlanMealId(),
                log.getRecipeId(), log.getConsumedDate(), log.getConsumedTime(), log.getMealType(), log.getServings(),
                log.getCaloriesKcal(), log.getProteinG(), log.getCarbsG(), log.getFatsG(), log.getCustomMealName(),
                log.getCustomMealNotes(), now, // Set deleted_at
                log.getCreatedAt(), now // Update updated_at
        );

        logRepository.save(deletedLog);
        logger.debug("Soft deleted meal log: id={}", mealLogId);
    }

    /**
     * Get a specific meal log by ID (excludes soft-deleted).
     */
    @Transactional(readOnly = true)
    public Optional<MealIntakeLogEntity> getMealLog(UUID id) {
        return logRepository.findByIdNotDeleted(id);
    }

    /**
     * Check if a meal plan meal has been logged/consumed.
     */
    @Transactional(readOnly = true)
    public boolean hasMealBeenLogged(UUID mealPlanMealId) {
        return logRepository.existsForMealPlanMeal(mealPlanMealId);
    }

    /**
     * Count how many meals were logged for a user on a specific date.
     */
    @Transactional(readOnly = true)
    public long countMealsForDay(UUID userId, LocalDate date) {
        return logRepository.countByUserIdAndConsumedDate(userId, date);
    }
}
