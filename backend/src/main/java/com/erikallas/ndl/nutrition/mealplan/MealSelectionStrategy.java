package com.erikallas.ndl.nutrition.mealplan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MealSelectionStrategy {

    private final MealPlanRecipeSelectorRepository selectorRepository;

    public MealSelectionStrategy(MealPlanRecipeSelectorRepository selectorRepository) {
        this.selectorRepository = selectorRepository;
    }

    public MealPlanRecipeSelectorRepository.RecipeCandidate chooseRecipe(
            String slotType,
            List<String> preferredCuisines,
            List<String> dietaryTags,
            List<String> allergyCodes,
            List<String> dislikedIngredients,
            Set<UUID> usedRecipeIds) {
        List<String> blockedTerms = buildBlockedTerms(allergyCodes, dislikedIngredients);

        // Progressive relaxation keeps plans populated while still preferring user constraints.
        List<SelectionAttempt> attempts = List.of(
                new SelectionAttempt(slotType, preferredCuisines, dietaryTags, blockedTerms),
                new SelectionAttempt(slotType, List.of(), dietaryTags, blockedTerms),
                new SelectionAttempt(slotType, List.of(), List.of(), blockedTerms),
                new SelectionAttempt(slotType, List.of(), List.of(), List.of()),
                new SelectionAttempt(null, List.of(), List.of(), List.of()));

        for (SelectionAttempt attempt : attempts) {
            List<MealPlanRecipeSelectorRepository.RecipeCandidate> candidates = selectorRepository.findCandidates(
                    attempt.mealType(),
                    attempt.preferredCuisines(),
                    attempt.dietaryTags(),
                    attempt.blockedTerms(),
                    usedRecipeIds,
                    50);
            if (!candidates.isEmpty()) {
                return candidates.get(0);
            }
        }
        return null;
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

    private record SelectionAttempt(
            String mealType,
            List<String> preferredCuisines,
            List<String> dietaryTags,
            List<String> blockedTerms) {
    }
}
