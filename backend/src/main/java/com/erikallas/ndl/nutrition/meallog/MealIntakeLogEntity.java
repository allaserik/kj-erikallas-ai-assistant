package com.erikallas.ndl.nutrition.meallog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "meal_intake_logs")
public class MealIntakeLogEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "meal_plan_meal_id")
    private UUID mealPlanMealId;

    @Column(name = "recipe_id")
    private UUID recipeId;

    @Column(name = "consumed_date", nullable = false)
    private LocalDate consumedDate;

    @Column(name = "consumed_time")
    private LocalTime consumedTime;

    @Column(name = "meal_type", nullable = false)
    private String mealType;

    @Column(name = "servings", nullable = false)
    private BigDecimal servings;

    // Nutrition snapshot
    @Column(name = "calories_kcal")
    private BigDecimal caloriesKcal;

    @Column(name = "protein_g")
    private BigDecimal proteinG;

    @Column(name = "carbs_g")
    private BigDecimal carbsG;

    @Column(name = "fats_g")
    private BigDecimal fatsG;

    // Custom meal info
    @Column(name = "custom_meal_name")
    private String customMealName;

    @Column(name = "custom_meal_notes")
    private String customMealNotes;

    // Soft delete
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MealIntakeLogEntity() {
    }

    public MealIntakeLogEntity(UUID id, UUID userId, UUID mealPlanMealId, UUID recipeId, LocalDate consumedDate,
            LocalTime consumedTime, String mealType, BigDecimal servings, BigDecimal caloriesKcal, BigDecimal proteinG,
            BigDecimal carbsG, BigDecimal fatsG, String customMealName, String customMealNotes,
            OffsetDateTime deletedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.mealPlanMealId = mealPlanMealId;
        this.recipeId = recipeId;
        this.consumedDate = consumedDate;
        this.consumedTime = consumedTime;
        this.mealType = mealType;
        this.servings = servings;
        this.caloriesKcal = caloriesKcal;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatsG = fatsG;
        this.customMealName = customMealName;
        this.customMealNotes = customMealNotes;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getMealPlanMealId() {
        return mealPlanMealId;
    }

    public UUID getRecipeId() {
        return recipeId;
    }

    public LocalDate getConsumedDate() {
        return consumedDate;
    }

    public LocalTime getConsumedTime() {
        return consumedTime;
    }

    public String getMealType() {
        return mealType;
    }

    public BigDecimal getServings() {
        return servings;
    }

    public BigDecimal getCaloriesKcal() {
        return caloriesKcal;
    }

    public BigDecimal getProteinG() {
        return proteinG;
    }

    public BigDecimal getCarbsG() {
        return carbsG;
    }

    public BigDecimal getFatsG() {
        return fatsG;
    }

    public String getCustomMealName() {
        return customMealName;
    }

    public String getCustomMealNotes() {
        return customMealNotes;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
