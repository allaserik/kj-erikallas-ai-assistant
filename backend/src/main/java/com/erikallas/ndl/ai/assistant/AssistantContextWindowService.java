package com.erikallas.ndl.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Manages the assistant conversation context window.
 *
 * Responsible for: - Loading conversation history for a user. - Estimating
 * total token usage. - Trimming oldest messages when the budget is exceeded. -
 * Serialising history to the JSON format expected by OpenAI.
 *
 * Token budget: 6,000 tokens for history (≈24,000 chars). System prompt is
 * handled separately by AssistantSystemPromptBuilder. A minimum of 5 recent
 * turns (10 messages) is always preserved.
 */
@Component
public class AssistantContextWindowService {

    private static final Logger log = LoggerFactory.getLogger(AssistantContextWindowService.class);

    /** Maximum history tokens before trimming oldest messages. */
    static final int MAX_HISTORY_TOKENS = 6_000;

    /** Always keep at least this many messages regardless of token count. */
    static final int MIN_KEEP_MESSAGES = 10;

    private final AssistantConversationRepository repo;
    private final ObjectMapper om;

    public AssistantContextWindowService(AssistantConversationRepository repo, ObjectMapper om) {
        this.repo = repo;
        this.om = om;
    }

    /**
     * Load recent history for a user, trim to fit within the token budget, and
     * return the result as a JSON string suitable for the OpenAI messages array.
     *
     * Only 'user' and 'assistant' roles are included — 'system_summary' is omitted
     * (those are stored for bookkeeping only in Phase 1).
     *
     * @param userId  Authenticated user ID.
     * @param exclude A message that was just added and not yet in the DB (null to
     *                skip).
     * @return JSON array string, e.g. [{"role":"user","content":"..."},...]
     */
    public String buildMessagesJson(UUID userId, AssistantConversationEntity exclude) {
        List<AssistantConversationEntity> history = repo.findByUserIdOrderByCreatedAtAsc(userId);

        // Filter to only user/assistant roles, and exclude the freshly-appended msg
        List<AssistantConversationEntity> filtered = new ArrayList<>();
        for (AssistantConversationEntity msg : history) {
            if ("system_summary".equals(msg.getRole()))
                continue;
            if (exclude != null && msg.getId().equals(exclude.getId()))
                continue;
            filtered.add(msg);
        }

        // Trim oldest while over token budget (but keep at least MIN_KEEP_MESSAGES)
        filtered = trimToTokenBudget(filtered);

        // Serialise to OpenAI messages JSON array
        ArrayNode array = om.createArrayNode();
        for (AssistantConversationEntity msg : filtered) {
            ObjectNode node = om.createObjectNode();
            node.put("role", msg.getRole());
            node.put("content", msg.getContent());
            array.add(node);
        }

        try {
            return om.writeValueAsString(array);
        } catch (Exception e) {
            log.error("Failed to serialize context window messages", e);
            return "[]";
        }
    }

    /**
     * Persist a user or assistant message turn.
     */
    public AssistantConversationEntity save(UUID userId, String role, String content) {
        AssistantConversationEntity entity = new AssistantConversationEntity(userId, role, content);
        return repo.save(entity);
    }

    /**
     * Count messages stored for a user.
     */
    public long count(UUID userId) {
        return repo.countByUserId(userId);
    }

    /**
     * Delete all conversation history for a user (clear history endpoint).
     */
    public void clearHistory(UUID userId) {
        repo.deleteAllByUserId(userId);
        log.info("Cleared conversation history for userId={}", userId);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private List<AssistantConversationEntity> trimToTokenBudget(List<AssistantConversationEntity> messages) {
        int totalTokens = messages.stream().mapToInt(AssistantConversationEntity::getTokenEstimate).sum();

        // Drop from the front (oldest) until under budget while keeping
        // MIN_KEEP_MESSAGES
        while (totalTokens > MAX_HISTORY_TOKENS && messages.size() > MIN_KEEP_MESSAGES) {
            AssistantConversationEntity removed = messages.remove(0);
            totalTokens -= removed.getTokenEstimate();
        }

        if (totalTokens > MAX_HISTORY_TOKENS) {
            log.debug("History token estimate ({}) still over budget after trimming to {} messages", totalTokens,
                    messages.size());
        }

        return messages;
    }
}
