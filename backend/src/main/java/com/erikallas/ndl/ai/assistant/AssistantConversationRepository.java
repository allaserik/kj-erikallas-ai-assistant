package com.erikallas.ndl.ai.assistant;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssistantConversationRepository extends JpaRepository<AssistantConversationEntity, UUID> {

    /**
     * Load full history for a user, oldest first (for building the message array).
     */
    List<AssistantConversationEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);

    /** Count total messages for a user (for windowing decisions). */
    long countByUserId(UUID userId);

    /** Delete all conversation history for a user (clear history). */
    @Modifying
    @Query("DELETE FROM AssistantConversationEntity c WHERE c.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);

    /**
     * Load the oldest N messages by id so the context window service can delete
     * them when trimming history.
     */
    @Query("""
            SELECT c FROM AssistantConversationEntity c
            WHERE c.userId = :userId
            ORDER BY c.createdAt ASC
            LIMIT :limit
            """)
    List<AssistantConversationEntity> findOldestByUserId(@Param("userId") UUID userId, @Param("limit") int limit);
}
