package com.erikallas.ndl.ai.assistant;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "assistant_conversations")
public class AssistantConversationEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 'user' | 'assistant' | 'system_summary' */
    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "token_estimate", nullable = false)
    private int tokenEstimate;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AssistantConversationEntity() {
    }

    public AssistantConversationEntity(UUID userId, String role, String content) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.tokenEstimate = content.length() / 4;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public int getTokenEstimate() {
        return tokenEstimate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
