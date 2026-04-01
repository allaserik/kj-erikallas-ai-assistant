package com.erikallas.ndl.ai.rag;

import org.springframework.stereotype.Service;
import com.erikallas.ndl.ai.embedding.VectorEmbeddingService;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogService;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogModels.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieval-Augmented Generation (RAG) service for semantic recipe search.
 * Retrieves relevant recipes for AI meal plan generation.
 * 
 * Phase 1: Minimal working version with basic keyword search. Phase 2: Add
 * vector embeddings for semantic similarity.
 */
@Service
public class RAGRetrieverService {
    private static final Logger log = LoggerFactory.getLogger(RAGRetrieverService.class);

    private final VectorEmbeddingService embeddingService;
    private final RecipeCatalogService recipeCatalogService;

    public RAGRetrieverService(VectorEmbeddingService embeddingService, RecipeCatalogService recipeCatalogService) {
        this.embeddingService = embeddingService;
        this.recipeCatalogService = recipeCatalogService;
    }

    /**
     * Retrieve relevant recipes based on a semantic query. Phase 2: Uses vector
     * embeddings and cosine similarity for ranking.
     * 
     * @param query User query (e.g., "healthy high-protein breakfast")
     * @param topK  Number of results to return
     * @return List of relevant recipes with details, ranked by semantic similarity
     */
    public List<RecipeDetail> retrieveRecipesByQuery(String query, int topK) {
        return retrieveRecipesByQuery(query, null, topK);
    }

    /**
     * Retrieve relevant recipes filtered by meal type, ranked by semantic
     * similarity.
     *
     * @param query    Semantic query describing desired recipe characteristics
     * @param mealType Meal type to filter by in DB (e.g. "breakfast", "lunch",
     *                 "dinner", "snack"). When non-null this is applied as an
     *                 exact-match filter on the {@code meal} column so the pool
     *                 always contains recipes appropriate for that meal slot.
     * @param topK     Number of results to return
     */
    public List<RecipeDetail> retrieveRecipesByQuery(String query, String mealType, int topK) {
        try {
            if (query == null || query.trim().isEmpty()) {
                log.warn("Empty query provided to RAG retriever");
                return new ArrayList<>();
            }

            // Compute embedding for the query
            var queryEmbedding = embeddingService.embed(query);

            // Use mealType as the dedicated meal-column filter; keep keyword query only
            // for title/ingredient matching (not as a substitute for the meal filter).
            String keywordQuery = (mealType != null && query.equalsIgnoreCase(mealType.trim())) ? null : query;

            // Create search filters for initial filtering (keyword + meal type)
            RecipeSearchFilters filters = new RecipeSearchFilters(keywordQuery, null, mealType, null, null, null,
                    Math.min(topK * 3, 100) // Get more results for semantic re-ranking
            );

            // Search recipes using keyword matching
            var results = recipeCatalogService.search(filters);

            // Score recipes by semantic similarity
            List<RecipeWithScore> scoredRecipes = new ArrayList<>();
            for (RecipeListItem item : results) {
                var recipe = recipeCatalogService.getById(item.id());
                if (recipe != null) {
                    // Compute embedding for recipe (title + description)
                    String recipeText = buildRecipeText(recipe);
                    var recipeEmbedding = embeddingService.embed(recipeText);

                    // Score by semantic similarity
                    double similarity = embeddingService.cosineSimilarity(queryEmbedding, recipeEmbedding);
                    scoredRecipes.add(new RecipeWithScore(recipe, similarity));
                }
            }

            // Sort by semantic similarity score (descending) and take top K
            var topRecipes = scoredRecipes.stream().sorted((a, b) -> Double.compare(b.score, a.score)).limit(topK)
                    .map(rs -> rs.recipe).collect(java.util.stream.Collectors.toList());

            log.info("RAG retrieved {} recipes for query '{}' using semantic search", topRecipes.size(), query);
            return topRecipes;
        } catch (Exception e) {
            log.error("Error retrieving recipes by query: " + query, e);
            return new ArrayList<>();
        }
    }

    /**
     * Build text representation of a recipe for embedding.
     */
    private String buildRecipeText(RecipeDetail recipe) {
        StringBuilder text = new StringBuilder();
        text.append(recipe.title()).append(" ");

        if (recipe.meal() != null) {
            text.append(recipe.meal()).append(" ");
        }
        if (recipe.cuisine() != null) {
            text.append(recipe.cuisine()).append(" ");
        }
        if (recipe.difficultyLevel() != null) {
            text.append(recipe.difficultyLevel()).append(" ");
        }

        // Include ingredients for semantic matching
        if (recipe.ingredients() != null && !recipe.ingredients().isEmpty()) {
            text.append("ingredients: ");
            for (RecipeIngredientItem ingredient : recipe.ingredients()) {
                text.append(ingredient.label()).append(" ");
            }
        }

        return text.toString();
    }

    /**
     * Internal class for scoring recipes by semantic similarity.
     */
    private static class RecipeWithScore {
        final RecipeDetail recipe;
        final double score;

        RecipeWithScore(RecipeDetail recipe, double score) {
            this.recipe = recipe;
            this.score = score;
        }
    }

    /**
     * Format recipes as context for LLM consumption.
     * 
     * @param recipes Recipes to format
     * @return Formatted recipe context string
     */
    public String formatAsContext(List<RecipeDetail> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            return "No recipes found in RAG context.";
        }

        StringBuilder context = new StringBuilder();
        context.append("# RAG-RETRIEVED RECIPE CONTEXT\n\n");

        for (RecipeDetail recipe : recipes) {
            context.append("## ").append(recipe.title()).append("\n");
            context.append("- Meal Type: ").append(recipe.meal()).append("\n");
            context.append("- Cuisine: ").append(recipe.cuisine() != null ? recipe.cuisine() : "Mixed").append("\n");
            context.append("- Time: ").append(recipe.timeMinutes()).append(" minutes\n");
            context.append("- Difficulty: ").append(recipe.difficultyLevel()).append("\n");
            context.append("- Servings: ").append(recipe.servings()).append("\n");
            if (recipe.nutrition() != null) {
                context.append("- Calories: ~").append(recipe.nutrition().caloriesKcal()).append(" kcal\n");
                context.append("- Protein: ").append(recipe.nutrition().proteinG()).append("g | ");
                context.append("Carbs: ").append(recipe.nutrition().carbsG()).append("g | ");
                context.append("Fats: ").append(recipe.nutrition().fatsG()).append("g\n");
            }
            context.append("\n");
        }

        return context.toString();
    }
}
