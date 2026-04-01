package com.erikallas.ndl.nutrition.mealplan;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MealPlanVersionRepository extends JpaRepository<MealPlanVersionEntity, UUID> {

    List<MealPlanVersionEntity> findByMealPlanIdOrderByVersionNumberDesc(UUID mealPlanId);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM MealPlanVersionEntity v WHERE v.mealPlanId = :mealPlanId")
    int findMaxVersionNumber(UUID mealPlanId);
}
