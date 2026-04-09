package com.erikallas.ndl.ai.assistant;

import java.time.OffsetDateTime;
import java.util.List;

/** Request/response models for the assistant API. */
public class AssistantModels {

    // -----------------------------------------------------------------------
    // Chart data models — embedded in ChatResponse when a chart function
    // was called. Stripped from the AI-facing JSON to save tokens.
    // -----------------------------------------------------------------------

    /**
     * A single (x, y) data point for any chart type.
     *
     * @param x Label or date string (ISO date for time-series, name for
     *          categorical).
     * @param y Numeric value.
     */
    public record ChartPoint(String x, double y) {
    }

    /**
     * One data series within a chart (line / bar series, or a pie segment set).
     *
     * @param label Human-readable series name (e.g. "Weight", "Protein").
     * @param unit  Display unit (e.g. "kg", "kcal", "g").
     * @param data  Ordered list of data points.
     */
    public record ChartSeries(String label, String unit, List<ChartPoint> data) {
    }

    /**
     * Chart payload delivered to the frontend alongside the assistant's text reply.
     *
     * @param type   "line" | "bar" | "pie"
     * @param title  Chart heading.
     * @param series Ordered list of data series.
     */
    public record ChartData(String type, String title, List<ChartSeries> series) {
    }

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
     * @param chart          Optional chart payload when a chart function was
     *                       called.
     */
    public record ChatResponse(String reply, String functionCalled, OffsetDateTime timestamp, ChartData chart) {
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
