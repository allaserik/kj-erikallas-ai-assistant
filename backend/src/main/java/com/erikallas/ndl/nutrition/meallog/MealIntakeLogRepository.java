package com.erikallas.ndl.nutrition.meallog;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MealIntakeLogRepository extends JpaRepository<MealIntakeLogEntity, UUID> {

    /**
     * Get all meal logs for a user on a specific date, excluding soft-deleted.
     */
    @Query("SELECT m FROM MealIntakeLogEntity m "
            + "WHERE m.userId = :userId AND m.consumedDate = :date AND m.deletedAt IS NULL "
            + "ORDER BY m.consumedTime ASC, m.createdAt ASC")
    List<MealIntakeLogEntity> findByUserIdAndConsumedDate(@Param("userId") UUID userId, @Param("date") LocalDate date);

    /**
     * Get all meal logs for a user within a date range, excluding soft-deleted.
     */
    @Query("SELECT m FROM MealIntakeLogEntity m "
            + "WHERE m.userId = :userId AND m.consumedDate >= :startDate AND m.consumedDate <= :endDate AND m.deletedAt IS NULL "
            + "ORDER BY m.consumedDate DESC, m.consumedTime ASC")
    List<MealIntakeLogEntity> findByUserIdAndDateRange(@Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get meal logs for a specific meal plan meal.
     */
    @Query("SELECT m FROM MealIntakeLogEntity m " + "WHERE m.mealPlanMealId = :mealPlanMealId AND m.deletedAt IS NULL")
    List<MealIntakeLogEntity> findByMealPlanMealId(@Param("mealPlanMealId") UUID mealPlanMealId);

    /**
     * Check if a meal plan meal has been logged.
     */
    @Query("SELECT COUNT(m) > 0 FROM MealIntakeLogEntity m "
            + "WHERE m.mealPlanMealId = :mealPlanMealId AND m.deletedAt IS NULL")
    boolean existsForMealPlanMeal(@Param("mealPlanMealId") UUID mealPlanMealId);

    /**
     * Find by ID, excluding soft-deleted.
     */
    @Query("SELECT m FROM MealIntakeLogEntity m WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<MealIntakeLogEntity> findByIdNotDeleted(@Param("id") UUID id);

    /**
     * Count total logged meals for a user on a specific date.
     */
    @Query("SELECT COUNT(m) FROM MealIntakeLogEntity m "
            + "WHERE m.userId = :userId AND m.consumedDate = :date AND m.deletedAt IS NULL")
    long countByUserIdAndConsumedDate(@Param("userId") UUID userId, @Param("date") LocalDate date);
}
