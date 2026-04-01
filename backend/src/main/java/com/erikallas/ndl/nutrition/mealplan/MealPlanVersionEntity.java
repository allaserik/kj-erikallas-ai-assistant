package com.erikallas.ndl.nutrition.mealplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "meal_plan_versions")
public class MealPlanVersionEntity {

    @Id
    private UUID id;

    @Column(name = "meal_plan_id", nullable = false)
    private UUID mealPlanId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    /**
     * JSON array snapshot of the plan's meals at the time this version was created.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected MealPlanVersionEntity() {
    }

    public MealPlanVersionEntity(UUID id, UUID mealPlanId, int versionNumber, String payloadJson,
            OffsetDateTime createdAt) {
        this.id = id;
        this.mealPlanId = mealPlanId;
        this.versionNumber = versionNumber;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMealPlanId() {
        return mealPlanId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
