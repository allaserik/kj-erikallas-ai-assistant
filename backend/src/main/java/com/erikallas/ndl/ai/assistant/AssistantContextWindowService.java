package com.erikallas.ndl.ai.assistant;

import com.erikallas.ndl.ai.openai.OpenAiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the assistant conversation context window.
 *
 * Responsible for: - Loading conversation history for a user. - Estimating
 * total token usage. - Summarising off-topic / oldest messages via AI when over
 * budget. - Serialising history to the JSON format expected by OpenAI.
 *
 * Token budget: 6,000 tokens for history (≈24,000 chars). System prompt is
 * handled separately by AssistantSystemPromptBuilder. A minimum of 5 recent
 * turns (10 messages) is always preserved.
 *
 * Summarisation strategy: when approaching the token limit the service detects
 * the current conversation topic from the most recent user messages, then
 * compresses messages that are off-topic OR oldest-first into a concise
 * {@code system_summary} row. That summary is prepended to subsequent context
 * windows so no key facts are lost.
 */
@Component
public class AssistantContextWindowService {

    private static final Logger log = LoggerFactory.getLogger(AssistantContextWindowService.class);

    /** Maximum history tokens before summarisation and trimming. */
    static final int MAX_HISTORY_TOKENS = 6_000;

    /** Always keep at least this many recent messages untouched. */
    static final int MIN_KEEP_MESSAGES = 10;

    private static final String SUMMARISE_SYSTEM = """
            You are a concise note-taker for a wellness assistant system. Your task is to compress
            a conversation excerpt into a brief memory note (max 180 words).
            Rules:
            - Preserve factual details: user's weight, goals, dietary preferences, macro numbers, anything specific.
            - Lead with details most relevant to the current topic (%s).
            - Omit pleasantries, filler, and repetition.
            - Write in third-person past tense: "User mentioned their weight goal is 70 kg..."
            - Return plain prose only — no bullet points, no headings.
            """;

    private final AssistantConversationRepository repo;
    private final OpenAiClient openAiClient;
    private final ObjectMapper om;

    public AssistantContextWindowService(AssistantConversationRepository repo, OpenAiClient openAiClient,
            ObjectMapper om) {
        this.repo = repo;
        this.openAiClient = openAiClient;
        this.om = om;
    }

    /**
     * Load recent history for a user, include any existing summary, and return the
     * result as a JSON string suitable for the OpenAI messages array.
     *
     * @param userId  Authenticated user ID.
     * @param exclude A message that was just added and not yet in the DB (null to
     *                skip).
     * @return JSON array string, e.g. [{"role":"user","content":"..."},...]
     */
    public String buildMessagesJson(UUID userId, AssistantConversationEntity exclude) {
        List<AssistantConversationEntity> history = repo.findByUserIdOrderByCreatedAtAsc(userId);

        // Collect the most recent system_summary (if any) — included at the top
        String conversationSummary = history.stream().filter(m -> "system_summary".equals(m.getRole()))
                .reduce((a, b) -> b).map(AssistantConversationEntity::getContent).orElse(null);

        // Filter to only user/assistant
        List<AssistantConversationEntity> filtered = history.stream().filter(m -> !"system_summary".equals(m.getRole()))
                .filter(m -> exclude == null || !m.getId().equals(exclude.getId())).collect(Collectors.toList());

        filtered = trimToTokenBudget(filtered);

        ArrayNode array = om.createArrayNode();

        // Prepend the prior-conversation summary as a system message
        if (conversationSummary != null) {
            ObjectNode summaryNode = om.createObjectNode();
            summaryNode.put("role", "system");
            summaryNode.put("content", "Prior conversation summary: " + conversationSummary);
            array.add(summaryNode);
        }

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
     * Check if summarisation is needed and, if so, compress eligible messages into
     * a new {@code system_summary} row. Called by AssistantService before
     * {@link #buildMessagesJson}.
     *
     * <p>
     * Topic-aware: messages that are off-topic relative to the most recent user
     * turns are compressed first (dynamic context detail adjustment). Oldest
     * messages are compressed first within the same topic category.
     *
     * @param userId Authenticated user ID.
     */
    @Transactional
    public void maybeSummariseHistory(UUID userId) {
        if (!openAiClient.hasKey()) {
            return; // Cannot summarise without AI
        }

        List<AssistantConversationEntity> history = repo.findByUserIdOrderByCreatedAtAsc(userId);
        List<AssistantConversationEntity> candidates = history.stream()
                .filter(m -> !"system_summary".equals(m.getRole())).collect(Collectors.toList());

        int total = candidates.stream().mapToInt(AssistantConversationEntity::getTokenEstimate).sum();
        if (total <= MAX_HISTORY_TOKENS) {
            return; // Within budget — nothing to do
        }

        if (candidates.size() <= MIN_KEEP_MESSAGES) {
            return; // Not enough messages to trim safely
        }

        // Detect the current topic from the last 3 user messages
        String currentTopic = detectTopic(candidates.stream().filter(m -> "user".equals(m.getRole()))
                .skip(Math.max(0, candidates.stream().filter(m -> "user".equals(m.getRole())).count() - 3))
                .map(AssistantConversationEntity::getContent).collect(Collectors.joining(" ")));

        // Eligible for summarisation: all except the most recent MIN_KEEP_MESSAGES
        List<AssistantConversationEntity> eligible = new ArrayList<>(
                candidates.subList(0, candidates.size() - MIN_KEEP_MESSAGES));

        if (eligible.isEmpty()) {
            return;
        }

        // Sort: off-topic messages first, then by age (oldest first within each
        // group). This implements dynamic context detail level adjustment.
        eligible.sort(Comparator
                .comparingInt((AssistantConversationEntity m) -> tagTopic(m.getContent()).equals(currentTopic) ? 1 : 0)
                .thenComparing(AssistantConversationEntity::getCreatedAt));

        // Build transcript for the AI summariser
        StringBuilder transcript = new StringBuilder();
        for (AssistantConversationEntity m : eligible) {
            transcript.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        }

        try {
            String summary = openAiClient.generateText(SUMMARISE_SYSTEM.formatted(currentTopic), transcript.toString(),
                    300);

            // Delete existing system_summary rows for this user
            history.stream().filter(m -> "system_summary".equals(m.getRole())).forEach(repo::delete);

            // Delete the summarised messages
            repo.deleteAllById(eligible.stream().map(AssistantConversationEntity::getId).toList());

            // Persist the new summary
            save(userId, "system_summary", summary.strip());
            log.info("Summarised {} messages for userId={} (topic={})", eligible.size(), userId, currentTopic);

        } catch (Exception e) {
            log.warn("Conversation summarisation failed for userId={}: {}", userId, e.getMessage());
            // Fall back to simple trimming — do not rethrow
            trimOldestMessages(eligible);
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
        while (totalTokens > MAX_HISTORY_TOKENS && messages.size() > MIN_KEEP_MESSAGES) {
            AssistantConversationEntity removed = messages.remove(0);
            totalTokens -= removed.getTokenEstimate();
        }
        return messages;
    }

    private void trimOldestMessages(List<AssistantConversationEntity> toDelete) {
        try {
            repo.deleteAllById(toDelete.stream().map(AssistantConversationEntity::getId).toList());
        } catch (Exception e) {
            log.warn("Fallback trim failed: {}", e.getMessage());
        }
    }

    /**
     * Classify a block of text into one of five topic categories using keywords.
     */
    private static String detectTopic(String text) {
        if (text == null || text.isBlank())
            return "general";
        return tagTopic(text.toLowerCase());
    }

    private static String tagTopic(String text) {
        String lower = text.toLowerCase();
        int weight = count(lower, "weight", "kg", "bmi", "body mass", "gained", "lost weight", "scale");
        int nutrition = count(lower, "calorie", "protein", "carbs", "fats", "macro", "nutrients", "eating", "food");
        int goals = count(lower, "goal", "target", "progress", "milestone", "on track", "achieve");
        int meal = count(lower, "meal", "recipe", "breakfast", "lunch", "dinner", "cook", "prepare", "eat today");
        int max = Math.max(weight, Math.max(nutrition, Math.max(goals, meal)));
        if (max == 0)
            return "general";
        if (max == weight)
            return "weight";
        if (max == nutrition)
            return "nutrition";
        if (max == goals)
            return "goals";
        return "meal_plan";
    }

    private static int count(String text, String... keywords) {
        int c = 0;
        for (String kw : keywords) {
            int idx = 0;
            while ((idx = text.indexOf(kw, idx)) >= 0) {
                c++;
                idx += kw.length();
            }
        }
        return c;
    }
}
