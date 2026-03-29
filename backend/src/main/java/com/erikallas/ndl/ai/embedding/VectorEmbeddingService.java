package com.erikallas.ndl.ai.embedding;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for computing vector embeddings. Used for semantic search and RAG
 * retrieval.
 * 
 * Phase 2: Lightweight word vector embeddings based on cooking vocabulary. Uses
 * TF-based vectors computed from recipe titles/descriptions. Phase 3: Will
 * integrate with Spring AI for OpenAI embeddings.
 */
@Service
public class VectorEmbeddingService {

    private static final int EMBEDDING_DIM = 100;

    // Cooking/nutrition vocabulary for semantic understanding
    private static final Set<String> VOCABULARY = Set.of("healthy", "light", "quick", "easy", "spicy", "sweet",
            "savory", "protein", "breakfast", "lunch", "dinner", "dessert", "snack", "appetizer", "main", "chicken",
            "beef", "pork", "fish", "salmon", "shrimp", "tofu", "vegetable", "salad", "soup", "pasta", "rice", "bread",
            "sandwich", "burger", "pizza", "thai", "italian", "mexican", "asian", "mediterranean", "indian", "american",
            "low-carb", "keto", "vegan", "vegetarian", "gluten-free", "dairy-free", "calorie", "nutrition", "diet",
            "weight", "fit", "exercise", "energy", "fruit", "grain", "olive", "garlic", "herb", "spice",
            "sauce", "baked", "grilled", "fried", "steamed", "roasted", "boiled", "raw", "simple", "complex",
            "traditional", "modern", "fusion", "comfort", "elegant", "appetizing", "delicious", "tasty", "flavorful",
            "rich", "fresh", "seasonal", "organic", "local", "homemade", "restaurant", "gourmet");

    /**
     * Compute embedding for a single text string. Uses word frequency in cooking
     * vocabulary as vector components.
     */
    public List<Double> embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return createZeroVector();
        }

        String[] words = text.toLowerCase().split("\\W+");
        Map<String, Integer> wordFreq = new HashMap<>();

        for (String word : words) {
            if (isRelevantWord(word)) {
                wordFreq.merge(word, 1, Integer::sum);
            }
        }

        return vectorizeWordFrequencies(wordFreq);
    }

    /**
     * Compute embeddings for multiple text strings.
     */
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        return texts.stream().map(this::embed).collect(Collectors.toList());
    }

    /**
     * Compute cosine similarity between two vectors.
     */
    public double cosineSimilarity(List<Double> vector1, List<Double> vector2) {
        if (vector1 == null || vector2 == null || vector1.isEmpty() || vector2.isEmpty()) {
            return 0.0;
        }

        if (vector1.size() != vector2.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double magnitude1 = 0.0;
        double magnitude2 = 0.0;

        for (int i = 0; i < vector1.size(); i++) {
            double v1 = vector1.get(i);
            double v2 = vector2.get(i);

            dotProduct += v1 * v2;
            magnitude1 += v1 * v1;
            magnitude2 += v2 * v2;
        }

        double magnitude = Math.sqrt(magnitude1) * Math.sqrt(magnitude2);
        return magnitude == 0 ? 0.0 : dotProduct / magnitude;
    }

    /**
     * Check if word is relevant for cooking semantics.
     */
    private boolean isRelevantWord(String word) {
        return word.length() > 2 && (VOCABULARY.contains(word) || word.length() >= 6); // Keep longer words for
                                                                                       // ingredient names
    }

    /**
     * Convert word frequencies to fixed-size vector using vocabulary hash. Each
     * vocabulary word maps to a position in the vector.
     */
    private List<Double> vectorizeWordFrequencies(Map<String, Integer> wordFreq) {
        List<Double> vector = createZeroVector();

        // Hash-based placement: map words to vector indices
        for (Map.Entry<String, Integer> entry : wordFreq.entrySet()) {
            int index = Math.abs(entry.getKey().hashCode()) % EMBEDDING_DIM;
            double value = vector.get(index) + (entry.getValue() * 0.5); // Frequency-based
            vector.set(index, Math.min(value, 1.0)); // Normalize to 0-1
        }

        // Normalize vector magnitude
        return normalizeVector(vector);
    }

    /**
     * Normalize vector to unit length.
     */
    private List<Double> normalizeVector(List<Double> vector) {
        double magnitude = 0.0;
        for (double v : vector) {
            magnitude += v * v;
        }
        magnitude = Math.sqrt(magnitude);

        if (magnitude == 0) {
            return vector;
        }

        final double finalMag = magnitude;
        return vector.stream().map(v -> v / finalMag).collect(Collectors.toList());
    }

    /**
     * Create zero vector of embedding dimension.
     */
    private List<Double> createZeroVector() {
        return new ArrayList<>(Collections.nCopies(EMBEDDING_DIM, 0.0));
    }
}
