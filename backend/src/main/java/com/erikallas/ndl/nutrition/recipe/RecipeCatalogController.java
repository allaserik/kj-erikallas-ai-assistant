package com.erikallas.ndl.nutrition.recipe;

import com.erikallas.ndl.auth.user.UserService;
import com.erikallas.ndl.common.api.dto.ApiSuccess;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirement(name = "bearerAuth")
@RestController
public class RecipeCatalogController {

    private final UserService userService;
    private final RecipeCatalogService recipeCatalogService;

    public RecipeCatalogController(UserService userService, RecipeCatalogService recipeCatalogService) {
        this.userService = userService;
        this.recipeCatalogService = recipeCatalogService;
    }

    @GetMapping("/api/recipes/search")
    public ApiSuccess<List<RecipeCatalogModels.RecipeListItem>> search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "cuisine", required = false) String cuisine,
            @RequestParam(name = "meal", required = false) String meal,
            @RequestParam(name = "dietary_tag", required = false) String dietaryTag,
            @RequestParam(name = "max_time_minutes", required = false) Integer maxTimeMinutes,
            @RequestParam(name = "max_calories_kcal", required = false) BigDecimal maxCaloriesKcal,
            @RequestParam(name = "limit", required = false) Integer limit, JwtAuthenticationToken auth) {
        userService.ensureUserFromJwt(auth);
        return ApiSuccess.of(recipeCatalogService.search(new RecipeCatalogModels.RecipeSearchFilters(q, cuisine, meal,
                dietaryTag, maxTimeMinutes, maxCaloriesKcal, limit)));
    }

    @GetMapping("/api/recipes/{recipeId}")
    public ApiSuccess<RecipeCatalogModels.RecipeDetail> getById(@PathVariable("recipeId") UUID recipeId,
            JwtAuthenticationToken auth) {
        userService.ensureUserFromJwt(auth);
        return ApiSuccess.of(recipeCatalogService.getById(recipeId));
    }

    /**
     * POST /api/recipes/{recipeId}/substitute-ingredient
     *
     * Ask the AI to suggest a substitute for a specific ingredient within a recipe.
     * Useful when an ingredient is unavailable or the user has a dietary
     * restriction.
     *
     * Request body: { "ingredient_id": "uuid", "reason": "optional free-text
     * reason" }
     */
    @PostMapping("/api/recipes/{recipeId}/substitute-ingredient")
    public ApiSuccess<RecipeCatalogModels.IngredientSubstitution> substituteIngredient(@PathVariable UUID recipeId,
            @RequestBody SubstituteIngredientRequest request, JwtAuthenticationToken auth) {
        userService.ensureUserFromJwt(auth);
        var result = recipeCatalogService.substituteIngredient(recipeId, request.ingredientId(), request.reason());
        return ApiSuccess.of(result);
    }

    /**
     * POST /api/recipes/{recipeId}/scale-servings
     *
     * Return a version of the recipe scaled to the requested number of servings.
     * All ingredient quantities and total nutrition values are recalculated
     * proportionally. No AI call is made — this is a deterministic operation.
     *
     * Request body: { "target_servings": 4 }
     */
    @PostMapping("/api/recipes/{recipeId}/scale-servings")
    public ApiSuccess<RecipeCatalogModels.ScaledRecipeDetail> scaleServings(@PathVariable UUID recipeId,
            @RequestBody ScaleServingsRequest request, JwtAuthenticationToken auth) {
        userService.ensureUserFromJwt(auth);
        var result = recipeCatalogService.scaleServings(recipeId, request.targetServings());
        return ApiSuccess.of(result);
    }

    // ===== Request DTOs =====

    public record SubstituteIngredientRequest(@JsonProperty("ingredient_id") UUID ingredientId,
            @JsonProperty("reason") String reason) {
    }

    public record ScaleServingsRequest(@JsonProperty("target_servings") int targetServings) {
    }
}
