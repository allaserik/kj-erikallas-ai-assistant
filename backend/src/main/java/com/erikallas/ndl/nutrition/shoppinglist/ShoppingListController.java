package com.erikallas.ndl.nutrition.shoppinglist;

import com.erikallas.ndl.auth.user.UserService;
import com.erikallas.ndl.common.api.dto.ApiSuccess;
import com.erikallas.ndl.nutrition.mealplan.MealPlanMealRepository;
import com.erikallas.ndl.nutrition.mealplan.MealPlanQueryService;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogQueryRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for shopping list generation and management.
 * 
 * Supports generating shopping lists from: - Meal plans or date ranges -
 * Individual meals - Custom ingredient lists
 */
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/nutrition/shopping-list")
public class ShoppingListController {

    private static final Logger logger = LoggerFactory.getLogger(ShoppingListController.class);

    private final UserService userService;
    private final MealPlanMealRepository mealPlanMealRepository;
    private final MealPlanQueryService mealPlanQueryService;
    private final RecipeCatalogQueryRepository recipeRepository;
    private final ShoppingListService shoppingListService;

    public ShoppingListController(UserService userService, MealPlanMealRepository mealPlanMealRepository,
            MealPlanQueryService mealPlanQueryService, RecipeCatalogQueryRepository recipeRepository,
            ShoppingListService shoppingListService) {
        this.userService = userService;
        this.mealPlanMealRepository = mealPlanMealRepository;
        this.mealPlanQueryService = mealPlanQueryService;
        this.recipeRepository = recipeRepository;
        this.shoppingListService = shoppingListService;
    }

    /**
     * POST /api/nutrition/shopping-list/from-meals
     * 
     * Generate shopping list from a list of meal IDs.
     */
    @PostMapping("/from-meals")
    public ApiSuccess<ShoppingListResponse> generateFromMeals(@RequestBody GenerateFromMealsRequest request,
            JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);

        List<ShoppingListService.RecipeIngredientInfo> allIngredients = new ArrayList<>();

        for (UUID mealId : request.mealIds()) {
            var meal = mealPlanMealRepository.findById(mealId)
                    .orElseThrow(() -> new IllegalArgumentException("Meal not found: " + mealId));

            // Fetch recipe and extract ingredients
            var recipe = recipeRepository.getById(meal.getRecipeId());
            if (recipe != null && recipe.ingredients() != null) {
                for (var ingredient : recipe.ingredients()) {
                    allIngredients.add(new ShoppingListService.RecipeIngredientInfo(
                            ingredient.label(),
                            ingredient.unit(),
                            ingredient.quantity()
                    ));
                }
                logger.debug("Added {} ingredients from recipe: {}", recipe.ingredients().size(), recipe.title());
            }
        }

        var list = shoppingListService.generateFromRecipes(allIngredients);
        logger.info("Generated shopping list for {} meals", request.mealIds().size());

        return ApiSuccess.of(new ShoppingListResponse(list.getItemCount(), list.getActiveItems().stream()
                .map(item -> new ShoppingListItemResponse(item.id(), item.label(), item.unit(),
                        item.quantity().doubleValue(), item.category().getDisplayName(), item.excluded(), item.notes()))
                .toList(),
                list.byCategory().entrySet().stream().filter(e -> !e.getValue().isEmpty())
                        .collect(java.util.LinkedHashMap::new, (m, e) -> m.put(e.getKey().getDisplayName(),
                                e.getValue().stream().filter(item -> !item.excluded())
                                        .map(item -> new ShoppingListItemResponse(item.id(), item.label(), item.unit(),
                                                item.quantity().doubleValue(), item.category().getDisplayName(),
                                                item.excluded(), item.notes()))
                                        .toList()),
                                java.util.LinkedHashMap::putAll)));
    }

    /**
     * POST /api/nutrition/shopping-list/from-date-range
     * 
     * Generate shopping list from all meals in a date range.
     */
    @PostMapping("/from-date-range")
    public ApiSuccess<ShoppingListResponse> generateFromDateRange(@RequestBody GenerateFromDateRangeRequest request,
            JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);

        // Get the latest meal plan for this user
        var latestPlan = mealPlanQueryService.getLatest(user.getId());
        if (latestPlan == null) {
            logger.warn("No meal plan found for user {}", user.getId());
            return ApiSuccess.of(new ShoppingListResponse(0, new ArrayList<>(), new LinkedHashMap<>()));
        }

        List<ShoppingListService.RecipeIngredientInfo> allIngredients = new ArrayList<>();
        
        // Find all meals in the date range
        for (var day : latestPlan.days()) {
            LocalDate dayDate = day.date();
            
            // Check if this day is within the requested range
            if (!dayDate.isBefore(request.startDate()) && !dayDate.isAfter(request.endDate())) {
                for (var meal : day.meals()) {
                    // Extract ingredients from each recipe
                    var recipe = recipeRepository.getById(meal.recipeId());
                    if (recipe != null && recipe.ingredients() != null) {
                        for (var ingredient : recipe.ingredients()) {
                            allIngredients.add(new ShoppingListService.RecipeIngredientInfo(
                                    ingredient.label(),
                                    ingredient.unit(),
                                    ingredient.quantity()
                            ));
                        }
                        logger.debug("Added {} ingredients from recipe: {} for date: {}", 
                            recipe.ingredients().size(), recipe.title(), dayDate);
                    }
                }
            }
        }

        var list = shoppingListService.generateFromRecipes(allIngredients);
        logger.info("Generated shopping list for date range {} to {} with {} meals", 
            request.startDate(), request.endDate(), allIngredients.size());

        return ApiSuccess.of(new ShoppingListResponse(list.getItemCount(), list.getActiveItems().stream()
                .map(item -> new ShoppingListItemResponse(item.id(), item.label(), item.unit(),
                        item.quantity().doubleValue(), item.category().getDisplayName(), item.excluded(), item.notes()))
                .toList(),
                list.byCategory().entrySet().stream().filter(e -> !e.getValue().isEmpty())
                        .collect(java.util.LinkedHashMap::new, (m, e) -> m.put(e.getKey().getDisplayName(),
                                e.getValue().stream().filter(item -> !item.excluded())
                                        .map(item -> new ShoppingListItemResponse(item.id(), item.label(), item.unit(),
                                                item.quantity().doubleValue(), item.category().getDisplayName(),
                                                item.excluded(), item.notes()))
                                        .toList()),
                                java.util.LinkedHashMap::putAll)));
    }

    /**
     * POST /api/nutrition/shopping-list/adjust-quantity
     * 
     * Adjust quantity of an item in the shopping list.
     * 
     * Note: Shopping lists are generated on-the-fly, not persisted. This endpoint
     * would be used when reconstructing a list client-side.
     */
    @PostMapping("/adjust-quantity")
    public ApiSuccess<String> adjustQuantity(@RequestBody AdjustQuantityRequest request) {
        logger.debug("Adjusting quantity for item {}: {} (new quantity)", request.itemId(), request.newQuantity());

        return ApiSuccess.of("Quantity adjusted successfully");
    }

    /**
     * POST /api/nutrition/shopping-list/exclude-item
     * 
     * Exclude an item from the shopping list.
     */
    @PostMapping("/exclude-item")
    public ApiSuccess<String> excludeItem(@RequestBody ExcludeItemRequest request) {
        logger.debug("Excluding item {}: {}", request.itemId(), request.notes());

        return ApiSuccess.of("Item excluded successfully");
    }

    // ===== Request/Response DTOs =====

    public record GenerateFromMealsRequest(List<UUID> mealIds) {
    }

    public record GenerateFromDateRangeRequest(LocalDate startDate, LocalDate endDate) {
    }

    public record AdjustQuantityRequest(String itemId, double newQuantity) {
    }

    public record ExcludeItemRequest(String itemId, String notes) {
    }

    public record ShoppingListItemResponse(String id, String label, String unit, double quantity, String category,
            boolean excluded, String notes) {
    }

    public record ShoppingListResponse(int itemCount, List<ShoppingListItemResponse> allItems,
            Map<String, List<ShoppingListItemResponse>> categorized) {
    }
}
