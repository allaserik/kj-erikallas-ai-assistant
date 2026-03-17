package com.erikallas.ndl.nutrition.mealplan;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing meal plan modifications: swapping and reordering meals.
 * 
 * Supports: - Reordering meals within a single day (change priority/sequence) -
 * Swapping meals between different days - Preserving meal plan integrity and
 * nutrition targets
 */
@Service
public class MealPlanManagementService {

    private static final Logger logger = LoggerFactory.getLogger(MealPlanManagementService.class);

    private final MealPlanMealRepository mealPlanMealRepository;

    public MealPlanManagementService(MealPlanMealRepository mealPlanMealRepository) {
        this.mealPlanMealRepository = mealPlanMealRepository;
    }

    /**
     * Reorder meals within the same day.
     * 
     * Takes a list of meal IDs in the desired order and updates their
     * positionIndex. All meals must be on the same day.
     * 
     * @param mealIds List of meal IDs in desired order
     * @param now     Current timestamp for updating updated_at
     * @throws IllegalArgumentException if meals are not on the same day
     */
    @Transactional
    public void reorderMealsInDay(List<UUID> mealIds, OffsetDateTime now) {
        if (mealIds == null || mealIds.isEmpty()) {
            throw new IllegalArgumentException("Meal IDs list cannot be empty");
        }

        // Load all meals
        List<MealPlanMealEntity> meals = new ArrayList<>();
        for (UUID mealId : mealIds) {
            var meal = mealPlanMealRepository.findById(mealId)
                    .orElseThrow(() -> new IllegalArgumentException("Meal not found: " + mealId));
            meals.add(meal);
        }

        // Verify all meals are on the same day
        var firstDay = meals.get(0).getDayDate();
        for (var meal : meals) {
            if (!meal.getDayDate().equals(firstDay)) {
                throw new IllegalArgumentException(
                        "Cannot reorder meals from different days. All meals must be on the same day.");
            }
        }

        // Update position index for each meal
        for (int i = 0; i < meals.size(); i++) {
            var meal = meals.get(i);
            var updatedMeal = new MealPlanMealEntity(meal.getId(), meal.getMealPlanId(), meal.getDayDate(),
                    meal.getMealType(), meal.getRecipeId(), i, // New position
                    meal.getCreatedAt(), now);
            mealPlanMealRepository.save(updatedMeal);
        }

        logger.info("Reordered {} meals on date {}", mealIds.size(), firstDay);
    }

    /**
     * Swap two meals, potentially across different days.
     * 
     * This supports: - Swapping meals on the same day (different types) - Swapping
     * meals on different days
     * 
     * After swapping, meals retain their position index but exchange dates.
     * 
     * @param mealId1 First meal ID
     * @param mealId2 Second meal ID
     * @param now     Current timestamp
     */
    @Transactional
    public void swapMeals(UUID mealId1, UUID mealId2, OffsetDateTime now) {
        var meal1 = mealPlanMealRepository.findById(mealId1)
                .orElseThrow(() -> new IllegalArgumentException("Meal not found: " + mealId1));
        var meal2 = mealPlanMealRepository.findById(mealId2)
                .orElseThrow(() -> new IllegalArgumentException("Meal not found: " + mealId2));

        // Verify both meals are in the same meal plan
        if (!meal1.getMealPlanId().equals(meal2.getMealPlanId())) {
            throw new IllegalArgumentException("Cannot swap meals from different meal plans");
        }

        // Swap dates and meal types
        var swappedMeal1 = new MealPlanMealEntity(meal1.getId(), meal1.getMealPlanId(), meal2.getDayDate(), // Swap!
                                                                                                            // Meal1
                                                                                                            // gets
                                                                                                            // meal2's
                                                                                                            // date
                meal2.getMealType(), // Swap! Meal1 gets meal2's type
                meal1.getRecipeId(), meal1.getPositionIndex(), meal1.getCreatedAt(), now);

        var swappedMeal2 = new MealPlanMealEntity(meal2.getId(), meal2.getMealPlanId(), meal1.getDayDate(), // Swap!
                                                                                                            // Meal2
                                                                                                            // gets
                                                                                                            // meal1's
                                                                                                            // date
                meal1.getMealType(), // Swap! Meal2 gets meal1's type
                meal2.getRecipeId(), meal2.getPositionIndex(), meal2.getCreatedAt(), now);

        mealPlanMealRepository.save(swappedMeal1);
        mealPlanMealRepository.save(swappedMeal2);

        logger.info("Swapped meals {} and {}: {} <-> {}", mealId1, mealId2, meal1.getDayDate(), meal2.getDayDate());
    }

    /**
     * Replace a single meal with a different recipe.
     * 
     * Useful for changing what's being eaten on a specific day without reordering.
     * 
     * @param mealId      Meal to update
     * @param newRecipeId New recipe to assign
     * @param now         Current timestamp
     */
    @Transactional
    public void replaceMeal(UUID mealId, UUID newRecipeId, OffsetDateTime now) {
        var meal = mealPlanMealRepository.findById(mealId)
                .orElseThrow(() -> new IllegalArgumentException("Meal not found: " + mealId));

        var updatedMeal = new MealPlanMealEntity(meal.getId(), meal.getMealPlanId(), meal.getDayDate(),
                meal.getMealType(), newRecipeId, // New recipe
                meal.getPositionIndex(), meal.getCreatedAt(), now);

        mealPlanMealRepository.save(updatedMeal);
        logger.info("Replaced meal {} with recipe {}", mealId, newRecipeId);
    }
}
