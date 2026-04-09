package com.erikallas.ndl.ai.assistant;

import com.erikallas.ndl.ai.assistant.AssistantModels.ChatRequest;
import com.erikallas.ndl.ai.assistant.AssistantModels.ChatResponse;
import com.erikallas.ndl.ai.assistant.AssistantModels.ConversationMessage;
import com.erikallas.ndl.ai.openai.OpenAiClient;
import com.erikallas.ndl.ai.openai.OpenAiClient.AIAssistantResponse;
import com.erikallas.ndl.ai.openai.OpenAiClient.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the conversational AI assistant.
 *
 * Chat flow: 1. Load conversation history (context window). 2. Build the OpenAI
 * system prompt with user personalisation. 3. Call OpenAI with function-calling
 * enabled. 4. If tool_calls are returned, dispatch each function, append
 * results, and call OpenAI a second time to produce the final natural-language
 * reply. 5. Persist the user message and assistant reply. 6. Return the
 * ChatResponse to the controller.
 *
 * Security notes: - The user's email is NEVER forwarded to the AI; only the
 * derived display name (email prefix) is passed to the system prompt. Function
 * handlers never receive the email. - userId is always sourced from the
 * authenticated JWT — never from any user-provided parameter. - If OpenAI is
 * unavailable (no API key), a graceful error is returned instead of a stack
 * trace.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final AssistantContextWindowService contextWindow;
    private final AssistantConversationRepository repo;
    private final AssistantSystemPromptBuilder promptBuilder;
    private final AssistantFunctionRegistry registry;
    private final OpenAiClient openAiClient;
    private final ObjectMapper om;

    public AssistantService(AssistantContextWindowService contextWindow, AssistantConversationRepository repo,
            AssistantSystemPromptBuilder promptBuilder, AssistantFunctionRegistry registry, OpenAiClient openAiClient,
            ObjectMapper om) {
        this.contextWindow = contextWindow;
        this.repo = repo;
        this.promptBuilder = promptBuilder;
        this.registry = registry;
        this.openAiClient = openAiClient;
        this.om = om;
    }

    /**
     * Process a chat turn for the authenticated user.
     *
     * @param userId    Authenticated user's ID (from JWT).
     * @param userEmail Authenticated user's email (used ONLY to derive a display
     *                  name for the system prompt, never forwarded to the AI
     *                  verbatim).
     * @param request   The validated chat request.
     * @return ChatResponse with the assistant's reply.
     */
    @Transactional
    public ChatResponse chat(UUID userId, String userEmail, ChatRequest request) {
        if (!openAiClient.hasKey()) {
            log.warn("OPENAI_API_KEY not set — cannot process assistant chat for userId={}", userId);
            String fallback = "I'm not available right now because the AI service is not configured. "
                    + "Please ask your administrator to set the OPENAI_API_KEY environment variable.";
            contextWindow.save(userId, "user", request.message());
            contextWindow.save(userId, "assistant", fallback);
            return new ChatResponse(fallback, null, OffsetDateTime.now(), null);
        }

        String displayName = extractDisplayName(userEmail);
        String systemPrompt = promptBuilder.build(displayName, request.mode());
        String funcDefs = registry.getFunctionDefinitions();

        // Summarise history if over token budget (topic-aware compression)
        contextWindow.maybeSummariseHistory(userId);

        // Load existing history (user msg not yet persisted)
        String historyJson = contextWindow.buildMessagesJson(userId, null);

        // Append current user message to the messages array
        String fullMessagesJson = appendUserMessage(historyJson, request.message());

        String finalReply;
        String functionCalled = null;
        AssistantModels.ChartData chartData = null;

        try {
            // --- Round 1: possible tool_calls ---
            String rawResponse = openAiClient.generateWithFunctionCalling(systemPrompt, fullMessagesJson, funcDefs);
            AIAssistantResponse parsed = openAiClient.parseAssistantResponse(rawResponse);

            if (!parsed.toolCalls().isEmpty()) {
                // Build round-2 messages: history + user msg + assistant tool-call msg + tool
                // results
                ArrayNode round2Array = (ArrayNode) om.readTree(fullMessagesJson);

                // Append the assistant's tool-call message as returned by OpenAI
                JsonNode assistantToolCallMsg = om.readTree(rawResponse).path("choices").get(0).path("message");
                round2Array.add(assistantToolCallMsg);

                // Dispatch each tool and append results
                for (ToolCall tc : parsed.toolCalls()) {
                    String result = registry.dispatch(tc.function(), tc.arguments(), userId);
                    log.debug("Function dispatched: function={} userId={}", tc.function(), userId);

                    // Extract _chart_data before sending to AI (saves tokens)
                    String resultForAI = result;
                    try {
                        JsonNode resultNode = om.readTree(result);
                        if (resultNode.isObject() && resultNode.has("_chart_data")) {
                            JsonNode chartNode = resultNode.path("_chart_data");
                            chartData = om.treeToValue(chartNode, AssistantModels.ChartData.class);
                            ((ObjectNode) resultNode).remove("_chart_data");
                            resultForAI = om.writeValueAsString(resultNode);
                        }
                    } catch (Exception ex) {
                        log.warn("Could not extract _chart_data from function result: {}", ex.getMessage());
                    }

                    ObjectNode toolResult = om.createObjectNode();
                    toolResult.put("role", "tool");
                    toolResult.put("tool_call_id", tc.id());
                    toolResult.put("content", resultForAI);
                    round2Array.add(toolResult);
                    functionCalled = tc.function();
                }

                // --- Round 2: final natural-language reply ---
                String round2Json = om.writeValueAsString(round2Array);
                String rawResponse2 = openAiClient.generateWithFunctionCalling(systemPrompt, round2Json, funcDefs);
                finalReply = openAiClient.parseAssistantResponse(rawResponse2).assistantMessage();
            } else {
                finalReply = parsed.assistantMessage();
            }

        } catch (Exception e) {
            log.error("Assistant chat failed for userId={}: {}", userId, e.getMessage(), e);
            finalReply = "I ran into an issue processing your request. Please try again in a moment.";
            chartData = null;
        }

        // Persist both turns
        contextWindow.save(userId, "user", request.message());
        contextWindow.save(userId, "assistant", finalReply);

        return new ChatResponse(finalReply, functionCalled, OffsetDateTime.now(), chartData);
    }

    /**
     * Return the full (untruncated) conversation history for the history endpoint.
     * Only 'user' and 'assistant' roles are returned; 'system_summary' is omitted.
     */
    @Transactional(readOnly = true)
    public List<ConversationMessage> getHistory(UUID userId) {
        return repo.findByUserIdOrderByCreatedAtAsc(userId).stream().filter(m -> !"system_summary".equals(m.getRole()))
                .map(m -> new ConversationMessage(m.getRole(), m.getContent(), m.getCreatedAt())).toList();
    }

    /**
     * Clear all conversation history for the authenticated user.
     */
    @Transactional
    public void clearHistory(UUID userId) {
        contextWindow.clearHistory(userId);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Derives a safe display name from the user's email without exposing the full
     * email address to the AI context. E.g. "john.smith@example.com" →
     * "john.smith".
     */
    static String extractDisplayName(String email) {
        if (email == null || email.isBlank())
            return "there";
        int atIdx = email.indexOf('@');
        return atIdx > 0 ? email.substring(0, atIdx) : email;
    }

    private String appendUserMessage(String historyJson, String message) {
        try {
            ArrayNode array = historyJson != null && !historyJson.isBlank() ? (ArrayNode) om.readTree(historyJson)
                    : om.createArrayNode();
            ObjectNode userNode = om.createObjectNode();
            userNode.put("role", "user");
            userNode.put("content", message);
            array.add(userNode);
            return om.writeValueAsString(array);
        } catch (Exception e) {
            log.error("Failed to append user message to context window", e);
            return "[{\"role\":\"user\",\"content\":" + om.valueToTree(message) + "}]";
        }
    }
}
