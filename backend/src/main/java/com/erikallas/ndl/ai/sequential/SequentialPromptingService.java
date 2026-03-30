package com.erikallas.ndl.ai.sequential;

import org.springframework.stereotype.Service;
import com.erikallas.ndl.ai.rag.RAGRetrieverService;
import com.erikallas.ndl.ai.function.FunctionCallingHandler;
import com.erikallas.ndl.ai.function.NutritionCalculationService;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogModels.*;
import com.erikallas.ndl.nutrition.mealplan.MealSelectionStrategyComposite;
import com.erikallas.ndl.nutrition.preferences.NutritionPreferencesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Sequential Prompting Service for multi-step AI meal planning.
 * 
 * Phase 3: Orchestrates meal plan generation through sequential steps: 1. Parse
 * user request → Extract preferences 2. Semantic recipe search → Get relevant
 * recipes 3. Score and filter recipes → Based on nutrition/preferences 4.
 * Generate meal combinations → Assemble into daily plans 5. Calculate total
 * nutrition → Return complete plan
 * 
 * Uses MealSelectionStrategyComposite which intelligently chooses between
 * AI-powered and deterministic meal selection.
 */
@Service
public class SequentialPromptingService {
    private static final Logger log = LoggerFactory.getLogger(SequentialPromptingService.class);

    private final RAGRetrieverService ragRetrieverService;
    private final FunctionCallingHandler functionCallingHandler;
    private final NutritionCalculationService nutritionCalculationService;
    private final MealSelectionStrategyComposite mealSelectionStrategy;
    private final NutritionPreferencesService nutritionPreferencesService;
    private final ObjectMapper objectMapper;

    // Default meal type distribution
    private static final Map<String, Integer> DEFAULT_MEALS_PER_TYPE = Map.of("breakfast", 1, "lunch", 1, "dinner", 1,
            "snack", 1);

    public SequentialPromptingService(RAGRetrieverService ragRetrieverService,
            FunctionCallingHandler functionCallingHandler, NutritionCalculationService nutritionCalculationService,
            MealSelectionStrategyComposite mealSelectionStrategy,
            NutritionPreferencesService nutritionPreferencesService, ObjectMapper objectMapper) {
        this.ragRetrieverService = ragRetrieverService;
        this.functionCallingHandler = functionCallingHandler;
        this.nutritionCalculationService = nutritionCalculationService;
        this.mealSelectionStrategy = mealSelectionStrategy;
        this.nutritionPreferencesService = nutritionPreferencesService;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a multi-day meal plan from a user request.
     * 
     * Sequential steps: 1. Parse request → extract preferences 2. Retrieve recipes
     * → semantic search per meal type 3. Score recipes → based on nutrition and
     * preferences 4. Assemble plan → combine recipes into daily meals 5. Calculate
     * nutrition → aggregate totals
     */
    public SequentialPromptingModels.MealPlanResponse generateMealPlan(
            SequentialPromptingModels.MealPlanningRequest request) {

        try {
            log.info("Starting sequential meal plan generation for query: {}", request.query());

            // Step 1: Parse and normalize request
            var preferences = extractPreferences(request);
            log.info("Step 1 Complete: Extracted preferences - Days={}, Meal types={}", request.numberOfDays(),
                    preferences.keySet());

            // Step 2: Retrieve recipes via semantic search
            var recipes = retrieveRecipesForMealTypes(request);
            log.info("Step 2 Complete: Retrieved {} recipes", recipes.size());

            // Step 3: Score recipes based on nutrition and preferences
            var scoredRecipes = scoreRecipes(recipes, request);
            log.info("Step 3 Complete: Scored {} recipes", scoredRecipes.size());

            // Step 4: Assemble meal plan (using AI-powered recipe selection)
            var mealPlan = assembleMealPlan(recipes, request);
            log.info("Step 4 Complete: Assembled {} daily meal summaries", mealPlan.size());

            // Step 5: Calculate total nutrition
            var response = buildResponse(request, mealPlan);
            log.info("Step 5 Complete: Generated meal plan - Total calories: {}", response.totalCalories());

            return response;
        } catch (Exception e) {
            log.error("Error generating meal plan for query: {}", request.query(), e);
            throw new RuntimeException("Meal plan generation failed: " + e.getMessage());
        }
    }

    /**
     * Step 1: Extract and normalize preferences from user request.
     */
    private Map<String, Object> extractPreferences(SequentialPromptingModels.MealPlanningRequest request) {

        return Map.of("numberOfDays", request.numberOfDays(), "mealType",
                request.mealType() != null ? request.mealType() : "all", "calorieTarget",
                request.calorieTarget() != null ? request.calorieTarget() : 2000, "proteinTargetG",
                request.proteinTargetG() != null ? request.proteinTargetG() : 100, "dietaryPreferences",
                request.dietaryPreferences() != null ? request.dietaryPreferences() : List.of(), "cuisinePreferences",
                request.cuisinePreferences() != null ? request.cuisinePreferences() : List.of(), "allergies",
                request.allergies() != null ? request.allergies() : List.of());
    }

    /**
     * Step 2: Retrieve recipes via semantic search for each meal type.
     */
    private List<RecipeDetail> retrieveRecipesForMealTypes(SequentialPromptingModels.MealPlanningRequest request) {

        List<RecipeDetail> allRecipes = new ArrayList<>();

        // Build semantic queries for each meal type
        List<String> queries = buildSemanticQueries(request);

        log.info("Step 2: Starting semantic recipe retrieval with {} queries", queries.size());

        for (String query : queries) {
            try {
                var recipes = ragRetrieverService.retrieveRecipesByQuery(query, 15);
                log.debug("Query '{}': Retrieved {} recipes", query, recipes.size());
                allRecipes.addAll(recipes);
            } catch (Exception e) {
                log.error("Failed to retrieve recipes for query '{}': {}", query, e.getMessage(), e);
            }
        }

        // Remove duplicates by recipe ID
        var uniqueRecipes = allRecipes.stream()
                .collect(Collectors.toMap(RecipeDetail::id, r -> r, (existing, replacement) -> existing)).values()
                .stream().collect(Collectors.toList());

        log.info("Step 2 Complete: Retrieved {} total recipes, {} unique after deduplication", allRecipes.size(),
                uniqueRecipes.size());

        return uniqueRecipes;
    }

    /**
     * Build semantic search queries for different meal types.
     */
    private List<String> buildSemanticQueries(SequentialPromptingModels.MealPlanningRequest request) {

        List<String> queries = new ArrayList<>();
        String baseQuery = request.query();

        // Add meal-type-specific queries
        if ("all".equals(request.mealType()) || request.mealType() == null) {
            queries.add(baseQuery + " breakfast");
            queries.add(baseQuery + " lunch");
            queries.add(baseQuery + " dinner");
        } else {
            queries.add(baseQuery + " " + request.mealType());
        }

        // Add dietary/cuisine modifiers
        if (request.cuisinePreferences() != null && !request.cuisinePreferences().isEmpty()) {
            queries.add(baseQuery + " " + String.join(" ", request.cuisinePreferences()));
        }

        return queries;
    }

    /**
     * Step 3: Score recipes based on nutrition and preference alignment.
     */
    private List<SequentialPromptingModels.ScoredRecipe> scoreRecipes(List<RecipeDetail> recipes,
            SequentialPromptingModels.MealPlanningRequest request) {

        double caloriePerMeal = (request.calorieTarget() != null ? request.calorieTarget() : 2000) / 4.0;

        return recipes.stream().map(recipe -> scoreRecipe(recipe, request, caloriePerMeal))
                .filter(sr -> sr.relevanceScore() > 0.3) // Filter low-scoring recipes
                .sorted((a, b) -> Double.compare(b.relevanceScore(), a.relevanceScore())).collect(Collectors.toList());
    }

    /**
     * Score a single recipe based on nutrition and alignment.
     */
    private SequentialPromptingModels.ScoredRecipe scoreRecipe(RecipeDetail recipe,
            SequentialPromptingModels.MealPlanningRequest request, double targetCaloriesPerMeal) {

        double score = 1.0;

        // Score based on calorie alignment
        if (recipe.nutrition() != null) {
            double actualCalories = recipe.nutrition().caloriesKcal().doubleValue();
            double calorieDeviation = Math.abs(actualCalories - targetCaloriesPerMeal) / targetCaloriesPerMeal;
            score *= Math.max(0, 1.0 - calorieDeviation); // Reduce score for deviation

            // Boost for high protein content
            if (request.proteinTargetG() != null) {
                double proteinPerMeal = request.proteinTargetG() / 4.0;
                double actualProtein = recipe.nutrition().proteinG().doubleValue();
                if (actualProtein >= proteinPerMeal * 0.8) {
                    score *= 1.2; // +20% bonus for meeting protein targets
                }
            }
        }

        // Boost for matching dietary preferences
        if (!request.dietaryPreferences().isEmpty() && recipe.dietaryTags() != null) {
            long matches = recipe.dietaryTags().stream().filter(tag -> request.dietaryPreferences().stream()
                    .anyMatch(pref -> tag.toLowerCase().contains(pref.toLowerCase()))).count();
            if (matches > 0) {
                score *= (1.0 + (matches * 0.1)); // +10% per matching dietary tag
            }
        }

        // Boost for matching cuisine preferences
        if (!request.cuisinePreferences().isEmpty() && recipe.cuisine() != null) {
            boolean cuisineMatch = request.cuisinePreferences().stream()
                    .anyMatch(pref -> recipe.cuisine().toLowerCase().contains(pref.toLowerCase()));
            if (cuisineMatch) {
                score *= 1.15; // +15% bonus for matching cuisine
            }
        }

        return new SequentialPromptingModels.ScoredRecipe(recipe.id(), recipe.title(), recipe.cuisine(), recipe.meal(),
                recipe.timeMinutes(), Math.min(score, 2.0), // Cap at 2.0
                recipe.nutrition() != null ? recipe.nutrition().caloriesKcal().doubleValue() : 0,
                recipe.nutrition() != null ? recipe.nutrition().proteinG().doubleValue() : 0,
                recipe.nutrition() != null ? recipe.nutrition().carbsG().doubleValue() : 0,
                recipe.nutrition() != null ? recipe.nutrition().fatsG().doubleValue() : 0);
    }

    /**
     * Step 4: Assemble recipes into daily meal plans using AI or deterministic
     * selection.
     * 
     * Attempts to use AI-powered meal selection (which may call OpenAI). Falls back
     * to deterministic selection if AI unavailable.
     */
    private List<SequentialPromptingModels.DailyNutritionSummary> assembleMealPlan(List<RecipeDetail> allRecipes,
            SequentialPromptingModels.MealPlanningRequest request) {

        List<SequentialPromptingModels.DailyNutritionSummary> days = new ArrayList<>();

        log.info("Step 4: Starting meal plan assembly with {} available recipes", allRecipes.size());

        // Build dietary info for meal selection
        List<String> dietaryTags = request.dietaryPreferences() != null ? request.dietaryPreferences() : List.of();
        List<String> allergyCodes = request.allergies() != null ? request.allergies() : List.of();
        List<String> dislikedIngredients = List.of(); // Could be extended from request
        List<String> preferredCuisines = request.cuisinePreferences() != null ? request.cuisinePreferences()
                : List.of();

        Set<UUID> usedRecipes = new HashSet<>();

        // Check if recipes are available
        if (allRecipes.isEmpty()) {
            log.error("NO RECIPES AVAILABLE! Recipe retrieval failed. Check RAG service and database.");
        }

        // Generate daily plans
        for (int day = 1; day <= request.numberOfDays(); day++) {
            var dailyMeals = new ArrayList<SequentialPromptingModels.PlannedMeal>();
            double dailyCalories = 0, dailyProtein = 0, dailyCarbsG = 0, dailyFats = 0;

            // Determine meal types for this day
            String[] mealTypes = { "breakfast", "lunch", "dinner", "snack" };

            for (String mealType : mealTypes) {
                try {
                    // Use composite strategy (tries AI first, falls back to deterministic)
                    var selectedRecipe = mealSelectionStrategy.chooseRecipe(mealType, preferredCuisines, dietaryTags,
                            allergyCodes, dislikedIngredients, usedRecipes, null); // userPrefs - will be handled by
                                                                                   // strategy with sensible defaults

                    if (selectedRecipe != null) {
                        usedRecipes.add(selectedRecipe.id());

                        var meal = new SequentialPromptingModels.PlannedMeal("Day " + day, mealType,
                                selectedRecipe.id(), selectedRecipe.title(), 1, // servings
                                selectedRecipe.caloriesKcal().doubleValue(), selectedRecipe.proteinG().doubleValue(),
                                selectedRecipe.carbsG().doubleValue(), selectedRecipe.fatsG().doubleValue());

                        dailyMeals.add(meal);
                        dailyCalories += selectedRecipe.caloriesKcal().doubleValue();
                        dailyProtein += selectedRecipe.proteinG().doubleValue();
                        dailyCarbsG += selectedRecipe.carbsG().doubleValue();
                        dailyFats += selectedRecipe.fatsG().doubleValue();

                        log.debug("Day {}: Selected {} - {} ({} kcal)", day, mealType, selectedRecipe.title(),
                                selectedRecipe.caloriesKcal());
                    } else {
                        log.warn("Day {}: Could not select recipe for {}", day, mealType);
                    }
                } catch (Exception e) {
                    log.error("Failed to select {} for day {}: {}", mealType, day, e.getMessage(), e);
                }
            }

            if (!dailyMeals.isEmpty()) {
                days.add(new SequentialPromptingModels.DailyNutritionSummary("Day " + day, dailyCalories, dailyProtein,
                        dailyCarbsG, dailyFats, dailyMeals));
                log.info("Day {}: Added {} meals ({} kcal total)", day, dailyMeals.size(), Math.round(dailyCalories));
            } else {
                log.error("Day {}: NO MEALS SELECTED! Recipe selection strategy returned null for all meal types.",
                        day);
            }
        }

        log.info("Step 4 Complete: Assembled {} days ({} with meals, {} empty)", request.numberOfDays(), days.size(),
                request.numberOfDays() - days.size());

        if (days.isEmpty()) {
            log.error(
                    "CRITICAL: Meal plan assembly resulted in 0 days! This means recipe selection completely failed.");
            log.error(
                    "Possible causes: (1) Recipe database is empty, (2) RAG retrieval failed, (3) No recipes match criteria");
        }

        return days;
    }

    /**
     * Step 5: Build final response with aggregated nutrition.
     */
    private SequentialPromptingModels.MealPlanResponse buildResponse(
            SequentialPromptingModels.MealPlanningRequest request,
            List<SequentialPromptingModels.DailyNutritionSummary> days) {

        double totalCalories = days.stream()
                .mapToDouble(SequentialPromptingModels.DailyNutritionSummary::totalCaloriesKcal).sum();

        double totalProtein = days.stream().mapToDouble(SequentialPromptingModels.DailyNutritionSummary::totalProteinG)
                .sum();

        String notes = String.format(
                "Generated %d-day meal plan for: %s. "
                        + "Plan provides ~%.0f total calories (%.0f/day) with %.0f total protein (%.0f/day).",
                request.numberOfDays(), request.query(), totalCalories, totalCalories / request.numberOfDays(),
                totalProtein, totalProtein / request.numberOfDays());

        return new SequentialPromptingModels.MealPlanResponse(request.query(), request.numberOfDays(), totalCalories,
                totalProtein, days, notes);
    }
}
