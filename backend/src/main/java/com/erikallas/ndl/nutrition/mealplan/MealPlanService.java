package com.erikallas.ndl.nutrition.mealplan;

import com.erikallas.ndl.nutrition.preferences.NutritionPreferencesService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealPlanService {

    public record GenerateCommand(String duration, LocalDate startDate) {
    }

    public record MealItemResponse(
            UUID id,
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

    public record MealPlanDayResponse(LocalDate date, List<MealItemResponse> meals) {
    }

    public record GenerateResponse(
            UUID mealPlanId,
            String periodType,
            LocalDate startDate,
            LocalDate endDate,
            int totalMeals,
            List<MealPlanDayResponse> days) {
    }

    private final MealPlanRepository mealPlanRepository;
    private final MealPlanMealRepository mealPlanMealRepository;
    private final MealPlanRecipeSelectorRepository selectorRepository;
    private final NutritionPreferencesService nutritionPreferencesService;

    public MealPlanService(
            MealPlanRepository mealPlanRepository,
            MealPlanMealRepository mealPlanMealRepository,
            MealPlanRecipeSelectorRepository selectorRepository,
            NutritionPreferencesService nutritionPreferencesService) {
        this.mealPlanRepository = mealPlanRepository;
        this.mealPlanMealRepository = mealPlanMealRepository;
        this.selectorRepository = selectorRepository;
        this.nutritionPreferencesService = nutritionPreferencesService;
    }

    @Transactional
    public GenerateResponse generate(UUID userId, GenerateCommand command) {
        String period = normalizePeriod(command.duration());
        LocalDate startDate = command.startDate() != null ? command.startDate() : LocalDate.now();
        LocalDate endDate = period.equals("weekly") ? startDate.plusDays(6) : startDate;

        var prefs = nutritionPreferencesService.getOrCreateWithPrefill(userId);
        List<String> slotTypes = buildMealSlots(prefs.mealsPerDay(), prefs.snacksPerDay());
        List<String> blockedTerms = buildBlockedTerms(prefs.allergyCodes(), prefs.dislikedIngredients());

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

        Set<UUID> usedRecipeIds = new HashSet<>();
        List<MealPlanMealEntity> mealRows = new ArrayList<>();
        List<MealPlanDayResponse> dayResponses = new ArrayList<>();

        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            List<MealItemResponse> meals = new ArrayList<>();
            for (int i = 0; i < slotTypes.size(); i++) {
                String slot = slotTypes.get(i);
                var candidate = chooseRecipe(
                        slot,
                        prefs.cuisinePreferences(),
                        prefs.dietaryPreferenceCodes(),
                        blockedTerms,
                        usedRecipeIds);

                if (candidate == null) {
                    continue;
                }

                usedRecipeIds.add(candidate.id());
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

                meals.add(new MealItemResponse(
                        row.getId(),
                        slot,
                        candidate.id(),
                        candidate.title(),
                        candidate.cuisine(),
                        candidate.timeMinutes(),
                        candidate.caloriesKcal(),
                        candidate.proteinG(),
                        candidate.carbsG(),
                        candidate.fatsG()));
            }
            dayResponses.add(new MealPlanDayResponse(day, meals));
        }

        mealPlanMealRepository.saveAll(mealRows);

        int totalMeals = dayResponses.stream().mapToInt(d -> d.meals().size()).sum();
        return new GenerateResponse(plan.getId(), period, startDate, endDate, totalMeals, dayResponses);
    }

    private MealPlanRecipeSelectorRepository.RecipeCandidate chooseRecipe(
            String slotType,
            List<String> preferredCuisines,
            List<String> dietaryTags,
            List<String> blockedTerms,
            Set<UUID> usedRecipeIds) {
        List<MealPlanRecipeSelectorRepository.RecipeCandidate> primary = selectorRepository.findCandidates(
                slotType,
                preferredCuisines,
                dietaryTags,
                blockedTerms,
                usedRecipeIds,
                50);
        if (!primary.isEmpty()) {
            return primary.get(0);
        }

        List<MealPlanRecipeSelectorRepository.RecipeCandidate> fallback = selectorRepository.findCandidates(
                null,
                preferredCuisines,
                dietaryTags,
                blockedTerms,
                usedRecipeIds,
                50);
        if (!fallback.isEmpty()) {
            return fallback.get(0);
        }
        return null;
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

    private List<String> buildBlockedTerms(List<String> allergyCodes, List<String> dislikedIngredients) {
        List<String> blocked = new ArrayList<>();
        if (dislikedIngredients != null) {
            blocked.addAll(dislikedIngredients);
        }
        Map<String, List<String>> allergyKeywords = Map.of(
                "peanut", List.of("peanut"),
                "tree_nut", List.of("almond", "cashew", "walnut", "nut"),
                "milk", List.of("milk", "cream", "cheese", "butter", "yogurt"),
                "egg", List.of("egg"),
                "soy", List.of("soy", "tofu"),
                "wheat", List.of("wheat", "flour", "gluten"),
                "fish", List.of("fish", "salmon", "tuna"),
                "shellfish", List.of("shrimp", "prawn", "crab", "lobster", "shellfish"),
                "sesame", List.of("sesame"),
                "lactose_intolerance", List.of("milk", "cream", "cheese", "lactose"));
        if (allergyCodes != null) {
            for (String code : allergyCodes) {
                blocked.addAll(allergyKeywords.getOrDefault(code, List.of()));
            }
        }
        return blocked.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .toList();
    }
}
