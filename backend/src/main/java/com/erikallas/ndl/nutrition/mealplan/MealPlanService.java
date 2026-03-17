package com.erikallas.ndl.nutrition.mealplan;

import com.erikallas.ndl.nutrition.preferences.NutritionPreferencesService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealPlanService {

    private final MealPlanRepository mealPlanRepository;
    private final MealPlanMealRepository mealPlanMealRepository;
    private final NutritionPreferencesService nutritionPreferencesService;
    private final MealSelectionStrategy mealSelectionStrategy;
    private final MealPlanQueryService mealPlanQueryService;

    public MealPlanService(
            MealPlanRepository mealPlanRepository,
            MealPlanMealRepository mealPlanMealRepository,
            NutritionPreferencesService nutritionPreferencesService,
            MealSelectionStrategy mealSelectionStrategy,
            MealPlanQueryService mealPlanQueryService) {
        this.mealPlanRepository = mealPlanRepository;
        this.mealPlanMealRepository = mealPlanMealRepository;
        this.nutritionPreferencesService = nutritionPreferencesService;
        this.mealSelectionStrategy = mealSelectionStrategy;
        this.mealPlanQueryService = mealPlanQueryService;
    }

    @Transactional
    public MealPlanModels.GenerateResponse generate(UUID userId, MealPlanModels.GenerateCommand command) {
        String period = normalizePeriod(command.duration());
        LocalDate startDate = command.startDate() != null ? command.startDate() : LocalDate.now();
        LocalDate endDate = period.equals("weekly") ? startDate.plusDays(6) : startDate;

        var prefs = nutritionPreferencesService.getOrCreateWithPrefill(userId);
        List<String> slotTypes = buildMealSlots(prefs.mealsPerDay(), prefs.snacksPerDay());

        OffsetDateTime now = OffsetDateTime.now();
        MealPlanEntity plan = new MealPlanEntity(
                UUID.randomUUID(),
                userId,
                period,
                startDate,
                endDate,
                "generated",
                "rule_based",
                now,
                now);
        mealPlanRepository.save(plan);

        List<MealPlanMealEntity> mealRows = new ArrayList<>();

        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            // Avoid duplicates within a single day, but allow repeats across different days.
            Set<UUID> usedRecipeIdsForDay = new HashSet<>();
            for (int i = 0; i < slotTypes.size(); i++) {
                String slot = slotTypes.get(i);
                var candidate = mealSelectionStrategy.chooseRecipe(
                        slot,
                        prefs.cuisinePreferences(),
                        prefs.dietaryPreferenceCodes(),
                        prefs.allergyCodes(),
                        prefs.dislikedIngredients(),
                        usedRecipeIdsForDay);

                if (candidate == null) {
                    continue;
                }

                usedRecipeIdsForDay.add(candidate.id());
                MealPlanMealEntity row = new MealPlanMealEntity(
                        UUID.randomUUID(),
                        plan.getId(),
                        day,
                        slot,
                        candidate.id(),
                        i,
                        now,
                        now);
                mealRows.add(row);

            }
        }

        mealPlanMealRepository.saveAll(mealRows);

        return getById(userId, plan.getId());
    }

    @Transactional(readOnly = true)
    public MealPlanModels.GenerateResponse getById(UUID userId, UUID mealPlanId) {
        return mealPlanQueryService.getById(userId, mealPlanId);
    }

    @Transactional(readOnly = true)
    public MealPlanModels.GenerateResponse getLatest(UUID userId) {
        return mealPlanQueryService.getLatest(userId);
    }

    private String normalizePeriod(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("daily".equals(normalized) || "weekly".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("duration must be 'daily' or 'weekly'");
    }

    private List<String> buildMealSlots(short mealsPerDay, short snacksPerDay) {
        List<String> slots = new ArrayList<>();
        if (mealsPerDay <= 1) {
            slots.add("dinner");
        } else if (mealsPerDay == 2) {
            slots.add("lunch");
            slots.add("dinner");
        } else {
            slots.add("breakfast");
            slots.add("lunch");
            slots.add("dinner");
            for (int i = 4; i <= mealsPerDay; i++) {
                slots.add("snack");
            }
        }
        for (int i = 0; i < snacksPerDay; i++) {
            slots.add("snack");
        }
        return slots;
    }

}
