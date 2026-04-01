package com.erikallas.ndl.nutrition.recipe;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class RecipeCatalogModels {

        public record RecipeNutritionSummary(BigDecimal caloriesKcal, BigDecimal proteinG, BigDecimal carbsG,
                        BigDecimal fatsG) {
        }

        public record RecipeListItem(UUID id, String title, String cuisine, String meal, int servings, int timeMinutes,
                        String difficultyLevel, List<String> dietaryTags, RecipeNutritionSummary nutrition) {
        }

        public record RecipeIngredientItem(UUID id, String label, String unit, BigDecimal quantity,
                        RecipeNutritionSummary nutrition) {
        }

        public record RecipePreparationStep(int stepNumber, String step, String description,
                        List<String> ingredientRefs) {
        }

        public record RecipeDetail(UUID id, String title, String cuisine, String meal, int servings, String summary,
                        int timeMinutes, String difficultyLevel, List<String> dietaryTags, String source, String img,
                        List<RecipeIngredientItem> ingredients, List<RecipePreparationStep> preparation,
                        RecipeNutritionSummary nutrition) {
        }

        public record RecipeSearchFilters(String q, String cuisine, String meal, String dietaryTag, String allergyTag,
                        Integer maxTimeMinutes, BigDecimal maxCaloriesKcal, BigDecimal minProteinG,
                        BigDecimal maxProteinG, Integer limit) {
        }

        /** Response for the substitute-ingredient endpoint. */
        public record IngredientSubstitution(String originalIngredientLabel, String substituteLabel, String unit,
                        BigDecimal suggestedQuantity, String rationale) {
        }

        /**
         * A full Recipe detail with ingredient quantities scaled to a different serving
         * count.
         */
        public record ScaledRecipeDetail(UUID id, String title, String cuisine, String meal, int originalServings,
                        int scaledServings, String summary, int timeMinutes, String difficultyLevel,
                        List<String> dietaryTags, String source, String img, List<RecipeIngredientItem> ingredients,
                        List<RecipePreparationStep> preparation, RecipeNutritionSummary nutrition) {
        }

        private RecipeCatalogModels() {
        }
}
