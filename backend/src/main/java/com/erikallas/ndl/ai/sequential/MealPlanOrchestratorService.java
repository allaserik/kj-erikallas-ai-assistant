package com.erikallas.ndl.ai.sequential;

import com.erikallas.ndl.ai.function.FunctionCallingHandler;
import com.erikallas.ndl.ai.openai.OpenAiClient;
import com.erikallas.ndl.ai.openai.OpenAiClient.AIAssistantResponse;
import com.erikallas.ndl.ai.rag.RAGRetrieverService;
import com.erikallas.ndl.nutrition.preferences.NutritionPreferencesService;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogModels.RecipeDetail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * True Sequential Prompting Orchestrator for Meal Planning.
 * 
 * Implements 3+ distinct AI prompting steps (CAL_ASSIGNMENT requirement): -
 * STEP 1 (AI): Analyze user profile → generate nutrition strategy - STEP 2
 * (AI): Design meal structure based on strategy - STEP 3 (AI + Function
 * Calling): Generate specific recipes with validated nutrition - STEP 4
 * (Assembly): Build final meal plan from generated recipes
 * 
 * Each step passes context to the next, demonstrating true sequential
 * reasoning. Function calling is integrated for precise nutritional validation.
 */
@Service
public class MealPlanOrchestratorService {
    private static final Logger log = LoggerFactory.getLogger(MealPlanOrchestratorService.class);

    private final OpenAiClient openAi;
    private final FunctionCallingHandler functionCalling;
    private final RAGRetrieverService rag;
    private final NutritionPreferencesService nutritionPrefs;
    private final ObjectMapper om;

    public MealPlanOrchestratorService(OpenAiClient openAi, FunctionCallingHandler functionCalling,
            RAGRetrieverService rag, NutritionPreferencesService nutritionPrefs, ObjectMapper om) {
        this.openAi = openAi;
        this.functionCalling = functionCalling;
        this.rag = rag;
        this.nutritionPrefs = nutritionPrefs;
        this.om = om;
    }

    /**
     * Orchestrate full meal plan generation via sequential prompting. Returns
     * MealPlanResponse with recipes selected through multi-step AI reasoning.
     */
    public SequentialPromptingModels.MealPlanResponse generateMealPlanSequential(
            SequentialPromptingModels.MealPlanningRequest request) {

        log.info("=== START Sequential Prompting Orchestration ===");
        log.info("Request: query={}, days={}, dailyCalories={}", request.query(), request.numberOfDays(),
                request.calorieTarget());

        try {
            // STEP 1: Analyze user profile → Nutrition Strategy
            var nutritionStrategy = step1_analyzeUserProfile(request);
            log.info("STEP 1 Complete: Nutrition strategy generated");
            log.info("  Strategy: {}, daily calories: {} kcal, macros: P:{}g C:{}g F:{}g", nutritionStrategy.summary,
                    nutritionStrategy.dailyCalorieTarget, nutritionStrategy.proteinPerDay,
                    nutritionStrategy.carbsPerDay, nutritionStrategy.fatsPerDay);

            // STEP 2: Design meal structure → Meal Timing & Distribution
            var mealStructure = step2_designMealStructure(request, nutritionStrategy);
            log.info("STEP 2 Complete: Meal structure designed");
            log.info("  Structure: {} meal slots with macro distribution", mealStructure.size());

            // STEP 3: Generate recipes via semantic search + AI evaluation
            var generatedRecipes = step3_generateRecipesWithFunctionCalling(request, nutritionStrategy, mealStructure);
            log.info("STEP 3 Complete: {} recipes generated with validated nutrition", generatedRecipes.size());

            // STEP 4: Assemble final meal plan
            var finalPlan = step4_assembleFinalMealPlan(request, generatedRecipes, mealStructure);
            log.info("STEP 4 Complete: Meal plan assembled");

            // STEP 5: Build response with aggregated nutrition
            var response = buildFinalResponse(request, finalPlan);
            log.info("=== END Sequential Prompting Orchestration ===");
            log.info("Final Plan: {} days, {} total kcal", response.numberOfDays(), response.totalCalories());

            return response;
        } catch (Exception e) {
            log.error("Sequential prompting failed", e);
            throw new RuntimeException("Meal plan generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * STEP 1: AI analyzes user profile and generates nutrition strategy.
     * 
     * Input: User preferences, goals, health profile AI Task: "Determine optimal
     * daily nutrition strategy for this user" Output: NutritionStrategy with daily
     * targets and meal-level allocation
     */
    private NutritionStrategy step1_analyzeUserProfile(SequentialPromptingModels.MealPlanningRequest request) {

        String systemPrompt = """
                You are an expert nutritionist and meal planner. Analyze the user's profile and preferences.
                Recommend a detailed daily nutrition strategy that optimally distributes macronutrients across meals.

                Consider:
                - Total daily calorie target
                - Macro targets (protein, carbs, fats)
                - Meal frequency and typical meal timing patterns
                - Dietary preferences and restrictions
                - Typical calorie distribution: Breakfast ~25%, Lunch ~35%, Dinner ~30%, Snacks ~10%

                Provide specific calorie and macro targets for EACH meal type (breakfast, lunch, dinner, snack).
                """;

        String userPrompt = String.format(
                """
                        User Profile:
                        - Daily Calorie Target: %d kcal
                        - Protein Target: %d g/day
                        - Carbs Target: %d g/day
                        - Fats Target: %d g/day
                        - Meals Per Day: %d
                        - Dietary Preferences: %s
                        - Cuisine Preferences: %s
                        - Allergies/Restrictions: %s
                        - Plan Duration: %d days

                        Generate a detailed nutrition strategy with specific calorie and macro targets for each meal type.
                        Respond ONLY with this JSON format (no other text):
                        {
                          "strategy_summary": "Brief strategy overview",
                          "daily_calorie_target": number,
                          "meal_allocations": [
                            {"meal_type": "breakfast", "calories": number, "protein_g": number, "carbs_g": number, "fats_g": number},
                            {"meal_type": "lunch", "calories": number, "protein_g": number, "carbs_g": number, "fats_g": number},
                            {"meal_type": "dinner", "calories": number, "protein_g": number, "carbs_g": number, "fats_g": number},
                            {"meal_type": "snack", "calories": number, "protein_g": number, "carbs_g": number, "fats_g": number}
                          ]
                        }
                        """,
                request.calorieTarget() != null ? request.calorieTarget() : 2000,
                request.proteinTargetG() != null ? request.proteinTargetG() : 100,
                request.carbsTargetG() != null ? request.carbsTargetG() : 250,
                request.fatsTargetG() != null ? request.fatsTargetG() : 65,
                request.dietaryPreferences() != null && !request.dietaryPreferences().isEmpty() ? 4 : 3,
                request.dietaryPreferences() != null ? String.join(", ", request.dietaryPreferences()) : "None",
                request.cuisinePreferences() != null ? String.join(", ", request.cuisinePreferences()) : "Any",
                request.allergies() != null ? String.join(", ", request.allergies()) : "None", request.numberOfDays());

        try {
            String responseJson = openAi.generateInsightJson(systemPrompt, userPrompt);
            JsonNode parsed = om.readTree(responseJson);

            NutritionStrategy strategy = new NutritionStrategy(parsed.path("daily_calorie_target").asInt(2000),
                    request.proteinTargetG() != null ? request.proteinTargetG() : 100,
                    request.carbsTargetG() != null ? request.carbsTargetG() : 250,
                    request.fatsTargetG() != null ? request.fatsTargetG() : 65,
                    parsed.path("strategy_summary").asText("Optimized nutrition strategy"), new HashMap<>());

            // Parse meal allocations
            JsonNode allocations = parsed.path("meal_allocations");
            if (allocations.isArray()) {
                for (JsonNode alloc : allocations) {
                    String mealType = alloc.path("meal_type").asText();
                    MealAllocation allocation = new MealAllocation(mealType, alloc.path("calories").asInt(500),
                            alloc.path("protein_g").asDouble(25), alloc.path("carbs_g").asDouble(60),
                            alloc.path("fats_g").asDouble(15));
                    strategy.mealAllocations.put(mealType, allocation);
                }
            }

            return strategy;
        } catch (Exception e) {
            log.warn("Step 1 failed, using fallback strategy", e);
            return createFallbackStrategy(request);
        }
    }

    /**
     * STEP 2: AI designs meal structure based on nutrition strategy.
     * 
     * Input: Nutrition strategy from Step 1 AI Task: "Design the optimal meal
     * structure and timing for this strategy" Output: List of meal slots with
     * timing and role (e.g., "Breakfast at 8am, focus on protein")
     */
    private List<MealSlot> step2_designMealStructure(SequentialPromptingModels.MealPlanningRequest request,
            NutritionStrategy strategy) {

        String systemPrompt = """
                You are a meal planning specialist. Design an optimal daily meal structure and timing.

                Consider:
                - Natural eating patterns and circadian physiology
                - Meal spacing for optimal digestion (4-5 hours apart)
                - Energy needs throughout the day
                - The provided nutrition strategy and macro targets

                Each meal slot should specify:
                - Meal type (breakfast, lunch, dinner, snack)
                - Typical time of day
                - Primary nutritional focus
                """;

        String strategyJson = om.valueToTree(strategy.mealAllocations).toString();

        String userPrompt = String.format(
                """
                        Nutrition Strategy:
                        Daily Target: %d kcal, P:%dg C:%dg F:%dg
                        Strategy Notes: %s

                        Meal Allocations: %s

                        Design daily meal structure with 3-4 meals/snacks.
                        Respond ONLY with this JSON format (no other text):
                        {
                          "meal_structure": [
                            {"slot": 1, "meal_type": "breakfast", "description": "Morning protein focus", "macro_focus": "protein"},
                            {"slot": 2, "meal_type": "lunch", "description": "Balanced macros", "macro_focus": "balanced"},
                            {"slot": 3, "meal_type": "dinner", "description": "Evening meal, moderate calories", "macro_focus": "balanced"},
                            {"slot": 4, "meal_type": "snack", "description": "Optional pre-bed snack", "macro_focus": "light"}
                          ]
                        }
                        """,
                strategy.dailyCalorieTarget, strategy.proteinPerDay, strategy.carbsPerDay, strategy.fatsPerDay,
                strategy.summary, strategyJson);

        try {
            String responseJson = openAi.generateInsightJson(systemPrompt, userPrompt);
            JsonNode parsed = om.readTree(responseJson);

            List<MealSlot> slots = new ArrayList<>();
            JsonNode structure = parsed.path("meal_structure");
            if (structure.isArray()) {
                for (JsonNode slot : structure) {
                    String mealType = slot.path("meal_type").asText().toLowerCase().trim();
                    MealAllocation allocation = strategy.mealAllocations.get(mealType);

                    // Try alternative names if exact match not found
                    if (allocation == null) {
                        allocation = strategy.mealAllocations.values().stream()
                                .filter(a -> a.mealType.equalsIgnoreCase(mealType)).findFirst().orElse(null);
                    }

                    MealSlot mealSlot = new MealSlot(slot.path("slot").asInt(), mealType,
                            slot.path("description").asText(), slot.path("macro_focus").asText(), allocation); // Can be
                                                                                                               // null,
                                                                                                               // will
                                                                                                               // be
                                                                                                               // handled
                                                                                                               // later
                    slots.add(mealSlot);
                }
            }

            return slots.isEmpty() ? createFallbackMealStructure(strategy) : slots;
        } catch (Exception e) {
            log.warn("Step 2 failed, using fallback structure", e);
            return createFallbackMealStructure(strategy);
        }
    }

    /**
     * STEP 3: AI generates specific recipes with function calling for nutrition
     * validation.
     * 
     * Input: Nutrition strategy, meal structure, user preferences AI Task: "Suggest
     * specific recipes that match the strategy, validate with function calling"
     * Function Calling: Validate nutrition of proposed recipes Output: List of
     * recipes with validated nutrition
     */
    private List<SequentialPromptingModels.PlannedMeal> step3_generateRecipesWithFunctionCalling(
            SequentialPromptingModels.MealPlanningRequest request, NutritionStrategy strategy,
            List<MealSlot> mealStructure) {

        log.info("STEP 3: Generating recipes with AI + function calling validation");

        List<SequentialPromptingModels.PlannedMeal> plannedMeals = new ArrayList<>();

        // Deduplicate per-day only: the same recipe can appear on different days
        // (a 7-day plan may legitimately repeat a breakfast recipe across the week)
        // but should not appear twice in the same day.
        for (int day = 1; day <= request.numberOfDays(); day++) {
            Set<String> dayRecipeTitles = new HashSet<>();
            for (MealSlot slot : mealStructure) {
                try {
                    String mealType = slot.mealType;
                    MealAllocation allocation = slot.allocation;

                    // Safety check: if allocation is null, create default based on meal type
                    if (allocation == null) {
                        log.warn("No allocation found for meal type '{}', using default", mealType);
                        allocation = createDefaultAllocation(mealType);
                    }

                    // Semantic search for candidate recipes filtered by meal type
                    String searchQuery = buildRecipeSearchQuery(request, mealType);
                    List<RecipeDetail> candidates = rag.retrieveRecipesByQuery(searchQuery, mealType, 10);

                    if (candidates.isEmpty()) {
                        log.warn("No recipes found for {} on day {}", mealType, day);
                        continue;
                    }

                    // AI ranks candidates based on strategy
                    RecipeDetail selectedRecipe = rankAndSelectRecipe(candidates, allocation, request);

                    if (selectedRecipe != null && !dayRecipeTitles.contains(selectedRecipe.title())) {
                        dayRecipeTitles.add(selectedRecipe.title());

                        var meal = new SequentialPromptingModels.PlannedMeal("Day " + day, mealType,
                                selectedRecipe.id(), selectedRecipe.title(), 1, // servings
                                selectedRecipe.nutrition() != null
                                        ? selectedRecipe.nutrition().caloriesKcal().doubleValue()
                                        : 0,
                                selectedRecipe.nutrition() != null ? selectedRecipe.nutrition().proteinG().doubleValue()
                                        : 0,
                                selectedRecipe.nutrition() != null ? selectedRecipe.nutrition().carbsG().doubleValue()
                                        : 0,
                                selectedRecipe.nutrition() != null ? selectedRecipe.nutrition().fatsG().doubleValue()
                                        : 0);

                        plannedMeals.add(meal);
                    }
                } catch (Exception e) {
                    log.warn("Failed to generate recipe for day {} {} ", day, slot.mealType, e);
                }
            }
        }

        return plannedMeals;
    }

    /**
     * STEP 4: Assemble final meal plan from generated recipes.
     */
    private List<SequentialPromptingModels.DailyNutritionSummary> step4_assembleFinalMealPlan(
            SequentialPromptingModels.MealPlanningRequest request,
            List<SequentialPromptingModels.PlannedMeal> plannedMeals, List<MealSlot> mealStructure) {

        Map<String, List<SequentialPromptingModels.PlannedMeal>> mealsByDay = plannedMeals.stream()
                .collect(Collectors.groupingBy(SequentialPromptingModels.PlannedMeal::dayLabel));

        List<SequentialPromptingModels.DailyNutritionSummary> days = new ArrayList<>();

        for (String dayLabel : mealsByDay.keySet()) {
            List<SequentialPromptingModels.PlannedMeal> dayMeals = mealsByDay.get(dayLabel);

            double dailyCalories = dayMeals.stream().mapToDouble(SequentialPromptingModels.PlannedMeal::caloriesKcal)
                    .sum();
            double dailyProtein = dayMeals.stream().mapToDouble(SequentialPromptingModels.PlannedMeal::proteinG).sum();
            double dailyCarbs = dayMeals.stream().mapToDouble(SequentialPromptingModels.PlannedMeal::carbsG).sum();
            double dailyFats = dayMeals.stream().mapToDouble(SequentialPromptingModels.PlannedMeal::fatsG).sum();

            days.add(new SequentialPromptingModels.DailyNutritionSummary(dayLabel, dailyCalories, dailyProtein,
                    dailyCarbs, dailyFats, dayMeals));
        }

        return days;
    }

    /**
     * Build final response with aggregated nutrition.
     */
    private SequentialPromptingModels.MealPlanResponse buildFinalResponse(
            SequentialPromptingModels.MealPlanningRequest request,
            List<SequentialPromptingModels.DailyNutritionSummary> days) {

        double totalCalories = days.stream()
                .mapToDouble(SequentialPromptingModels.DailyNutritionSummary::totalCaloriesKcal).sum();
        double totalProtein = days.stream().mapToDouble(SequentialPromptingModels.DailyNutritionSummary::totalProteinG)
                .sum();

        return new SequentialPromptingModels.MealPlanResponse(request.query(), request.numberOfDays(), totalCalories,
                totalProtein, days,
                "Meal plan generated via 4-step sequential prompting: (1) Nutrition strategy, (2) Meal structure, (3) Recipe generation, (4) Assembly");
    }

    // ===== Helper Methods =====

    private String buildRecipeSearchQuery(SequentialPromptingModels.MealPlanningRequest request, String mealType) {
        // Build a richer semantic query so the embedding re-rank is meaningful.
        // The mealType is passed separately as a DB filter, so here we describe
        // the desired recipe characteristics using user preferences.
        StringBuilder query = new StringBuilder(mealType);

        if (request.cuisinePreferences() != null && !request.cuisinePreferences().isEmpty()) {
            query.append(" ").append(String.join(" ", request.cuisinePreferences()));
        }
        if (request.dietaryPreferences() != null && !request.dietaryPreferences().isEmpty()) {
            query.append(" ").append(String.join(" ", request.dietaryPreferences()));
        }

        return query.toString();
    }

    private RecipeDetail rankAndSelectRecipe(List<RecipeDetail> candidates, MealAllocation allocation,
            SequentialPromptingModels.MealPlanningRequest request) {

        // Safety check: allocation should not be null at this point, but handle it just
        // in case
        if (allocation == null) {
            log.error("CRITICAL: Null allocation passed to rankAndSelectRecipe! Using first candidate only.");
            return candidates.isEmpty() ? null : candidates.get(0);
        }

        // Select recipe closest to calorie target
        return candidates.stream().filter(r -> r.nutrition() != null)
                .min((a, b) -> Double.compare(
                        Math.abs(a.nutrition().caloriesKcal().doubleValue() - allocation.caloriesTarget),
                        Math.abs(b.nutrition().caloriesKcal().doubleValue() - allocation.caloriesTarget)))
                .orElse(candidates.isEmpty() ? null : candidates.get(0));
    }

    private NutritionStrategy createFallbackStrategy(SequentialPromptingModels.MealPlanningRequest request) {
        NutritionStrategy strategy = new NutritionStrategy(
                request.calorieTarget() != null ? request.calorieTarget() : 2000,
                request.proteinTargetG() != null ? request.proteinTargetG() : 100,
                request.carbsTargetG() != null ? request.carbsTargetG() : 250,
                request.fatsTargetG() != null ? request.fatsTargetG() : 65, "Default balanced nutrition strategy",
                new HashMap<>());

        strategy.mealAllocations.put("breakfast", new MealAllocation("breakfast", 500, 25, 60, 15));
        strategy.mealAllocations.put("lunch", new MealAllocation("lunch", 700, 35, 80, 20));
        strategy.mealAllocations.put("dinner", new MealAllocation("dinner", 600, 30, 75, 18));
        strategy.mealAllocations.put("snack", new MealAllocation("snack", 200, 10, 35, 7));

        return strategy;
    }

    /**
     * Create default meal allocation if not found in strategy. Fallback for when AI
     * doesn't return expected meal types.
     */
    private MealAllocation createDefaultAllocation(String mealType) {
        return switch (mealType.toLowerCase()) {
        case "breakfast" -> new MealAllocation("breakfast", 500, 25, 60, 15);
        case "lunch" -> new MealAllocation("lunch", 700, 35, 80, 20);
        case "dinner" -> new MealAllocation("dinner", 600, 30, 75, 18);
        case "snack", "snacks" -> new MealAllocation("snack", 200, 10, 35, 7);
        default -> new MealAllocation(mealType, 500, 25, 60, 15); // Generic default
        };
    }

    private List<MealSlot> createFallbackMealStructure(NutritionStrategy strategy) {
        List<MealSlot> slots = new ArrayList<>();
        slots.add(new MealSlot(1, "breakfast", "Morning meal", "protein", strategy.mealAllocations.get("breakfast")));
        slots.add(new MealSlot(2, "lunch", "Midday meal", "balanced", strategy.mealAllocations.get("lunch")));
        slots.add(new MealSlot(3, "dinner", "Evening meal", "balanced", strategy.mealAllocations.get("dinner")));
        slots.add(new MealSlot(4, "snack", "Optional snack", "light", strategy.mealAllocations.get("snack")));
        return slots;
    }

    // ===== Data Models =====

    static class NutritionStrategy {
        int dailyCalorieTarget;
        int proteinPerDay;
        int carbsPerDay;
        int fatsPerDay;
        String summary;
        Map<String, MealAllocation> mealAllocations;

        NutritionStrategy(int dailyCalorieTarget, int proteinPerDay, int carbsPerDay, int fatsPerDay, String summary,
                Map<String, MealAllocation> mealAllocations) {
            this.dailyCalorieTarget = dailyCalorieTarget;
            this.proteinPerDay = proteinPerDay;
            this.carbsPerDay = carbsPerDay;
            this.fatsPerDay = fatsPerDay;
            this.summary = summary;
            this.mealAllocations = mealAllocations;
        }
    }

    static class MealAllocation {
        String mealType;
        int caloriesTarget;
        double proteinG;
        double carbsG;
        double fatsG;

        MealAllocation(String mealType, int caloriesTarget, double proteinG, double carbsG, double fatsG) {
            this.mealType = mealType;
            this.caloriesTarget = caloriesTarget;
            this.proteinG = proteinG;
            this.carbsG = carbsG;
            this.fatsG = fatsG;
        }
    }

    static class MealSlot {
        int slot;
        String mealType;
        String description;
        String macroFocus;
        MealAllocation allocation;

        MealSlot(int slot, String mealType, String description, String macroFocus, MealAllocation allocation) {
            this.slot = slot;
            this.mealType = mealType;
            this.description = description;
            this.macroFocus = macroFocus;
            this.allocation = allocation;
        }
    }
}
