package com.erikallas.ndl.nutrition.mealplan;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPlanRepository extends JpaRepository<MealPlanEntity, UUID> {
    Optional<MealPlanEntity> findByIdAndUserId(UUID id, UUID userId);
    Optional<MealPlanEntity> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
}
