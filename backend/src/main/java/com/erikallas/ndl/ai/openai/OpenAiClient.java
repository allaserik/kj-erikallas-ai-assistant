package com.erikallas.ndl.ai.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiClient {

    private final RestClient rest;
    private final ObjectMapper om;
    private final Environment env;

    public OpenAiClient(ObjectMapper om, Environment env) {
        this.om = om;
        this.env = env;
        this.rest = RestClient.builder().baseUrl("https://api.openai.com/v1").build();
    }

    public String model() {
        return env.getProperty("OPENAI_MODEL", "gpt-4o-mini");
    }

    public boolean hasKey() {
        String key = env.getProperty("OPENAI_API_KEY");
        return key != null && !key.isBlank();
    }

    public String generateInsightJson(String systemPrompt, String userPrompt) {
        return generateJsonResponse(systemPrompt, userPrompt, null);
    }

    /**
     * Generate structured JSON response from OpenAI using json_schema mode.
     * Supports optional custom schema for flexible output formats.
     */
    public String generateJsonResponse(String systemPrompt, String userPrompt, Map<String, Object> customSchema) {
        String key = env.getProperty("OPENAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not set");
        }

        // Use custom schema if provided, otherwise use default insight schema
        Map<String, Object> schema = customSchema != null ? customSchema
                : Map.of("type", "object", "additionalProperties", false, "properties",
                        Map.of("recommendations",
                                Map.of("type", "array", "minItems", 3, "maxItems", 3, "items",
                                        Map.of("type", "string", "maxLength", 220)),
                                "reflection_question", Map.of("type", "string", "maxLength", 220), "summary",
                                Map.of("type", "string", "maxLength", 400)),
                        "required", java.util.List.of("recommendations", "reflection_question", "summary"));

        Map<String, Object> responseFormat = Map.of("type", "json_schema", "json_schema",
                Map.of("name", "structured_response", "strict", true, "schema", schema));

        Map<String, Object> body = Map.of("model", model(), "messages",
                java.util.List.of(Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "response_format", responseFormat, "temperature", 0.7);

        String raw = rest.post().uri("/chat/completions").header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);

        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("OpenAI response body was empty");
        }

        JsonNode resp;
        try {
            resp = om.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse OpenAI response JSON", e);
        }

        // Extract JSON from first choice's content
        JsonNode choices = resp.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode firstChoice = choices.get(0);
            JsonNode content = firstChoice.path("message").path("content");
            if (!content.isMissingNode()) {
                return content.asText();
            }
        }

        throw new IllegalStateException("OpenAI response missing valid content in choices");
    }
}
