CREATE TABLE IF NOT EXISTS meal_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_type TEXT NOT NULL CHECK (period_type IN ('daily', 'weekly')),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status TEXT NOT NULL DEFAULT 'generated',
    source TEXT NOT NULL DEFAULT 'rule_based',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_meal_plans_user_created_at
    ON meal_plans(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS meal_plan_meals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_plan_id UUID NOT NULL REFERENCES meal_plans(id) ON DELETE CASCADE,
    day_date DATE NOT NULL,
    meal_type TEXT NOT NULL,
    recipe_id UUID REFERENCES recipes(id) ON DELETE SET NULL,
    position_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (meal_plan_id, day_date, meal_type, position_index)
);

CREATE INDEX IF NOT EXISTS idx_meal_plan_meals_plan_day
    ON meal_plan_meals(meal_plan_id, day_date, position_index);
