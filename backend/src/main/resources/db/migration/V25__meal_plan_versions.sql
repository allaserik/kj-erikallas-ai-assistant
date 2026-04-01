-- Meal plan version snapshots for version control / restore
-- Each snapshot stores a JSON array of the plan's meals at that point in time.

CREATE TABLE IF NOT EXISTS meal_plan_versions (
    id              UUID PRIMARY KEY,
    meal_plan_id    UUID NOT NULL REFERENCES meal_plans(id) ON DELETE CASCADE,
    version_number  INTEGER NOT NULL,
    -- payload: JSON array of meal objects (id, day_date, meal_type, recipe_id, position_index)
    payload_json    JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (meal_plan_id, version_number)
);

CREATE INDEX IF NOT EXISTS idx_meal_plan_versions_plan
    ON meal_plan_versions(meal_plan_id, version_number DESC);
