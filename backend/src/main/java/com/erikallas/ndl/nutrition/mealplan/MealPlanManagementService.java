package com.erikallas.ndl.nutrition.mealplan;

import com.erikallas.ndl.ai.rag.RAGRetrieverService;
import com.erikallas.ndl.nutrition.preferences.NutritionPreferencesService;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogModels;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final MealPlanRepository mealPlanRepository;
    private final MealPlanVersionRepository mealPlanVersionRepository;
    private final RAGRetrieverService ragRetrieverService;
    private final NutritionPreferencesService nutritionPreferencesService;
    private final ObjectMapper objectMapper;

    public MealPlanManagementService(MealPlanMealRepository mealPlanMealRepository,
            MealPlanRepository mealPlanRepository, MealPlanVersionRepository mealPlanVersionRepository,
            RAGRetrieverService ragRetrieverService, NutritionPreferencesService nutritionPreferencesService,
            ObjectMapper objectMapper) {
        this.mealPlanMealRepository = mealPlanMealRepository;
        this.mealPlanRepository = mealPlanRepository;
        this.mealPlanVersionRepository = mealPlanVersionRepository;
        this.ragRetrieverService = ragRetrieverService;
        this.nutritionPreferencesService = nutritionPreferencesService;
        this.objectMapper = objectMapper;
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

        // Snapshot before mutation so the user can undo
        snapshotMealPlan(meals.get(0).getMealPlanId(), now);

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

        // Snapshot before mutation
        snapshotMealPlan(meal1.getMealPlanId(), now);

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

        // Snapshot before mutation
        snapshotMealPlan(meal.getMealPlanId(), now);

        var updatedMeal = new MealPlanMealEntity(meal.getId(), meal.getMealPlanId(), meal.getDayDate(),
                meal.getMealType(), newRecipeId, // New recipe
                meal.getPositionIndex(), meal.getCreatedAt(), now);

        mealPlanMealRepository.save(updatedMeal);
        logger.info("Replaced meal {} with recipe {}", mealId, newRecipeId);
    }

    /**
     * Delete a meal from a meal plan.
     * 
     * After deletion, position indices of remaining meals on that day are adjusted.
     * 
     * @param mealId Meal to delete
     * @param now    Current timestamp
     */
    @Transactional
    public void deleteMeal(UUID mealId, OffsetDateTime now) {
        var meal = mealPlanMealRepository.findById(mealId)
                .orElseThrow(() -> new IllegalArgumentException("Meal not found: " + mealId));

        var mealPlanId = meal.getMealPlanId();
        var dayDate = meal.getDayDate();
        var deletedPosition = meal.getPositionIndex();

        // Snapshot before mutation
        snapshotMealPlan(mealPlanId, now);

        // Delete the meal
        mealPlanMealRepository.deleteById(mealId);

        // Adjust position indices for remaining meals on the same day
        var remainingMeals = mealPlanMealRepository.findByMealPlanIdAndDayDate(mealPlanId, dayDate);
        for (var remainingMeal : remainingMeals) {
            if (remainingMeal.getPositionIndex() > deletedPosition) {
                // Shift positions down
                var updatedMeal = new MealPlanMealEntity(remainingMeal.getId(), remainingMeal.getMealPlanId(),
                        remainingMeal.getDayDate(), remainingMeal.getMealType(), remainingMeal.getRecipeId(),
                        remainingMeal.getPositionIndex() - 1, remainingMeal.getCreatedAt(), now);
                mealPlanMealRepository.save(updatedMeal);
            }
        }

        logger.info("Deleted meal {} from day {}", mealId, dayDate);
    }

    /**
     * Add a new meal to a meal plan for a specific day.
     * 
     * The meal is appended as the last meal of the day (highest position index).
     * 
     * @param mealPlanId Which meal plan to add to
     * @param dayDate    Which day to add the meal
     * @param mealType   Type of meal (breakfast, lunch, dinner, snack)
     * @param recipeId   Recipe to use
     * @param now        Current timestamp
     * @return The created meal ID
     */
    @Transactional
    public UUID addMealToDay(UUID mealPlanId, java.time.LocalDate dayDate, String mealType, UUID recipeId,
            OffsetDateTime now) {
        if (mealType == null || mealType.isBlank()) {
            throw new IllegalArgumentException("Meal type cannot be empty");
        }

        // Snapshot before mutation
        snapshotMealPlan(mealPlanId, now);

        // Get existing meals for this day to find max position
        var existingMeals = mealPlanMealRepository.findByMealPlanIdAndDayDate(mealPlanId, dayDate);
        int nextPosition = existingMeals.stream().mapToInt(MealPlanMealEntity::getPositionIndex).max().orElse(-1) + 1;

        // Create new meal entity
        var newMeal = new MealPlanMealEntity(UUID.randomUUID(), mealPlanId, dayDate, mealType, recipeId, nextPosition,
                now, now);

        mealPlanMealRepository.save(newMeal);
        logger.info("Added meal to plan {} on day {} at position {}", mealPlanId, dayDate, nextPosition);

        return newMeal.getId();
    }

    // ===== Regenerate individual meal =====

    /**
     * Regenerate a single meal slot by using RAG to find a better recipe. The
     * current recipe is excluded so the user always gets something new.
     *
     * @param mealId Meal to regenerate
     * @param userId Owner of the meal plan (used to look up preferences)
     * @param now    Current timestamp
     */
    @Transactional
    public void regenerateMeal(UUID mealId, UUID userId, OffsetDateTime now) {
        var meal = mealPlanMealRepository.findById(mealId)
                .orElseThrow(() -> new IllegalArgumentException("Meal not found: " + mealId));

        // Snapshot current state before mutation
        snapshotMealPlan(meal.getMealPlanId(), now);

        String mealType = meal.getMealType();
        var prefs = nutritionPreferencesService.getOrCreateWithPrefill(userId);

        // Build a richer semantic query using user preferences
        StringBuilder query = new StringBuilder(mealType);
        if (prefs.cuisinePreferences() != null && !prefs.cuisinePreferences().isEmpty()) {
            query.append(" ").append(String.join(" ", prefs.cuisinePreferences()));
        }
        if (prefs.dietaryPreferenceCodes() != null && !prefs.dietaryPreferenceCodes().isEmpty()) {
            query.append(" ").append(String.join(" ", prefs.dietaryPreferenceCodes()));
        }

        List<RecipeCatalogModels.RecipeDetail> candidates = ragRetrieverService.retrieveRecipesByQuery(query.toString(),
                mealType, 10);

        // Pick the first candidate that isn't the current recipe
        UUID currentRecipeId = meal.getRecipeId();
        RecipeCatalogModels.RecipeDetail chosen = candidates.stream().filter(r -> !r.id().equals(currentRecipeId))
                .findFirst().orElse(null);

        if (chosen == null) {
            throw new IllegalStateException("No alternative recipes found for meal type: " + mealType);
        }

        var updated = new MealPlanMealEntity(meal.getId(), meal.getMealPlanId(), meal.getDayDate(), meal.getMealType(),
                chosen.id(), meal.getPositionIndex(), meal.getCreatedAt(), now);
        mealPlanMealRepository.save(updated);

        logger.info("Regenerated meal {} from recipe {} to recipe {}", mealId, currentRecipeId, chosen.id());
    }

    // ===== Get alternatives =====

    /**
     * Return up to three alternative recipes for a meal slot (excluding the
     * currently assigned recipe).
     *
     * @param mealId Meal whose alternatives are requested
     * @param userId Owner of the meal plan (used to look up preferences)
     * @return Ordered list of alternative recipe summaries (up to 3)
     */
    @Transactional(readOnly = true)
    public List<RecipeCatalogModels.RecipeListItem> getAlternatives(UUID mealId, UUID userId) {
        var meal = mealPlanMealRepository.findById(mealId)
                .orElseThrow(() -> new IllegalArgumentException("Meal not found: " + mealId));

        String mealType = meal.getMealType();
        var prefs = nutritionPreferencesService.getOrCreateWithPrefill(userId);

        StringBuilder query = new StringBuilder(mealType);
        if (prefs.cuisinePreferences() != null && !prefs.cuisinePreferences().isEmpty()) {
            query.append(" ").append(String.join(" ", prefs.cuisinePreferences()));
        }
        if (prefs.dietaryPreferenceCodes() != null && !prefs.dietaryPreferenceCodes().isEmpty()) {
            query.append(" ").append(String.join(" ", prefs.dietaryPreferenceCodes()));
        }

        List<RecipeCatalogModels.RecipeDetail> candidates = ragRetrieverService.retrieveRecipesByQuery(query.toString(),
                mealType, 10);

        UUID currentRecipeId = meal.getRecipeId();
        return candidates.stream().filter(r -> !r.id().equals(currentRecipeId)).limit(3)
                .map(r -> new RecipeCatalogModels.RecipeListItem(r.id(), r.title(), r.cuisine(), r.meal(), r.servings(),
                        r.timeMinutes(), r.difficultyLevel(), r.dietaryTags(), r.nutrition()))
                .collect(java.util.stream.Collectors.toList());
    }

    // ===== Versioning helpers =====

    /**
     * Snapshot the current state of a meal plan's meals. Called automatically
     * before any mutating operation so the user can always restore to an earlier
     * version.
     */
    @Transactional
    public void snapshotMealPlan(UUID mealPlanId, OffsetDateTime now) {
        List<MealPlanMealEntity> meals = mealPlanMealRepository.findByMealPlanId(mealPlanId);

        List<Map<String, Object>> snapshotRows = new ArrayList<>();
        for (var meal : meals) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", meal.getId().toString());
            row.put("day_date", meal.getDayDate().toString());
            row.put("meal_type", meal.getMealType());
            row.put("recipe_id", meal.getRecipeId() != null ? meal.getRecipeId().toString() : null);
            row.put("position_index", meal.getPositionIndex());
            snapshotRows.add(row);
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(snapshotRows);
        } catch (Exception e) {
            logger.warn("Could not serialise meal plan snapshot for plan {}: {}", mealPlanId, e.getMessage());
            return;
        }

        int nextVersion = mealPlanVersionRepository.findMaxVersionNumber(mealPlanId) + 1;
        var version = new MealPlanVersionEntity(UUID.randomUUID(), mealPlanId, nextVersion, payloadJson,
                now != null ? now : OffsetDateTime.now(ZoneOffset.UTC));
        mealPlanVersionRepository.save(version);

        logger.info("Saved version {} for meal plan {}", nextVersion, mealPlanId);
    }

    /**
     * List all versions for a meal plan, latest first.
     */
    @Transactional(readOnly = true)
    public List<MealPlanVersionEntity> listVersions(UUID mealPlanId) {
        return mealPlanVersionRepository.findByMealPlanIdOrderByVersionNumberDesc(mealPlanId);
    }

    /**
     * Restore a meal plan to a previously saved version. Current meals are deleted;
     * meals from the snapshot are recreated.
     *
     * @param mealPlanId Plan to restore
     * @param versionId  Version whose snapshot to restore from
     * @param now        Timestamp for recreated entities
     */
    @Transactional
    public void restoreVersion(UUID mealPlanId, UUID versionId, OffsetDateTime now) {
        // Validate plan exists
        mealPlanRepository.findById(mealPlanId)
                .orElseThrow(() -> new IllegalArgumentException("Meal plan not found: " + mealPlanId));

        var version = mealPlanVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));

        if (!version.getMealPlanId().equals(mealPlanId)) {
            throw new IllegalArgumentException("Version does not belong to meal plan: " + mealPlanId);
        }

        // Snapshot current state before overwriting
        snapshotMealPlan(mealPlanId, now);

        // Delete current meals (logs get meal_plan_meal_id = NULL via ON DELETE SET
        // NULL)
        mealPlanMealRepository.deleteByMealPlanId(mealPlanId);

        // Recreate from snapshot
        try {
            com.fasterxml.jackson.databind.JsonNode rows = objectMapper.readTree(version.getPayloadJson());
            List<MealPlanMealEntity> restored = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode row : rows) {
                UUID recipeId = row.path("recipe_id").isNull() ? null : UUID.fromString(row.path("recipe_id").asText());
                var meal = new MealPlanMealEntity(UUID.randomUUID(), // fresh ID — old logs nulled, not relinked
                        mealPlanId, java.time.LocalDate.parse(row.path("day_date").asText()),
                        row.path("meal_type").asText(), recipeId, row.path("position_index").asInt(), now, now);
                restored.add(meal);
            }
            mealPlanMealRepository.saveAll(restored);
            logger.info("Restored meal plan {} to version {} ({} meals)", mealPlanId, version.getVersionNumber(),
                    restored.size());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to restore meal plan from version: " + e.getMessage(), e);
        }
    }
}
