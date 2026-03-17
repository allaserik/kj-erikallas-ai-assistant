-- V24__meal_intake_logs.sql
-- Meal intake logging: tracks when users actually consume meals
-- Allows users to log meals from meal plans or custom meals
-- Stores nutrition snapshot at time of logging for historical accuracy

CREATE TABLE IF NOT EXISTS meal_intake_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    meal_plan_meal_id UUID REFERENCES meal_plan_meals(id) ON DELETE SET NULL,
    recipe_id UUID REFERENCES recipes(id) ON DELETE SET NULL,
    consumed_date DATE NOT NULL,
    consumed_time TIME,
    meal_type TEXT NOT NULL,
    servings NUMERIC(8, 2) NOT NULL DEFAULT 1.0 CHECK (servings > 0),
    
    -- Nutrition snapshot at time of logging (not fetched from recipe later)
    calories_kcal NUMERIC(8, 2) CHECK (calories_kcal IS NULL OR calories_kcal >= 0),
    protein_g NUMERIC(8, 2) CHECK (protein_g IS NULL OR protein_g >= 0),
    carbs_g NUMERIC(8, 2) CHECK (carbs_g IS NULL OR carbs_g >= 0),
    fats_g NUMERIC(8, 2) CHECK (fats_g IS NULL OR fats_g >= 0),
    
    -- Custom meal info (when not from a recipe)
    custom_meal_name TEXT,
    custom_meal_notes TEXT,
    
    -- Soft delete
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Either meal_plan_meal_id or recipe_id should be set
    -- (or both for logged meals from a plan)
    CHECK (
        (meal_plan_meal_id IS NOT NULL OR recipe_id IS NOT NULL OR custom_meal_name IS NOT NULL)
    )
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_meal_intake_logs_user_consumed_date
    ON meal_intake_logs(user_id, consumed_date DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_meal_intake_logs_user_date_range
    ON meal_intake_logs(user_id, consumed_date)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_meal_intake_logs_meal_plan_meal
    ON meal_intake_logs(meal_plan_meal_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_meal_intake_logs_recipe
    ON meal_intake_logs(recipe_id)
    WHERE deleted_at IS NULL;
