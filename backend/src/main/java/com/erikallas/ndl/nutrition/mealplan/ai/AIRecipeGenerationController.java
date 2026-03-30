package com.erikallas.ndl.nutrition.mealplan.ai;

import com.erikallas.ndl.common.api.dto.ApiSuccess;
import com.erikallas.ndl.auth.user.UserService;
import com.erikallas.ndl.ai.function.FunctionCallingHandler;
import com.erikallas.ndl.ai.rag.RAGRetrieverService;
import com.erikallas.ndl.ai.sequential.SequentialPromptingService;
import com.erikallas.ndl.ai.sequential.SequentialPromptingModels;
import com.erikallas.ndl.ai.sequential.MealPlanOrchestratorService;
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
 * /api/meal-plans/ai/generate - Generate meal plan via sequential prompting
 * (IMPROVED: Now uses MealPlanOrchestratorService for true sequential prompts)
 * 
 * PHASE 4: Generate and save meal plan - POST
 * /api/meal-plans/ai/generate-and-save - Full workflow with database
 * persistence
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
    private final MealPlanOrchestratorService mealPlanOrchestratorService; // NEW: True sequential prompting
    private final MealPlanAIService mealPlanAIService;
    private final RecipeCatalogQueryRepository recipeCatalogQueryRepository;
    private final MealPlanMealRepository mealPlanMealRepository;
    private final ObjectMapper objectMapper;

    public AIRecipeGenerationController(UserService userService, FunctionCallingHandler functionCallingHandler,
            RAGRetrieverService ragRetrieverService, SequentialPromptingService sequentialPromptingService,
            MealPlanOrchestratorService mealPlanOrchestratorService, MealPlanAIService mealPlanAIService,
            RecipeCatalogQueryRepository recipeCatalogQueryRepository, MealPlanMealRepository mealPlanMealRepository,
            ObjectMapper objectMapper) {
        this.userService = userService;
        this.functionCallingHandler = functionCallingHandler;
        this.ragRetrieverService = ragRetrieverService;
        this.sequentialPromptingService = sequentialPromptingService;
        this.mealPlanOrchestratorService = mealPlanOrchestratorService;
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
     * Phase 3: Generate meal plan via TRUE sequential prompting.
     * 
     * IMPROVED: Now uses MealPlanOrchestratorService which implements 3+ distinct
     * AI prompts: - STEP 1 (AI): Analyze user profile → nutrition strategy - STEP 2
     * (AI): Design meal structure - STEP 3 (AI + RAG): Generate recipes with
     * semantic search - STEP 4: Assemble final plan
     * 
     * Each step passes context to the next for true sequential reasoning.
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
            logger.info("Generating meal plan via sequential prompting for user: {}", user.getId());

            // Use MealPlanOrchestratorService for true sequential prompting (NEW)
            var mealPlan = mealPlanOrchestratorService.generateMealPlanSequential(request);

            return ApiSuccess.of(mealPlan);
        } catch (Exception e) {
            logger.error("Meal plan generation failed", e);
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
            logger.info("Generating and saving meal plan via sequential prompting for user: {}", user.getId());

            // Step 1: Generate meal plan via sequential prompting (Phase 3)
            var mealPlan = mealPlanOrchestratorService.generateMealPlanSequential(request);

            // Step 2: Save to database (Phase 4)
            var savedPlan = mealPlanAIService.saveMealPlan(user.getId(), mealPlan);

            // Step 3: Build response with database IDs
            var response = mealPlanAIService.buildGenerateResponse(savedPlan, mealPlan);

            return ApiSuccess.of(response);
        } catch (Exception e) {
            logger.error("Meal plan generation and save failed", e);
            throw new RuntimeException("Meal plan generation failed: " + e.getMessage());
        }
    }
}
