package com.erikallas.ndl.nutrition.mealplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "meal_plan_meals")
public class MealPlanMealEntity {

    @Id
    private UUID id;

    @Column(name = "meal_plan_id", nullable = false)
    private UUID mealPlanId;

    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

    @Column(name = "meal_type", nullable = false)
    private String mealType;

    @Column(name = "recipe_id")
    private UUID recipeId;

    @Column(name = "position_index", nullable = false)
    private int positionIndex;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MealPlanMealEntity() {
    }

    public MealPlanMealEntity(UUID id, UUID mealPlanId, LocalDate dayDate, String mealType, UUID recipeId, int positionIndex,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.mealPlanId = mealPlanId;
        this.dayDate = dayDate;
        this.mealType = mealType;
        this.recipeId = recipeId;
        this.positionIndex = positionIndex;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMealPlanId() {
        return mealPlanId;
    }

    public LocalDate getDayDate() {
        return dayDate;
    }

    public String getMealType() {
        return mealType;
    }

    public UUID getRecipeId() {
        return recipeId;
    }

    public int getPositionIndex() {
        return positionIndex;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
