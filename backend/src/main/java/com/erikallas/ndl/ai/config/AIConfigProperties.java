package com.erikallas.ndl.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for AI integration (OpenAI, embeddings, etc.)
 * Loaded from application.yaml or application-local.yaml
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AIConfigProperties {
    
    /** OpenAI API key */
    private String openaiApiKey;
    
    /** OpenAI model name (e.g., gpt-4o-mini, gpt-4) */
    private String openaiModel = "gpt-4o-mini";
    
    /** Embedding model (e.g., text-embedding-3-small) */
    private String embeddingModel = "text-embedding-3-small";
    
    /** Temperature for generation (0-2, lower = more deterministic) */
    private Double temperature = 0.7;
    
    /** Max tokens for generation */
    private Integer maxTokens = 2000;
    
    /** Embedding dimension (depends on model) */
    private Integer embeddingDimension = 1536;
    
    /** Number of RAG retrieval results */
    private Integer ragTopK = 5;
    
    /** Similarity threshold for RAG (0-1) */
    private Double ragSimilarityThreshold = 0.6;

    // Getters and Setters
    public String getOpenaiApiKey() {
        return openaiApiKey;
    }

    public void setOpenaiApiKey(String openaiApiKey) {
        this.openaiApiKey = openaiApiKey;
    }

    public String getOpenaiModel() {
        return openaiModel;
    }

    public void setOpenaiModel(String openaiModel) {
        this.openaiModel = openaiModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Integer getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(Integer embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public Integer getRagTopK() {
        return ragTopK;
    }

    public void setRagTopK(Integer ragTopK) {
        this.ragTopK = ragTopK;
    }

    public Double getRagSimilarityThreshold() {
        return ragSimilarityThreshold;
    }

    public void setRagSimilarityThreshold(Double ragSimilarityThreshold) {
        this.ragSimilarityThreshold = ragSimilarityThreshold;
    }
}
