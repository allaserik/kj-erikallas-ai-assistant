package com.erikallas.ndl.ai.assistant;

import java.time.OffsetDateTime;

/** Request/response models for the assistant API. */
public class AssistantModels {

    /**
     * Incoming chat message from the client.
     *
     * @param message The user's text (1–1000 chars, validated server-side).
     * @param mode    Response verbosity: "concise" (default) or "detailed".
     */
    public record ChatRequest(String message, String mode) {

        public ChatRequest {
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("Message must not be blank");
            }
            if (message.length() > 1000) {
                throw new IllegalArgumentException("Message must not exceed 1000 characters");
            }
            if (mode == null || mode.isBlank()) {
                mode = "concise";
            }
            if (!mode.equals("concise") && !mode.equals("detailed")) {
                mode = "concise";
            }
        }
    }

    /**
     * Assistant reply returned to the client.
     *
     * @param reply          Markdown-formatted text from the assistant.
     * @param functionCalled Name of the function called (if any), or null.
     * @param timestamp      Server time the reply was generated.
     */
    public record ChatResponse(String reply, String functionCalled, OffsetDateTime timestamp) {
    }

    /**
     * A single conversation turn for the history endpoint.
     *
     * @param role      "user" | "assistant" | "system_summary"
     * @param content   Message text.
     * @param createdAt When the message was recorded.
     */
    public record ConversationMessage(String role, String content, OffsetDateTime createdAt) {
    }

    private AssistantModels() {
    }
}
