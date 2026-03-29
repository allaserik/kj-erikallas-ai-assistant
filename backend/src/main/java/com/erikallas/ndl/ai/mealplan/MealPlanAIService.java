package com.erikallas.ndl.ai.mealplan;

import org.springframework.stereotype.Service;
import com.erikallas.ndl.ai.sequential.SequentialPromptingModels;
import com.erikallas.ndl.nutrition.mealplan.MealPlanEntity;
import com.erikallas.ndl.nutrition.mealplan.MealPlanMealEntity;
import com.erikallas.ndl.nutrition.mealplan.MealPlanRepository;
import com.erikallas.ndl.nutrition.mealplan.MealPlanMealRepository;
import com.erikallas.ndl.nutrition.mealplan.MealPlanModels;
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
 * Converts sequential prompting output to saved database entities.
 * Bridges Phase 3 (sequential prompting) to persistent storage.
 */
@Service
public class MealPlanAIService {
    private static final Logger log = LoggerFactory.getLogger(MealPlanAIService.class);

    private final MealPlanRepository mealPlanRepository;
    private final MealPlanMealRepository mealPlanMealRepository;

    public MealPlanAIService(
            MealPlanRepository mealPlanRepository,
            MealPlanMealRepository mealPlanMealRepository) {
        this.mealPlanRepository = mealPlanRepository;
        this.mealPlanMealRepository = mealPlanMealRepository;
    }

    /**
     * Save generated meal plan from sequential prompting to database.
     * 
     * @param userId User who owns this meal plan
     * @param response Generated meal plan from SequentialPromptingService
     * @return Saved meal plan entity with generated IDs
     */
    public MealPlanEntity saveMealPlan(UUID userId, SequentialPromptingModels.MealPlanResponse response) {
        try {
            log.info("Saving AI-generated meal plan for user: {}, {} days", userId, response.numberOfDays());

            // Step 1: Create and save MealPlanEntity
            var mealPlan = createMealPlanEntity(userId, response);
            mealPlan = mealPlanRepository.save(mealPlan);
            log.info("Saved meal plan with ID: {}", mealPlan.getId());

            // Step 2: Create and save individual MealPlanMealEntity for each day/meal
            var mealEntities = new ArrayList<MealPlanMealEntity>();
            int positionIndex = 0;

            for (var dayNutrition : response.days()) {
                for (var meal : dayNutrition.meals()) {
                    // Parse day number from "Day N" label
                    LocalDate mealDate = extractDateFromDay(dayNutrition.dayLabel(), response);
                    
                    var mealEntity = new MealPlanMealEntity(
                            UUID.randomUUID(),
                            mealPlan.getId(),
                            mealDate,
                            meal.mealType(),
                            meal.recipeId(),
                            positionIndex++,
                            OffsetDateTime.now(),
                            OffsetDateTime.now()
                    );
                    mealEntities.add(mealEntity);
                }
            }

            // Batch save all meals
            mealPlanMealRepository.saveAll(mealEntities);
            log.info("Saved {} meal plan meals", mealEntities.size());

            return mealPlan;
        } catch (Exception e) {
            log.error("Error saving AI-generated meal plan for user: {}", userId, e);
            throw new RuntimeException("Failed to save meal plan: " + e.getMessage());
        }
    }

    /**
     * Convert sequential prompting response to MealPlanEntity.
     */
    private MealPlanEntity createMealPlanEntity(
            UUID userId,
            SequentialPromptingModels.MealPlanResponse response) {
        
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(response.numberOfDays() - 1);

        return new MealPlanEntity(
                UUID.randomUUID(),                          // id
                userId,                                      // userId
                response.numberOfDays() > 7 ? "weekly" : "daily",  // periodType
                startDate,                                   // startDate
                endDate,                                     // endDate
                "GENERATED",                                 // status
                "AI_GENERATED",                              // source
                now,                                         // createdAt
                now                                          // updatedAt
        );
    }

    /**
     * Extract date from day label (e.g., "Day 1" -> LocalDate.now()).
     */
    private LocalDate extractDateFromDay(
            String dayLabel,
            SequentialPromptingModels.MealPlanResponse response) {
        
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
     * Build response model for saved meal plan (used by controller).
     */
    public MealPlanModels.GenerateResponse buildGenerateResponse(
            MealPlanEntity savedPlan,
            SequentialPromptingModels.MealPlanResponse nutritionPlan) {
        
        // Group meals by day
        Map<LocalDate, List<MealPlanModels.MealItemResponse>> mealsByDay = new TreeMap<>();
        
        // Reconstruct meals from original response
        for (var day : nutritionPlan.days()) {
            List<MealPlanModels.MealItemResponse> dayMeals = new ArrayList<>();
            
            for (var meal : day.meals()) {
                // Calculate estimated time (30 min base + 5 min per serving)
                int estimatedTime = 30 + (meal.servings() * 5);
                
                var mealItem = new MealPlanModels.MealItemResponse(
                        UUID.randomUUID(),  // mealId (would be from saved entity in real scenario)
                        meal.mealType(),    // mealType
                        meal.recipeId(),    // recipeId
                        meal.recipeTitle(), // recipeTitle
                        "unspecified",      // cuisine (not available in PlannedMeal)
                        estimatedTime,      // timeMinutes
                        BigDecimal.valueOf(meal.caloriesKcal()),  // caloriesKcal
                        BigDecimal.valueOf(meal.proteinG()),      // proteinG
                        BigDecimal.valueOf(meal.carbsG()),        // carbsG
                        BigDecimal.valueOf(meal.fatsG())          // fatsG
                );
                dayMeals.add(mealItem);
            }
            
            mealsByDay.put(LocalDate.now().plusDays(day.dayLabel().length() - 1), dayMeals);
        }
        
        // Build day responses
        List<MealPlanModels.MealPlanDayResponse> dayResponses = mealsByDay.entrySet().stream()
            .map(entry -> new MealPlanModels.MealPlanDayResponse(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
        
        return new MealPlanModels.GenerateResponse(
                savedPlan.getId(),
                savedPlan.getPeriodType(),
                savedPlan.getStartDate(),
                savedPlan.getEndDate(),
                dayResponses.stream()
                    .mapToInt(d -> d.meals().size())
                    .sum(),
                dayResponses
        );
    }
}
