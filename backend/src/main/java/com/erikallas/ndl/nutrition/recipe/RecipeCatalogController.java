package com.erikallas.ndl.nutrition.recipe;

import com.erikallas.ndl.auth.user.UserService;
import com.erikallas.ndl.common.api.dto.ApiSuccess;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            @RequestParam(name = "limit", required = false) Integer limit,
            JwtAuthenticationToken auth) {
        userService.ensureUserFromJwt(auth);
        return ApiSuccess.of(recipeCatalogService.search(new RecipeCatalogModels.RecipeSearchFilters(
                q,
                cuisine,
                meal,
                dietaryTag,
                maxTimeMinutes,
                maxCaloriesKcal,
                limit)));
    }

    @GetMapping("/api/recipes/{recipeId}")
    public ApiSuccess<RecipeCatalogModels.RecipeDetail> getById(
            @PathVariable("recipeId") UUID recipeId,
            JwtAuthenticationToken auth) {
        userService.ensureUserFromJwt(auth);
        return ApiSuccess.of(recipeCatalogService.getById(recipeId));
    }
}
