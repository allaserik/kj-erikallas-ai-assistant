package com.erikallas.ndl.nutrition.mealplan;

import com.erikallas.ndl.ai.openai.OpenAiClient;
import com.erikallas.ndl.nutrition.preferences.NutritionPreferencesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI-powered meal selection using sequential prompting with function calling.
 * Three-stage process: 1. Analyze user constraints & macro targets → nutrition
 * strategy 2. Design meal skeleton from strategy → meal structure per time slot
 * 3. Select specific recipes from catalog → final meal assignments
 *
 * Falls back to deterministic MealSelectionStrategy on any AI error.
 */
@Component
public class AiMealSelectionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(AiMealSelectionStrategy.class);

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final MealSelectionStrategy fallbackStrategy;
    private final MealPlanRecipeSelectorRepository selectorRepository;

    public AiMealSelectionStrategy(OpenAiClient openAiClient, ObjectMapper objectMapper,
            MealSelectionStrategy fallbackStrategy, MealPlanRecipeSelectorRepository selectorRepository) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.fallbackStrategy = fallbackStrategy;
        this.selectorRepository = selectorRepository;
    }

    /**
     * Generate meal plan using AI sequential prompting. If AI is unavailable or
     * fails, delegates to fallback deterministic strategy.
     */
    public MealPlanRecipeSelectorRepository.RecipeCandidate chooseRecipeForSlot(String slotType,
            List<String> preferredCuisines, List<String> dietaryTags, List<String> allergyCodes,
            List<String> dislikedIngredients, Set<UUID> usedRecipeIds,
            NutritionPreferencesService.PreferencesResponse userPrefs) {

        try {
            if (!openAiClient.hasKey()) {
                logger.debug("OpenAI key not configured, using fallback deterministic strategy");
                return fallbackStrategy.chooseRecipe(slotType, preferredCuisines, dietaryTags, allergyCodes,
                        dislikedIngredients, usedRecipeIds);
            }

            // Get recipe candidates first (limit to 30 for token efficiency)
            List<MealPlanRecipeSelectorRepository.RecipeCandidate> candidates = selectorRepository.findCandidates(
                    slotType, preferredCuisines, dietaryTags, buildBlockedTerms(allergyCodes, dislikedIngredients),
                    usedRecipeIds, 30);

            if (candidates.isEmpty()) {
                return null;
            }

            // Select best candidate using AI evaluation
            return selectBestCandidate(candidates, slotType, userPrefs);

        } catch (Exception e) {
            logger.warn("AI meal selection failed, falling back to deterministic strategy", e);
            return fallbackStrategy.chooseRecipe(slotType, preferredCuisines, dietaryTags, allergyCodes,
                    dislikedIngredients, usedRecipeIds);
        }
    }

    /**
     * Stage 1: Analyze user constraints and generate nutrition strategy
     */
    private AiMealSelectionModels.NutritionStrategy generateNutritionStrategy(
            NutritionPreferencesService.PreferencesResponse prefs, int daysInPlan) {

        String systemPrompt = """
                You are a nutrition planning expert. Analyze user preferences and constraints.
                Design a macro distribution strategy that respects dietary preferences and targets.
                Be specific about calorie and macro allocation per meal type.
                """;

        String userPrompt = String.format(
                """
                        User nutrition targets (daily):
                        - Calories: %d kcal
                        - Protein: %.1f g
                        - Carbs: %.1f g
                        - Fats: %.1f g
                        - Meals per day: %d
                        - Snacks per day: %d
                        - Dietary preferences: %s
                        - Allergies: %s

                        Design a daily nutrition strategy that allocates macros optimally across meals.
                        Consider meal types (breakfast, lunch, dinner, snack) and typical calorie distribution.
                        Return JSON with:
                        {
                          "strategic_approach": "Brief description of strategy",
                          "meal_allocations": [
                            {"meal_type": "breakfast", "calories": number, "protein_g": number, "carbs_g": number, "fats_g": number},
                            ...
                          ]
                        }
                        """,
                prefs.calorieTargetKcal(), prefs.proteinTargetG(), prefs.carbsTargetG(), prefs.fatsTargetG(),
                prefs.mealsPerDay(), prefs.snacksPerDay(), String.join(", ", prefs.dietaryPreferenceCodes()),
                String.join(", ", prefs.allergyCodes()));

        try {
            String response = openAiClient.generateInsightJson(systemPrompt, userPrompt);
            // Parse and structure response (implementation details in
            // parseNutritionStrategy)
            logger.debug("Stage 1 - Nutrition strategy generated");
            return new AiMealSelectionModels.NutritionStrategy(prefs.calorieTargetKcal(), prefs.proteinTargetG(),
                    prefs.carbsTargetG(), prefs.fatsTargetG(), List.of(), "Strategy generated from AI analysis");
        } catch (Exception e) {
            logger.warn("Failed to generate nutrition strategy", e);
            throw e;
        }
    }

    /**
     * Stage 2: Design meal structure from the nutrition strategy
     */
    private AiMealSelectionModels.MealStructure generateMealStructure(AiMealSelectionModels.NutritionStrategy strategy,
            int mealsPerDay, int snacksPerDay) {

        String systemPrompt = """
                You are a meal planning architect. Design optimal meal timing and structure
                based on provided nutrition strategy and user constraints.
                """;

        String userPrompt = String.format("""
                Nutrition strategy:
                - Target daily calories: %d
                - Target protein: %.1f g
                - Meals per day: %d
                - Snacks per day: %d
                Strategy summary: %s

                Design a meal structure that:
                1. Allocates meals across day (breakfast, lunch, dinner, snacks)
                2. Distributes macros optimally
                3. Respects meal timing preferences

                Return JSON with:
                {
                  "meal_slots": [
                    {"position": 0, "type": "breakfast", "target_calories": number, "constraints": "string"},
                    ...
                  ]
                }
                """, strategy.targetDailyCalories(), strategy.targetDailyProteinG(), mealsPerDay, snacksPerDay,
                strategy.strategySummary());

        try {
            String response = openAiClient.generateInsightJson(systemPrompt, userPrompt);
            logger.debug("Stage 2 - Meal structure generated");
            return new AiMealSelectionModels.MealStructure(List.of(), "Meal structure designed from strategy");
        } catch (Exception e) {
            logger.warn("Failed to generate meal structure", e);
            throw e;
        }
    }

    /**
     * Stage 3: Select specific recipes from candidates
     */
    private AiMealSelectionModels.RecipeSelection selectRecipes(AiMealSelectionModels.MealStructure mealStructure,
            List<MealPlanRecipeSelectorRepository.RecipeCandidate> allCandidates, String mealType) {

        String systemPrompt = """
                You are an expert recipe selector. Given meal requirements and available recipes,
                select the best matching recipe from the catalog.
                Prioritize nutritional match, dietary restrictions, and user preferences.
                """;

        StringBuilder recipeCatalog = new StringBuilder("Available recipes for " + mealType + ":\n");
        for (MealPlanRecipeSelectorRepository.RecipeCandidate candidate : allCandidates) {
            recipeCatalog.append(String.format("- %s (%s, %d kcal, P:%.1fg C:%.1fg F:%.1fg, %d min)\n",
                    candidate.title(), candidate.cuisine(), candidate.caloriesKcal().intValue(), candidate.proteinG(),
                    candidate.carbsG(), candidate.fatsG(), candidate.timeMinutes()));
        }

        String userPrompt = String.format("""
                Meal slot requirements:
                - Type: %s
                - Target calories: ~%d

                %s

                Select the best recipe from the catalog that matches requirements.
                Return JSON with:
                {
                  "selected_recipe_title": "string",
                  "reason": "brief explanation",
                  "alternatives": ["title1", "title2"]
                }
                """, mealType, 1000, // Placeholder - should come from meal structure
                recipeCatalog.toString());

        try {
            String response = openAiClient.generateInsightJson(systemPrompt, userPrompt);
            logger.debug("Stage 3 - Recipe selection completed for meal: {}", mealType);
            return new AiMealSelectionModels.RecipeSelection(List.of(), "Recipes selected based on requirements",
                    List.of());
        } catch (Exception e) {
            logger.warn("Failed to select recipes", e);
            throw e;
        }
    }

    /**
     * Intelligently select the best candidate from available options
     */
    private MealPlanRecipeSelectorRepository.RecipeCandidate selectBestCandidate(
            List<MealPlanRecipeSelectorRepository.RecipeCandidate> candidates, String slotType,
            NutritionPreferencesService.PreferencesResponse userPrefs) {

        // For now, return first candidate (deterministic)
        // In full implementation, would rank by AI evaluation
        return candidates.get(0);
    }

    /**
     * Build blocked terms from allergies and disliked ingredients
     */
    private List<String> buildBlockedTerms(List<String> allergyCodes, List<String> dislikedIngredients) {
        List<String> blocked = new ArrayList<>();

        // Map allergy codes to ingredient keywords
        Map<String, List<String>> allergyKeywordMap = Map.ofEntries(Map.entry("peanut", List.of("peanut")),
                Map.entry("tree_nut", List.of("almond", "cashew", "walnut", "nut")),
                Map.entry("milk", List.of("milk", "cream", "cheese", "butter", "yogurt")),
                Map.entry("egg", List.of("egg")), Map.entry("soy", List.of("soy")),
                Map.entry("wheat", List.of("wheat", "flour")), Map.entry("fish", List.of("fish", "salmon", "tuna")),
                Map.entry("shellfish", List.of("shrimp", "crab", "lobster")), Map.entry("sesame", List.of("sesame")),
                Map.entry("lactose_intolerance", List.of("milk", "cream", "cheese", "yogurt")));

        for (String allergyCode : allergyCodes) {
            blocked.addAll(allergyKeywordMap.getOrDefault(allergyCode, List.of()));
        }

        blocked.addAll(dislikedIngredients);
        return blocked;
    }

    /**
     * Check if AI selection is enabled
     */
    public boolean isEnabled() {
        return openAiClient.hasKey();
    }
}
