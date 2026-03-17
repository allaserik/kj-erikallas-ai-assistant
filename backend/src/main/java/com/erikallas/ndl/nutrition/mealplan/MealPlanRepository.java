package com.erikallas.ndl.nutrition.mealplan;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPlanRepository extends JpaRepository<MealPlanEntity, UUID> {
}
