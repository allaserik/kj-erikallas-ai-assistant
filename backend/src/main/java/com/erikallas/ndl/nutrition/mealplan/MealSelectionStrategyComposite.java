package com.erikallas.ndl.nutrition.mealplan;

import com.erikallas.ndl.nutrition.preferences.NutritionPreferencesService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Composite meal selection strategy that intelligently routes between AI and
 * deterministic approaches. - Uses AI when enabled and available - Falls back
 * to deterministic for reliability - Maintains consistent interface for
 * MealPlanService
 */
@Component
public class MealSelectionStrategyComposite {

    private static final Logger logger = LoggerFactory.getLogger(MealSelectionStrategyComposite.class);

    private final MealSelectionStrategy deterministicStrategy;
    private final AiMealSelectionStrategy aiStrategy;

    public MealSelectionStrategyComposite(MealSelectionStrategy deterministicStrategy,
            AiMealSelectionStrategy aiStrategy) {
        this.deterministicStrategy = deterministicStrategy;
        this.aiStrategy = aiStrategy;
    }

    /**
     * Choose recipe using AI or deterministic strategy. Attempts AI first if
     * configured and available, falls back to deterministic.
     */
    public MealPlanRecipeSelectorRepository.RecipeCandidate chooseRecipe(String slotType,
            List<String> preferredCuisines, List<String> dietaryTags, List<String> allergyCodes,
            List<String> dislikedIngredients, Set<UUID> usedRecipeIds,
            NutritionPreferencesService.PreferencesResponse userPrefs) {

        // Try AI first if available
        if (aiStrategy.isEnabled()) {
            try {
                logger.debug("Using AI meal selection for slot: {}", slotType);
                return aiStrategy.chooseRecipeForSlot(slotType, preferredCuisines, dietaryTags, allergyCodes,
                        dislikedIngredients, usedRecipeIds, userPrefs);
            } catch (Exception e) {
                logger.warn("AI selection failed for slot {}, falling back to deterministic", slotType, e);
            }
        }

        // Fallback to deterministic
        logger.debug("Using deterministic meal selection for slot: {}", slotType);
        return deterministicStrategy.chooseRecipe(slotType, preferredCuisines, dietaryTags, allergyCodes,
                dislikedIngredients, usedRecipeIds);
    }

    /**
     * Set user preferences in context for AI-enhanced selection. This allows AI to
     * make decisions based on full user profile.
     */
    public void setUserPreferencesContext(NutritionPreferencesService.PreferencesResponse prefs) {
        // Context stored per request scope if needed
    }
}
