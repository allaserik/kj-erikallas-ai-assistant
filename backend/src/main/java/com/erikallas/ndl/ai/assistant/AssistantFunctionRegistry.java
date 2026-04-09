package com.erikallas.ndl.ai.assistant;

import com.erikallas.ndl.ai.assistant.functions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registers the four assistant function definitions (sent to OpenAI as the
 * "tools" array) and dispatches tool_call requests to the appropriate handler.
 *
 * Design decisions: - Function names match the OpenAI tool_call "function.name"
 * field exactly. - All parameter extraction happens here, keeping handler
 * classes simple. - userId is always sourced from the authenticated JWT — never
 * from function arguments (prevents context-injection attacks).
 */
@Component
public class AssistantFunctionRegistry {

    private static final Logger log = LoggerFactory.getLogger(AssistantFunctionRegistry.class);

    private final GetHealthMetricsFunction healthMetrics;
    private final GetGoalsFunction goals;
    private final GetMealPlanFunction mealPlan;
    private final GetNutritionSummaryFunction nutritionSummary;
    private final ObjectMapper om;

    public AssistantFunctionRegistry(GetHealthMetricsFunction healthMetrics, GetGoalsFunction goals,
            GetMealPlanFunction mealPlan, GetNutritionSummaryFunction nutritionSummary, ObjectMapper om) {
        this.healthMetrics = healthMetrics;
        this.goals = goals;
        this.mealPlan = mealPlan;
        this.nutritionSummary = nutritionSummary;
        this.om = om;
    }

    /**
     * Returns the JSON array of function definitions to pass to OpenAI.
     */
    public String getFunctionDefinitions() {
        return """
                [
                  {
                    "type": "function",
                    "function": {
                      "name": "get_health_metrics",
                      "description": "Retrieve the user's current health metrics: BMI, weight trend, height, age, gender, wellness score, dietary preferences and restrictions, and baseline activity level. Call this when the user asks about their health stats, weight, BMI, or wellness score.",
                      "parameters": {
                        "type": "object",
                        "properties": {},
                        "required": []
                      }
                    }
                  },
                  {
                    "type": "function",
                    "function": {
                      "name": "get_goals",
                      "description": "Retrieve the user's active health and fitness goals with the latest progress snapshot. Call this when the user asks about their goals, targets, or progress.",
                      "parameters": {
                        "type": "object",
                        "properties": {
                          "include_history": {
                            "type": "boolean",
                            "description": "If true, include the last 5 progress snapshots for each goal. Defaults to false."
                          }
                        },
                        "required": []
                      }
                    }
                  },
                  {
                    "type": "function",
                    "function": {
                      "name": "get_meal_plan",
                      "description": "Retrieve the user's current meal plan for today, tomorrow, or the full week. Returns meal names, types, macros, and estimated calories. Call this when the user asks what they should eat, their meal plan, or a specific day's meals.",
                      "parameters": {
                        "type": "object",
                        "properties": {
                          "period": {
                            "type": "string",
                            "description": "Which part of the meal plan to retrieve.",
                            "enum": ["today", "tomorrow", "week"]
                          }
                        },
                        "required": ["period"]
                      }
                    }
                  },
                  {
                    "type": "function",
                    "function": {
                      "name": "get_nutrition_summary",
                      "description": "Retrieve the user's nutritional intake versus their targets. For 'today', returns calories and all macros (protein, carbs, fats) with percentage of target. For 'week', returns a 7-day rolling calorie trend. Call this when the user asks about calories, macros, nutritional progress, or how well they have been eating.",
                      "parameters": {
                        "type": "object",
                        "properties": {
                          "period": {
                            "type": "string",
                            "description": "Time period to retrieve nutrition data for.",
                            "enum": ["today", "week"]
                          }
                        },
                        "required": ["period"]
                      }
                    }
                  }
                ]
                """;
    }

    /**
     * Dispatches a tool_call to the correct function handler.
     *
     * @param functionName  The OpenAI function name (e.g. "get_health_metrics").
     * @param argumentsJson Raw JSON string of function arguments from OpenAI.
     * @param userId        The authenticated user's ID.
     * @return JSON string result to return to OpenAI as the tool result.
     */
    public String dispatch(String functionName, String argumentsJson, UUID userId) {
        log.debug("Dispatching function_call: function={} userId={}", functionName, userId);
        try {
            JsonNode args = argumentsJson != null && !argumentsJson.isBlank() ? om.readTree(argumentsJson)
                    : om.createObjectNode();

            return switch (functionName) {
            case "get_health_metrics" -> healthMetrics.execute(userId);
            case "get_goals" -> {
                boolean includeHistory = args.path("include_history").asBoolean(false);
                yield goals.execute(userId, includeHistory);
            }
            case "get_meal_plan" -> {
                String period = args.path("period").asText("today");
                yield mealPlan.execute(userId, period);
            }
            case "get_nutrition_summary" -> {
                String period = args.path("period").asText("today");
                yield nutritionSummary.execute(userId, period);
            }
            default -> {
                log.warn("Unknown function requested: {}", functionName);
                yield "{\"error\": \"Unknown function: " + functionName + "\"}";
            }
            };
        } catch (Exception e) {
            log.error("Function dispatch failed: function={} error={}", functionName, e.getMessage(), e);
            return "{\"error\": \"Function execution failed: " + e.getMessage() + "\"}";
        }
    }
}
