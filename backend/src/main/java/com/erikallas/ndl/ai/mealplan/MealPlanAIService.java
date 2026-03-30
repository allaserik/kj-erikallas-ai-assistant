package com.erikallas.ndl.ai.mealplan;

import org.springframework.stereotype.Service;
import com.erikallas.ndl.ai.sequential.SequentialPromptingModels;
import com.erikallas.ndl.nutrition.mealplan.MealPlanEntity;
import com.erikallas.ndl.nutrition.mealplan.MealPlanMealEntity;
import com.erikallas.ndl.nutrition.mealplan.MealPlanRepository;
import com.erikallas.ndl.nutrition.mealplan.MealPlanMealRepository;
import com.erikallas.ndl.nutrition.mealplan.MealPlanModels;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogService;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Meal Plan Service - Phase 4 Persistence Layer
 * 
 * Converts sequential prompting output to saved database entities. Bridges
 * Phase 3 (sequential prompting) to persistent storage.
 */
@Service
public class MealPlanAIService {
    private static final Logger log = LoggerFactory.getLogger(MealPlanAIService.class);

    private final MealPlanRepository mealPlanRepository;
    private final MealPlanMealRepository mealPlanMealRepository;
    private final RecipeCatalogService recipeCatalogService;

    public MealPlanAIService(MealPlanRepository mealPlanRepository, MealPlanMealRepository mealPlanMealRepository,
            RecipeCatalogService recipeCatalogService) {
        this.mealPlanRepository = mealPlanRepository;
        this.mealPlanMealRepository = mealPlanMealRepository;
        this.recipeCatalogService = recipeCatalogService;
    }

    /**
     * Save generated meal plan from sequential prompting to database.
     * 
     * @param userId   User who owns this meal plan
     * @param response Generated meal plan from SequentialPromptingService
     * @return Saved meal plan entity with generated IDs
     */
    public MealPlanEntity saveMealPlan(UUID userId, SequentialPromptingModels.MealPlanResponse response) {
        try {
            log.info("Saving AI-generated meal plan for user: {}, {} days", userId, response.numberOfDays());

            // Debug: print plan structure
            int totalMealCount = response.days().stream().mapToInt(day -> day.meals().size()).sum();
            log.info("Meal plan summary: {} days, {} total meals", response.days().size(), totalMealCount);

            for (int i = 0; i < response.days().size(); i++) {
                var day = response.days().get(i);
                log.debug("  Day {}: {} meals ({} kcal)", i + 1, day.meals().size(),
                        Math.round(day.totalCaloriesKcal()));
            }

            // Step 1: Create and save MealPlanEntity
            var mealPlan = createMealPlanEntity(userId, response);
            mealPlan = mealPlanRepository.save(mealPlan);
            log.info("Saved meal plan with ID: {}, date range: {} to {}", mealPlan.getId(), mealPlan.getStartDate(),
                    mealPlan.getEndDate());

            // Step 2: Create and save individual MealPlanMealEntity for each day/meal
            var mealEntities = new ArrayList<MealPlanMealEntity>();
            int positionIndex = 0;

            for (var dayNutrition : response.days()) {
                var dayMealCount = dayNutrition.meals().size();
                log.debug("Processing day '{}': {} meals", dayNutrition.dayLabel(), dayMealCount);

                for (var meal : dayNutrition.meals()) {
                    // Parse day number from "Day N" label
                    LocalDate mealDate = extractDateFromDay(dayNutrition.dayLabel(), response);

                    var mealEntity = new MealPlanMealEntity(UUID.randomUUID(), mealPlan.getId(), mealDate,
                            meal.mealType(), meal.recipeId(), positionIndex++, OffsetDateTime.now(),
                            OffsetDateTime.now());
                    mealEntities.add(mealEntity);

                    log.trace("  Created meal entity: {} ({}) for {}", meal.recipeTitle(), meal.mealType(), mealDate);
                }
            }

            // Batch save all meals
            if (!mealEntities.isEmpty()) {
                mealPlanMealRepository.saveAll(mealEntities);
                log.info("Saved {} meal plan meals to database", mealEntities.size());
            } else {
                log.error("NO MEALS TO SAVE! The meal plan has 0 meals. This will result in empty daily views.");
            }

            return mealPlan;
        } catch (Exception e) {
            log.error("Error saving AI-generated meal plan for user: {}", userId, e);
            throw new RuntimeException("Failed to save meal plan: " + e.getMessage());
        }
    }

    /**
     * Convert sequential prompting response to MealPlanEntity.
     */
    private MealPlanEntity createMealPlanEntity(UUID userId, SequentialPromptingModels.MealPlanResponse response) {

        OffsetDateTime now = OffsetDateTime.now();
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(response.numberOfDays() - 1);

        return new MealPlanEntity(UUID.randomUUID(), // id
                userId, // userId
                response.numberOfDays() > 7 ? "weekly" : "daily", // periodType
                startDate, // startDate
                endDate, // endDate
                "GENERATED", // status
                "AI_GENERATED", // source
                now, // createdAt
                now // updatedAt
        );
    }

    /**
     * Extract date from day label (e.g., "Day 1" -> LocalDate.now()).
     */
    private LocalDate extractDateFromDay(String dayLabel, SequentialPromptingModels.MealPlanResponse response) {

        try {
            // Extract number from "Day N" format
            String[] parts = dayLabel.split(" ");
            if (parts.length >= 2) {
                int dayNumber = Integer.parseInt(parts[1]);
                return LocalDate.now().plusDays(dayNumber - 1);
            }
        } catch (Exception e) {
            log.warn("Could not parse day label: {}", dayLabel);
        }

        return LocalDate.now();
    }

    /**
     * Build response model for saved meal plan (used by controller). Fetches full
     * recipe details including ingredients and preparation steps.
     */
    public MealPlanModels.GenerateResponse buildGenerateResponse(MealPlanEntity savedPlan,
            SequentialPromptingModels.MealPlanResponse nutritionPlan) {

        // Group meals by day
        Map<LocalDate, List<MealPlanModels.MealItemResponse>> mealsByDay = new TreeMap<>();

        // Reconstruct meals from original response
        for (var day : nutritionPlan.days()) {
            List<MealPlanModels.MealItemResponse> dayMeals = new ArrayList<>();

            for (var meal : day.meals()) {
                try {
                    // Fetch full recipe details from catalog
                    RecipeCatalogModels.RecipeDetail recipeDetail = recipeCatalogService.getById(meal.recipeId());

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

                        var mealItem = new MealPlanModels.MealItemResponse(UUID.randomUUID(), // mealId
                                meal.mealType(), // mealType
                                meal.recipeId(), // recipeId
                                recipeDetail.title(), // recipeTitle
                                recipeDetail.cuisine(), // cuisine
                                recipeDetail.timeMinutes(), // timeMinutes
                                recipeDetail.difficultyLevel(), // difficultyLevel
                                recipeDetail.summary(), // summary
                                recipeDetail.source(), // source
                                recipeDetail.img(), // img
                                recipeDetail.nutrition().caloriesKcal(), // caloriesKcal
                                recipeDetail.nutrition().proteinG(), // proteinG
                                recipeDetail.nutrition().carbsG(), // carbsG
                                recipeDetail.nutrition().fatsG(), // fatsG
                                ingredients, // ingredients
                                preparation // preparation
                        );
                        dayMeals.add(mealItem);
                    }
                } catch (Exception e) {
                    log.warn("Could not fetch recipe details for meal {}: {}", meal.recipeId(), e.getMessage());
                }
            }

            mealsByDay.put(LocalDate.now().plusDays(day.dayLabel().length() - 1), dayMeals);
        }

        // Build day responses
        List<MealPlanModels.MealPlanDayResponse> dayResponses = mealsByDay.entrySet().stream()
                .map(entry -> new MealPlanModels.MealPlanDayResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        return new MealPlanModels.GenerateResponse(savedPlan.getId(), savedPlan.getPeriodType(),
                savedPlan.getStartDate(), savedPlan.getEndDate(),
                dayResponses.stream().mapToInt(d -> d.meals().size()).sum(), dayResponses);
    }
}
