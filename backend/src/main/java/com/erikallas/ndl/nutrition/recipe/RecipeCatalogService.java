package com.erikallas.ndl.nutrition.recipe;

import com.erikallas.ndl.ai.openai.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RecipeCatalogService {

    private static final Logger log = LoggerFactory.getLogger(RecipeCatalogService.class);

    private final RecipeCatalogQueryRepository recipeCatalogQueryRepository;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public RecipeCatalogService(RecipeCatalogQueryRepository recipeCatalogQueryRepository, OpenAiClient openAiClient,
            ObjectMapper objectMapper) {
        this.recipeCatalogQueryRepository = recipeCatalogQueryRepository;
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    public List<RecipeCatalogModels.RecipeListItem> search(RecipeCatalogModels.RecipeSearchFilters filters) {
        return recipeCatalogQueryRepository.search(filters);
    }

    public RecipeCatalogModels.RecipeDetail getById(UUID recipeId) {
        return recipeCatalogQueryRepository.getById(recipeId);
    }

    /**
     * AI-powered ingredient substitution.
     *
     * Asks OpenAI to suggest a substitute for the specified ingredient in the
     * context of this recipe, taking into account the user-supplied reason and any
     * other ingredients present.
     *
     * @param recipeId     Recipe the ingredient belongs to
     * @param ingredientId Ingredient to replace
     * @param reason       Optional reason (e.g. "I'm out of butter", "I'm vegan")
     * @return Substitution suggestion with rationale
     */
    public RecipeCatalogModels.IngredientSubstitution substituteIngredient(UUID recipeId, UUID ingredientId,
            String reason) {
        var recipe = recipeCatalogQueryRepository.getById(recipeId);
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe not found: " + recipeId);
        }

        // Find the target ingredient
        var target = recipe.ingredients().stream().filter(i -> i.id().equals(ingredientId)).findFirst().orElseThrow(
                () -> new IllegalArgumentException("Ingredient " + ingredientId + " not found in recipe " + recipeId));

        // Build context string of other ingredients so the AI can suggest something
        // that works together with the rest of the recipe
        var otherIngredients = recipe.ingredients().stream().filter(i -> !i.id().equals(ingredientId))
                .map(i -> i.quantity() + " " + i.unit() + " " + i.label())
                .collect(java.util.stream.Collectors.joining(", "));

        String systemPrompt = """
                You are a culinary expert. Suggest a practical ingredient substitution.
                Respond ONLY with valid JSON matching this exact structure:
                {
                  "substitute_label": "name of the substitute ingredient",
                  "unit": "same unit as original or adjusted unit",
                  "suggested_quantity": number,
                  "rationale": "brief explanation why this works (max 150 chars)"
                }
                """;

        String userPrompt = String.format("""
                Recipe: %s
                Ingredient to replace: %s %s %s
                Other ingredients: %s
                Reason for substitution: %s

                Suggest the best single substitute ingredient.
                """, recipe.title(), target.quantity(), target.unit(), target.label(),
                otherIngredients.isEmpty() ? "none" : otherIngredients,
                reason != null && !reason.isBlank() ? reason : "general preference");

        try {
            // temperature=0.5 for focused culinary advice, top_p=0.9 for some variety
            String json = openAiClient.generateInsightJson(systemPrompt, userPrompt, 0.5, 0.9);
            JsonNode node = objectMapper.readTree(json);
            return new RecipeCatalogModels.IngredientSubstitution(target.label(),
                    node.path("substitute_label").asText(target.label()), node.path("unit").asText(target.unit()),
                    BigDecimal.valueOf(node.path("suggested_quantity").asDouble(target.quantity().doubleValue())),
                    node.path("rationale").asText("No rationale provided"));
        } catch (Exception e) {
            log.warn("substituteIngredient AI call failed for recipe {}: {}", recipeId, e.getMessage());
            // Graceful fallback — tell the caller what we know
            return new RecipeCatalogModels.IngredientSubstitution(target.label(), target.label(), target.unit(),
                    target.quantity(), "AI suggestion unavailable; no suitable substitute identified automatically.");
        }
    }

    /**
     * Scale a recipe to a different number of servings.
     *
     * All ingredient quantities are multiplied by {@code targetServings /
     * recipe.servings}. The total nutrition is recalculated from the scaled
     * ingredients. This is purely deterministic — no AI call required.
     *
     * @param recipeId       The recipe to scale
     * @param targetServings Desired serving count (must be ≥ 1)
     * @return A new {@link RecipeCatalogModels.ScaledRecipeDetail} with updated
     *         quantities and nutrition
     */
    public RecipeCatalogModels.ScaledRecipeDetail scaleServings(UUID recipeId, int targetServings) {
        if (targetServings < 1) {
            throw new IllegalArgumentException("targetServings must be at least 1");
        }

        var recipe = recipeCatalogQueryRepository.getById(recipeId);
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe not found: " + recipeId);
        }

        int originalServings = recipe.servings() < 1 ? 1 : recipe.servings();
        BigDecimal scaleFactor = BigDecimal.valueOf(targetServings).divide(BigDecimal.valueOf(originalServings), 6,
                RoundingMode.HALF_UP);

        // Scale every ingredient
        List<RecipeCatalogModels.RecipeIngredientItem> scaledIngredients = recipe.ingredients().stream().map(ing -> {
            BigDecimal scaledQty = ing.quantity().multiply(scaleFactor).setScale(2, RoundingMode.HALF_UP);
            RecipeCatalogModels.RecipeNutritionSummary scaledNut = ing.nutrition() == null ? null
                    : new RecipeCatalogModels.RecipeNutritionSummary(scale(ing.nutrition().caloriesKcal(), scaleFactor),
                            scale(ing.nutrition().proteinG(), scaleFactor),
                            scale(ing.nutrition().carbsG(), scaleFactor), scale(ing.nutrition().fatsG(), scaleFactor));
            return new RecipeCatalogModels.RecipeIngredientItem(ing.id(), ing.label(), ing.unit(), scaledQty,
                    scaledNut);
        }).collect(java.util.stream.Collectors.toList());

        // Aggregate total nutrition from scaled ingredients
        BigDecimal totalCalories = scaledIngredients.stream().filter(i -> i.nutrition() != null)
                .map(i -> i.nutrition().caloriesKcal()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalProtein = scaledIngredients.stream().filter(i -> i.nutrition() != null)
                .map(i -> i.nutrition().proteinG()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCarbs = scaledIngredients.stream().filter(i -> i.nutrition() != null)
                .map(i -> i.nutrition().carbsG()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFats = scaledIngredients.stream().filter(i -> i.nutrition() != null)
                .map(i -> i.nutrition().fatsG()).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Fall back to pre-computed recipe nutrition if no ingredient nutrition
        // available
        if (totalCalories.compareTo(BigDecimal.ZERO) == 0 && recipe.nutrition() != null) {
            totalCalories = scale(recipe.nutrition().caloriesKcal(), scaleFactor);
            totalProtein = scale(recipe.nutrition().proteinG(), scaleFactor);
            totalCarbs = scale(recipe.nutrition().carbsG(), scaleFactor);
            totalFats = scale(recipe.nutrition().fatsG(), scaleFactor);
        }

        var scaledNutrition = new RecipeCatalogModels.RecipeNutritionSummary(totalCalories, totalProtein, totalCarbs,
                totalFats);

        return new RecipeCatalogModels.ScaledRecipeDetail(recipe.id(), recipe.title(), recipe.cuisine(), recipe.meal(),
                originalServings, targetServings, recipe.summary(), recipe.timeMinutes(), recipe.difficultyLevel(),
                recipe.dietaryTags(), recipe.source(), recipe.img(), scaledIngredients, recipe.preparation(),
                scaledNutrition);
    }

    private static BigDecimal scale(BigDecimal value, BigDecimal factor) {
        if (value == null)
            return BigDecimal.ZERO;
        return value.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
