package com.erikallas.ndl.nutrition.mealplan.ai;

import com.erikallas.ndl.common.api.dto.ApiSuccess;
import com.erikallas.ndl.auth.user.UserService;
import com.erikallas.ndl.ai.function.FunctionCallingHandler;
import com.erikallas.ndl.ai.rag.RAGRetrieverService;
import com.erikallas.ndl.ai.sequential.SequentialPromptingService;
import com.erikallas.ndl.ai.sequential.SequentialPromptingModels;
import com.erikallas.ndl.ai.mealplan.MealPlanAIService;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogModels.RecipeDetail;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogQueryRepository;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogModels;
import com.erikallas.ndl.nutrition.mealplan.MealPlanModels;
import com.erikallas.ndl.nutrition.mealplan.MealPlanMealRepository;
import com.erikallas.ndl.nutrition.mealplan.MealPlanMealEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for AI-powered meal plan generation.
 * 
 * PHASE 1: Function calling for nutrition calculation - POST
 * /api/meal-plans/ai/function-call - Test function calling with nutrition
 * calculation
 * 
 * PHASE 2: Semantic search for recipes - GET /api/meal-plans/ai/search -
 * Semantic recipe search
 * 
 * PHASE 3: Sequential multi-step meal planning - POST
 * /api/meal-plans/ai/generate - Generate complete meal plan via sequential
 * prompting
 * 
 * PHASE 4+ (PENDING): - POST /api/meal-plans/ai/regenerate/{mealId} -
 * Regenerate specific meal - GET /api/meal-plans/ai/alternatives/{mealId} - Get
 * alternative suggestions
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/meal-plans/ai")
public class AIRecipeGenerationController {

    private static final Logger logger = LoggerFactory.getLogger(AIRecipeGenerationController.class);

    private final UserService userService;
    private final FunctionCallingHandler functionCallingHandler;
    private final RAGRetrieverService ragRetrieverService;
    private final SequentialPromptingService sequentialPromptingService;
    private final MealPlanAIService mealPlanAIService;
    private final RecipeCatalogQueryRepository recipeCatalogQueryRepository;
    private final MealPlanMealRepository mealPlanMealRepository;
    private final ObjectMapper objectMapper;

    public AIRecipeGenerationController(UserService userService, FunctionCallingHandler functionCallingHandler,
            RAGRetrieverService ragRetrieverService, SequentialPromptingService sequentialPromptingService,
            MealPlanAIService mealPlanAIService, RecipeCatalogQueryRepository recipeCatalogQueryRepository,
            MealPlanMealRepository mealPlanMealRepository, ObjectMapper objectMapper) {
        this.userService = userService;
        this.functionCallingHandler = functionCallingHandler;
        this.ragRetrieverService = ragRetrieverService;
        this.sequentialPromptingService = sequentialPromptingService;
        this.mealPlanAIService = mealPlanAIService;
        this.recipeCatalogQueryRepository = recipeCatalogQueryRepository;
        this.mealPlanMealRepository = mealPlanMealRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Phase 1: Test endpoint for function calling with nutrition calculation.
     * 
     * Example request body: { "ingredients": [ {"name": "chicken breast",
     * "quantity": 100, "unit": "g"}, {"name": "broccoli", "quantity": 150, "unit":
     * "g"} ], "servings": 2 }
     * 
     * @param auth    JWT authentication
     * @param request Ingredients and servings
     * @return Nutrition calculation result from function calling
     */
    @PostMapping("/function-call")
    public ApiSuccess<String> testFunctionCall(JwtAuthenticationToken auth, @RequestBody Map<String, Object> request) {

        var user = userService.ensureUserFromJwt(auth);

        try {
            // Execute function calling handler to calculate nutrition
            String result = functionCallingHandler.executeFunction("calculate_nutrition",
                    objectMapper.writeValueAsString(request));
            return ApiSuccess.of(result);
        } catch (Exception e) {
            return ApiSuccess.of("Error executing function call: " + e.getMessage());
        }
    }

    /**
     * Phase 2: Semantic recipe search using vector embeddings.
     * 
     * Retrieves recipes semantically similar to the query using word embeddings and
     * cosine similarity scoring.
     * 
     * Example query: "healthy high-protein breakfast"
     * 
     * @param auth  JWT authentication
     * @param query Search query (e.g., "healthy high-protein breakfast")
     * @param limit Maximum number of results (default 10, max 50)
     * @return List of semantically relevant recipes
     */
    @GetMapping("/search")
    public ApiSuccess<List<RecipeDetail>> semanticSearch(JwtAuthenticationToken auth, @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {

        var user = userService.ensureUserFromJwt(auth);

        // Validate inputs
        if (query == null || query.trim().isEmpty()) {
            return ApiSuccess.of(List.of());
        }

        int topK = Math.min(Math.max(limit, 1), 50); // Clamp between 1 and 50

        try {
            // Use RAG retriever for semantic search
            List<RecipeDetail> results = ragRetrieverService.retrieveRecipesByQuery(query, topK);
            return ApiSuccess.of(results);
        } catch (Exception e) {
            return ApiSuccess.of(List.of());
        }
    }

    /**
     * Phase 3: Generate complete meal plan via sequential prompting.
     * 
     * Multi-step orchestration: 1. Parse request → extract dietary preferences,
     * nutrition targets 2. Semantic search → retrieve relevant recipes for
     * different meal types 3. Score recipes → rank by nutrition alignment and
     * preferences 4. Assemble plan → create daily meal combinations 5. Calculate
     * totals → aggregate nutrition across days
     * 
     * Example request body: { "query": "healthy high-protein breakfast plan",
     * "numberOfDays": 3, "mealType": "breakfast", "calorieTarget": 2000,
     * "proteinTargetG": 150, "dietaryPreferences": ["low-carb", "high-protein"],
     * "cuisinePreferences": ["mediterranean", "asian"], "allergies": ["peanuts",
     * "shellfish"] }
     * 
     * @param auth    JWT authentication
     * @param request Meal planning request with preferences
     * @return Generated meal plan with nutrition summaries
     */
    @PostMapping("/generate")
    public ApiSuccess<SequentialPromptingModels.MealPlanResponse> generateMealPlan(JwtAuthenticationToken auth,
            @RequestBody SequentialPromptingModels.MealPlanningRequest request) {

        var user = userService.ensureUserFromJwt(auth);

        try {
            // Generate meal plan via sequential prompting
            var mealPlan = sequentialPromptingService.generateMealPlan(request);
            return ApiSuccess.of(mealPlan);
        } catch (Exception e) {
            return ApiSuccess.of(null);
        }
    }

    /**
     * Phase 4: Generate and save AI meal plan to database.
     * 
     * Extends Phase 3 by persisting the generated meal plan: 1. Execute sequential
     * prompting (Phase 3) 2. Save to database - creates MealPlanEntity +
     * MealPlanMealEntity rows 3. Return saved meal plan with database IDs
     * 
     * This endpoint provides full persistence - the meal plan can be retrieved,
     * modified, and tracked in the user's history.
     * 
     * @param auth    JWT authentication
     * @param request Meal planning request with preferences
     * @return Saved meal plan entity with generated IDs
     */
    @PostMapping("/generate-and-save")
    public ApiSuccess<MealPlanModels.GenerateResponse> generateAndSaveMealPlan(JwtAuthenticationToken auth,
            @RequestBody SequentialPromptingModels.MealPlanningRequest request) {

        var user = userService.ensureUserFromJwt(auth);

        try {
            // Step 1: Generate meal plan via sequential prompting (Phase 3)
            var mealPlan = sequentialPromptingService.generateMealPlan(request);

            // Step 2: Save to database (Phase 4)
            var savedPlan = mealPlanAIService.saveMealPlan(user.getId(), mealPlan);

            // Step 3: Build response with database IDs
            var response = mealPlanAIService.buildGenerateResponse(savedPlan, mealPlan);

            return ApiSuccess.of(response);
        } catch (Exception e) {
            logger.error("Meal plan generation failed for user {}: {}", user.getId(), e.getMessage(), e);
            throw new RuntimeException("Meal plan generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * PHASE 4+: Get alternative meal suggestions.
     * 
     * Finds alternative recipes for a specific meal by: 1. Getting the current
     * meal's type and nutrition profile 2. Searching for recipes of the same meal
     * type 3. Filtering out the current recipe 4. Returning top alternatives sorted
     * by relevance
     * 
     * @param auth   JWT authentication
     * @param mealId ID of the meal to get alternatives for
     * @param limit  Maximum number of alternatives to return (default 5, max 20)
     * @return List of alternative recipes with full details
     */
    @GetMapping("/alternatives/{mealId}")
    public ApiSuccess<List<RecipeDetail>> getAlternativeMeals(JwtAuthenticationToken auth, @PathVariable String mealId,
            @RequestParam(defaultValue = "5") int limit) {

        var user = userService.ensureUserFromJwt(auth);
        int topK = Math.min(Math.max(limit, 1), 20); // Clamp between 1 and 20

        try {
            // Step 1: Get the current meal
            var mealUuid = java.util.UUID.fromString(mealId);
            var meal = mealPlanMealRepository.findById(mealUuid)
                    .orElseThrow(() -> new IllegalArgumentException("Meal not found: " + mealId));

            // Step 2: Search for recipes of the same meal type
            var filters = new RecipeCatalogModels.RecipeSearchFilters(null, // q - no text query
                    null, // cuisine - keep open
                    meal.getMealType(), // meal type
                    null, // dietaryTag
                    null, // maxTimeMinutes
                    null, // maxCaloriesKcal
                    topK + 1 // Request one extra to account for current recipe
            );

            var candidates = recipeCatalogQueryRepository.search(filters);

            // Step 3: Filter out the current recipe and get details
            List<RecipeDetail> alternatives = new ArrayList<>();
            for (var candidate : candidates) {
                if (!candidate.id().equals(meal.getRecipeId()) && alternatives.size() < topK) {
                    try {
                        var detail = recipeCatalogQueryRepository.getById(candidate.id());
                        if (detail != null) {
                            alternatives.add(detail);
                        }
                    } catch (Exception e) {
                        logger.debug("Failed to get recipe details for {}: {}", candidate.id(), e.getMessage());
                    }
                }
            }

            logger.debug("Found {} alternatives for meal {} (type: {})", alternatives.size(), mealId,
                    meal.getMealType());
            return ApiSuccess.of(alternatives);
        } catch (Exception e) {
            logger.error("Failed to get alternatives for meal {}: {}", mealId, e.getMessage(), e);
            return ApiSuccess.of(new ArrayList<>()); // Return empty list on error
        }
    }

    /**
     * PHASE 4+: Regenerate a single meal in a meal plan.
     * 
     * Uses semantic search and sequential prompting to generate a replacement meal
     * for a specific meal slot while preserving meal type and nutrition
     * constraints.
     * 
     * @param auth    JWT authentication
     * @param mealId  ID of the meal to regenerate
     * @param request Preferences for regeneration (optional overrides)
     * @return Updated meal with new recipe
     */
    @PostMapping("/meals/{mealId}/regenerate")
    public ApiSuccess<String> regenerateMeal(JwtAuthenticationToken auth, @PathVariable String mealId,
            @RequestBody(required = false) Map<String, Object> request) {

        var user = userService.ensureUserFromJwt(auth);

        // PHASE 4+: This endpoint is planned but not yet implemented
        // It will use semantic search to find alternative recipes for the meal slot
        logger.info("Regenerate meal {} for user {} - Deferred to Phase 4+", mealId, user.getId());
        return ApiSuccess.of("Meal regeneration is planned for Phase 4+. Use Swap or Replace features for now.");
    }
}
