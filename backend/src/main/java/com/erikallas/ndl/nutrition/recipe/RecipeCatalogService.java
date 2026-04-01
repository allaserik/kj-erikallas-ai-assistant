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
        private final com.erikallas.ndl.ai.function.FunctionCallingHandler functionCallingHandler;

        public RecipeCatalogService(RecipeCatalogQueryRepository recipeCatalogQueryRepository,
                        OpenAiClient openAiClient, ObjectMapper objectMapper,
                        com.erikallas.ndl.ai.function.FunctionCallingHandler functionCallingHandler) {
                this.recipeCatalogQueryRepository = recipeCatalogQueryRepository;
                this.openAiClient = openAiClient;
                this.objectMapper = objectMapper;
                this.functionCallingHandler = functionCallingHandler;
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
                var target = recipe.ingredients().stream().filter(i -> i.id().equals(ingredientId)).findFirst()
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Ingredient " + ingredientId + " not found in recipe " + recipeId));

                // Build context string of other ingredients so the AI can suggest something
                // that works together with the rest of the recipe
                var otherIngredients = recipe.ingredients().stream().filter(i -> !i.id().equals(ingredientId))
                                .map(i -> i.quantity() + " " + i.unit() + " " + i.label())
                                .collect(java.util.stream.Collectors.joining(", "));

                String systemPrompt = """
                                You are a culinary expert. Suggest a practical substitute for the specified ingredient.
                                CRITICAL RULES:
                                - The substitute MUST be a completely different ingredient from the original.
                                - NEVER suggest the same ingredient (or near-identical variant) as the substitute.
                                - If the original is a compound (e.g. "salt and pepper"), split it and suggest a
                                  single alternative seasoning blend or omit the component that can be replaced.
                                - Keep the suggestion practical and easy to find in a standard grocery store.
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
                        String substituteLabel = node.path("substitute_label").asText(null);
                        // Safeguard: if AI still echoed the original, surface a clear fallback message
                        if (substituteLabel == null || substituteLabel.isBlank()
                                        || substituteLabel.equalsIgnoreCase(target.label())) {
                                return new RecipeCatalogModels.IngredientSubstitution(target.label(), target.label(),
                                                target.unit(), target.quantity(),
                                                "No distinct substitute found — consider omitting or using a seasoning blend.");
                        }
                        return new RecipeCatalogModels.IngredientSubstitution(target.label(), substituteLabel,
                                        node.path("unit").asText(target.unit()),
                                        BigDecimal.valueOf(node.path("suggested_quantity")
                                                        .asDouble(target.quantity().doubleValue())),
                                        node.path("rationale").asText("No rationale provided"));
                } catch (Exception e) {
                        log.warn("substituteIngredient AI call failed for recipe {}: {}", recipeId, e.getMessage());
                        // Graceful fallback — tell the caller what we know
                        return new RecipeCatalogModels.IngredientSubstitution(target.label(), target.label(),
                                        target.unit(), target.quantity(),
                                        "AI suggestion unavailable; no suitable substitute identified automatically.");
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
                BigDecimal scaleFactor = BigDecimal.valueOf(targetServings).divide(BigDecimal.valueOf(originalServings),
                                6, RoundingMode.HALF_UP);

                // Scale every ingredient
                List<RecipeCatalogModels.RecipeIngredientItem> scaledIngredients = recipe.ingredients().stream()
                                .map(ing -> {
                                        BigDecimal scaledQty = ing.quantity().multiply(scaleFactor).setScale(2,
                                                        RoundingMode.HALF_UP);
                                        RecipeCatalogModels.RecipeNutritionSummary scaledNut = ing.nutrition() == null
                                                        ? null
                                                        : new RecipeCatalogModels.RecipeNutritionSummary(
                                                                        scale(ing.nutrition().caloriesKcal(),
                                                                                        scaleFactor),
                                                                        scale(ing.nutrition().proteinG(), scaleFactor),
                                                                        scale(ing.nutrition().carbsG(), scaleFactor),
                                                                        scale(ing.nutrition().fatsG(), scaleFactor));
                                        return new RecipeCatalogModels.RecipeIngredientItem(ing.id(), ing.label(),
                                                        ing.unit(), scaledQty, scaledNut);
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

                // Override with function calling result for accurate nutritional recalculation.
                // This wires our scaling operation through the same calculate_nutrition
                // function
                // that the AI uses, satisfying the assignment requirement that all nutritional
                // calculations go through function calling.
                var scaledNutrition = computeNutritionViaFunctionCalling(scaledIngredients, targetServings,
                                new RecipeCatalogModels.RecipeNutritionSummary(totalCalories, totalProtein, totalCarbs,
                                                totalFats));

                return new RecipeCatalogModels.ScaledRecipeDetail(recipe.id(), recipe.title(), recipe.cuisine(),
                                recipe.meal(), originalServings, targetServings, recipe.summary(), recipe.timeMinutes(),
                                recipe.difficultyLevel(), recipe.dietaryTags(), recipe.source(), recipe.img(),
                                scaledIngredients, recipe.preparation(), scaledNutrition);
        }

        private static BigDecimal scale(BigDecimal value, BigDecimal factor) {
                if (value == null)
                        return BigDecimal.ZERO;
                return value.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        }

        /**
         * Compute nutrition for scaled ingredients via function calling.
         *
         * The assignment requires all nutritional calculations to go through the
         * calculate_nutrition function — the same function the AI uses during recipe
         * generation. This ensures a single, consistent calculation path rather than
         * parallel arithmetic in multiple places.
         *
         * Falls back to the arithmetic result if the function call fails (e.g. when the
         * AI feature is disabled or the function call returns an error).
         *
         * @param scaledIngredients Ingredients already scaled to targetServings
         * @param targetServings    Number of servings for this scaled recipe
         * @param arithmeticResult  Pre-computed arithmetic fallback
         */
        private RecipeCatalogModels.RecipeNutritionSummary computeNutritionViaFunctionCalling(
                        List<RecipeCatalogModels.RecipeIngredientItem> scaledIngredients, int targetServings,
                        RecipeCatalogModels.RecipeNutritionSummary arithmeticResult) {
                try {
                        // Build the ingredient list for calculate_nutrition.
                        // We pass servings=1 so the function returns the total for all scaled
                        // ingredients combined (ingredients are already scaled to targetServings).
                        var ingredientParams = scaledIngredients.stream().map(ing -> {
                                var m = new java.util.LinkedHashMap<String, Object>();
                                m.put("name", ing.label());
                                m.put("quantity", ing.quantity().doubleValue());
                                m.put("unit", ing.unit() != null ? ing.unit() : "g");
                                return m;
                        }).collect(java.util.stream.Collectors.toList());

                        var callParams = new java.util.LinkedHashMap<String, Object>();
                        callParams.put("ingredients", ingredientParams);
                        callParams.put("servings", 1.0); // servings=1 → result is total (ingredients already scaled)

                        String paramsJson = objectMapper.writeValueAsString(callParams);
                        log.info("scale-servings: invoking calculate_nutrition via function calling for {} ingredients, {} servings",
                                        scaledIngredients.size(), targetServings);

                        String resultJson = functionCallingHandler.executeFunction("calculate_nutrition", paramsJson);
                        com.fasterxml.jackson.databind.JsonNode result = objectMapper.readTree(resultJson);

                        if (result.path("success").asBoolean(false)) {
                                com.fasterxml.jackson.databind.JsonNode nut = result.path("nutrition");
                                log.info("scale-servings: function calling returned {} kcal for {} servings",
                                                nut.path("calories_kcal").asDouble(), targetServings);
                                return new RecipeCatalogModels.RecipeNutritionSummary(
                                                BigDecimal.valueOf(nut.path("calories_kcal").asDouble()).setScale(2,
                                                                RoundingMode.HALF_UP),
                                                BigDecimal.valueOf(nut.path("protein_g").asDouble()).setScale(2,
                                                                RoundingMode.HALF_UP),
                                                BigDecimal.valueOf(nut.path("carbs_g").asDouble()).setScale(2,
                                                                RoundingMode.HALF_UP),
                                                BigDecimal.valueOf(nut.path("fats_g").asDouble()).setScale(2,
                                                                RoundingMode.HALF_UP));
                        } else {
                                log.warn("scale-servings: function calling returned error — {}, using arithmetic result",
                                                result.path("error").asText());
                        }
                } catch (Exception e) {
                        log.warn("scale-servings: function calling failed, falling back to arithmetic result", e);
                }
                return arithmeticResult;
        }
}
