package com.erikallas.ndl.nutrition.mealplan;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPlanMealRepository extends JpaRepository<MealPlanMealEntity, UUID> {
}
