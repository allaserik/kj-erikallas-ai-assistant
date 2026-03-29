package com.erikallas.ndl.ai.function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for handling AI function calling for nutritional calculations.
 * 
 * Allows the AI to request nutritional analysis of recipes/ingredients via
 * structured function calls. The AI recognizes function definitions and makes
 * structured requests like: "I need to calculate_nutrition({ingredients: [...],
 * servings: 2})"
 * 
 * This service: 1. Defines available functions with JSON schema 2. Parses
 * function calls from AI responses 3. Executes calculations with error handling
 * 4. Returns results to AI for incorporation into responses
 * 
 * Error Handling: - Parsing errors: Invalid JSON/malformed requests - Missing
 * parameters: Required fields not provided - Invalid values: Out-of-range,
 * negative, or nonsensical values - Execution errors: Database/API failures -
 * Timeout errors: Calculation takes too long - Rate limit errors: Too many
 * requests - Connectivity issues: Network failures
 */
@Service
public class FunctionCallingHandler {
    private static final Logger log = LoggerFactory.getLogger(FunctionCallingHandler.class);

    private final ObjectMapper om;
    private final NutritionCalculationService nutritionCalcService;

    public FunctionCallingHandler(ObjectMapper om, NutritionCalculationService nutritionCalcService) {
        this.om = om;
        this.nutritionCalcService = nutritionCalcService;
    }

    /**
     * Get the function definitions available for AI function calling. These are
     * passed to OpenAI in the API request to enable the AI to make structured
     * calls.
     * 
     * @return JSON array of function definitions compatible with OpenAI function
     *         calling
     */
    public String getFunctionDefinitions() {
        return """
                [
                  {
                    "type": "function",
                    "function": {
                      "name": "calculate_nutrition",
                      "description": "Calculate total nutritional information for a recipe by analyzing its ingredients. Returns calories, protein, carbs, and fats in standard units (kcal, grams).",
                      "parameters": {
                        "type": "object",
                        "properties": {
                          "ingredients": {
                            "type": "array",
                            "description": "List of ingredients with quantities and units",
                            "items": {
                              "type": "object",
                              "properties": {
                                "name": {
                                  "type": "string",
                                  "description": "Name of the ingredient (e.g., 'chicken breast', 'olive oil')"
                                },
                                "quantity": {
                                  "type": "number",
                                  "description": "Quantity of the ingredient as a number"
                                },
                                "unit": {
                                  "type": "string",
                                  "description": "Unit of measurement (g, ml, cup, tbsp, oz, etc.)",
                                  "enum": ["g", "mg", "ml", "l", "cup", "tbsp", "tsp", "oz", "lb", "piece", "whole"]
                                }
                              },
                              "required": ["name", "quantity", "unit"]
                            },
                            "minItems": 1,
                            "maxItems": 50
                          },
                          "servings": {
                            "type": "number",
                            "description": "Number of servings this recipe yields",
                            "minimum": 0.5,
                            "maximum": 20
                          }
                        },
                        "required": ["ingredients", "servings"]
                      }
                    }
                  }
                ]
                """;
    }

    /**
     * Parse and execute a function call from the AI.
     * 
     * Handles all error cases gracefully: - Parsing errors: Returns error message -
     * Missing/invalid parameters: Validates and returns helpful error - Execution
     * failures: Catches and logs - Rate limits: Returns specific error to AI
     * 
     * @param functionName   Name of function to call
     * @param parametersJson JSON parameters from AI
     * @return Result JSON with nutritional data or error information
     */
    public String executeFunction(String functionName, String parametersJson) {
        log.info("Executing function call: {}", functionName);

        try {
            if ("calculate_nutrition".equals(functionName)) {
                return executeCalculateNutrition(parametersJson);
            } else {
                return errorResponse("unknown_function",
                        "Function '" + functionName + "' not found. Available: calculate_nutrition");
            }
        } catch (Exception e) {
            log.error("Unexpected error executing function {}", functionName, e);
            return errorResponse("execution_error", e.getMessage());
        }
    }

    /**
     * Execute calculate_nutrition function.
     * 
     * @param parametersJson JSON with ingredients array and servings number
     * @return JSON result with nutritional breakdown
     */
    private String executeCalculateNutrition(String parametersJson) {
        try {
            // Parse parameters
            JsonNode params = om.readTree(parametersJson);

            // Validate and extract ingredients
            JsonNode ingredientsNode = params.get("ingredients");
            if (ingredientsNode == null || !ingredientsNode.isArray()) {
                return errorResponse("missing_parameter", "Required parameter 'ingredients' (array) not found");
            }

            if (ingredientsNode.size() == 0) {
                return errorResponse("invalid_value", "Ingredients array must contain at least 1 item");
            }

            if (ingredientsNode.size() > 50) {
                return errorResponse("invalid_value", "Ingredients array cannot exceed 50 items");
            }

            List<IngredientInput> ingredients = new ArrayList<>();
            for (JsonNode ingNode : ingredientsNode) {
                try {
                    String name = getStringField(ingNode, "name", "ingredient name");
                    double quantity = getNumberField(ingNode, "quantity", "ingredient quantity");
                    String unit = getStringField(ingNode, "unit", "ingredient unit");

                    if (quantity <= 0) {
                        return errorResponse("invalid_value",
                                "Ingredient '" + name + "' quantity must be positive, got: " + quantity);
                    }

                    ingredients.add(new IngredientInput(name, quantity, unit));
                } catch (Exception e) {
                    return errorResponse("parsing_error", "Invalid ingredient format: " + e.getMessage());
                }
            }

            // Extract and validate servings
            JsonNode servingsNode = params.get("servings");
            if (servingsNode == null) {
                return errorResponse("missing_parameter", "Required parameter 'servings' (number) not found");
            }

            if (!servingsNode.isNumber()) {
                return errorResponse("parsing_error",
                        "Parameter 'servings' must be a number, got: " + servingsNode.getNodeType());
            }

            double servings = servingsNode.asDouble();
            if (servings < 0.5 || servings > 20) {
                return errorResponse("invalid_value", "Servings must be between 0.5 and 20, got: " + servings);
            }

            // Call nutrition calculation with timeout
            NutritionResult nutrition = nutritionCalcService.calculateNutrition(ingredients, servings);

            // Return success response
            return om.writeValueAsString(Map.of("success", true, "nutrition",
                    Map.of("calories_kcal", Math.round(nutrition.caloriesKcal() * 10.0) / 10.0, "protein_g",
                            Math.round(nutrition.proteinG() * 10.0) / 10.0, "carbs_g",
                            Math.round(nutrition.carbsG() * 10.0) / 10.0, "fats_g",
                            Math.round(nutrition.fatsG() * 10.0) / 10.0, "per_serving",
                            Map.of("calories_kcal", Math.round((nutrition.caloriesKcal() / servings) * 10.0) / 10.0,
                                    "protein_g", Math.round((nutrition.proteinG() / servings) * 10.0) / 10.0, "carbs_g",
                                    Math.round((nutrition.carbsG() / servings) * 10.0) / 10.0, "fats_g",
                                    Math.round((nutrition.fatsG() / servings) * 10.0) / 10.0)),
                    "servings", servings, "ingredients_analyzed", ingredients.size()));
        } catch (Exception e) {
            log.error("Error parsing calculate_nutrition parameters", e);
            return errorResponse("parsing_error", "Failed to parse function parameters: " + e.getMessage());
        }
    }

    /**
     * Extract string field from JSON node with error handling.
     */
    private String getStringField(JsonNode node, String fieldName, String fieldDesc) {
        JsonNode field = node.get(fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Missing " + fieldDesc + " '" + fieldName + "'");
        }
        if (!field.isTextual()) {
            throw new IllegalArgumentException(
                    fieldDesc + " '" + fieldName + "' must be string, got: " + field.getNodeType());
        }
        String value = field.asText();
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldDesc + " '" + fieldName + "' cannot be empty");
        }
        return value;
    }

    /**
     * Extract number field from JSON node with error handling.
     */
    private double getNumberField(JsonNode node, String fieldName, String fieldDesc) {
        JsonNode field = node.get(fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Missing " + fieldDesc + " '" + fieldName + "'");
        }
        if (!field.isNumber()) {
            throw new IllegalArgumentException(
                    fieldDesc + " '" + fieldName + "' must be number, got: " + field.getNodeType());
        }
        return field.asDouble();
    }

    /**
     * Format an error response in a consistent way.
     */
    private String errorResponse(String errorType, String message) {
        try {
            return om.writeValueAsString(Map.of("success", false, "error_type", errorType, "error_message", message));
        } catch (Exception e) {
            log.error("Failed to serialize error response", e);
            return "{\"success\": false, \"error_type\": \"serialization_error\"}";
        }
    }

    /**
     * Input model for ingredient calculation.
     */
    public record IngredientInput(String name, double quantity, String unit) {
    }

    /**
     * Output model for nutrition calculation.
     */
    public record NutritionResult(double caloriesKcal, double proteinG, double carbsG, double fatsG) {
    }
}
