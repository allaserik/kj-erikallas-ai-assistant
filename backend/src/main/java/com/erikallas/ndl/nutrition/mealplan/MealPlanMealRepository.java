package com.erikallas.ndl.nutrition.mealplan;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPlanMealRepository extends JpaRepository<MealPlanMealEntity, UUID> {
    List<MealPlanMealEntity> findByMealPlanIdAndDayDate(UUID mealPlanId, LocalDate dayDate);
}
