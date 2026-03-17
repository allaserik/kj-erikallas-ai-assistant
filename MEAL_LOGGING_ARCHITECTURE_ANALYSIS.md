# Meal Logging & Nutrition Tracking Flow - Complete Analysis

**Date**: March 17, 2026  
**Status**: ⚠️ **CRITICAL GAP IDENTIFIED** - Meal logging system not implemented

---

## Executive Summary

The nutrition dashboard currently shows nutrition progress based on **planned meals**, not **logged/eaten meals**. There is no meal consumption tracking system implemented yet. This is a critical architectural gap that needs to be addressed before the nutrition tracking dashboard can be considered functional for actual user check-ins.

---

## 1. Current Meal Logging Implementation

### Status: ❌ **NOT IMPLEMENTED**

**What exists:**

- ✅ Meal plan generation (rules-based AI strategies)
- ✅ Meal plan storage ([meal_plans](backend/src/main/resources/db/migration/V23__meal_plans_foundation.sql), [meal_plan_meals](backend/src/main/resources/db/migration/V23__meal_plans_foundation.sql) tables)
- ✅ Recipe catalog with nutrition data

**What's missing:**

- ❌ No `meal_intake_logs` table
- ❌ No `MealIntakeLog`, `MealCheckin`, `MealLog`, or `MealConsumption` entity
- ❌ No API endpoints for logging meal consumption
- ❌ No frontend UI to mark meals as eaten
- ❌ No meal check-in functionality

**Key Files Checked:**
| File | Contains | Status |
|------|----------|--------|
| `backend/src/main/resources/db/migration/` | V1-V23 migrations | ✅ No meal logging tables found |
| `backend/src/main/java/com/erikallas/ndl/nutrition/mealplan/` | MealPlanEntity, MealPlanMealEntity | ✅ Templates only, not logs |
| `backend/src/main/java/com/erikallas/ndl/nutrition/dashboard/` | NutritionDashboardService | ✅ Reads plans, not logs |
| `frontend/src/features/checkin/` | CheckInPage | ✅ Weight/activity only, no meals |

---

## 2. Dashboard Nutrition Data Source

### Architecture: Plan-Based (Not Consumption-Based)

#### Flow:

```
User                 NutritionDashboardController
  └─ GET /api/nutrition/dashboard/summary
          └─ NutritionDashboardService.getDailySummary(userId)
                  │
                  ├─1. MealPlanQueryService.getLatest(userId)
                  │     └─ Returns latest meal_plan + ALL meal_plan_meals
                  │
                  ├─2. Filter today's meals from plan
                  │     └─ Loop meal_plan.days(), find date = today
                  │
                  ├─3. Aggregate nutrition (SUM calories, protein, carbs, fats)
                  │     └─ From recipe nutrition snapshots in meal_plan_meals
                  │
                  ├─4. Get user targets from NutritionPreferences
                  │     └─ calorieTargetKcal, proteinTargetG, etc.
                  │
                  └─5. Calculate vs targets (percentages, surplus/deficit)
                        └─ Return DailySummary { date, totalMeals, calories, protein, carbs, fats, ...}
```

**Source of Truth: Latest meal plan, filtered by date**

#### Code Evidence:

[NutritionDashboardService.java](backend/src/main/java/com/erikallas/ndl/nutrition/dashboard/NutritionDashboardService.java#L50-L76):

```java
@Transactional(readOnly = true)
public NutritionSummaryModels.DailySummary getDailySummary(UUID userId) {
    LocalDate today = LocalDate.now();

    try {
        // Step 1: Get latest meal plan for user
        var mealPlan = mealPlanQueryService.getLatest(userId);

        if (mealPlan == null) {
            return createEmptySummary(today);
        }

        // Step 2 & 3: Extract and sum today's meals
        AggregatedNutrition totalNutrition = aggregateTodaysMeals(mealPlan, today);

        // Step 4: Get user targets
        var preferences = preferencesService.getOrCreateWithPrefill(userId);

        // Step 5 & 6: Compare and format
        return buildSummary(today, totalNutrition, preferences);
    } catch (Exception e) {
        return createEmptySummary(today);
    }
}
```

#### Current Dashboard SummaryResponse:

```typescript
export type DailySummary = {
  date: string;
  totalMeals: number;
  calories: NutrientProgress;
  protein: NutrientProgress;
  carbs: NutrientProgress;
  fats: NutrientProgress;
  summary: string;
  source: "generated" | "ai"; // ← Always "generated" (rule-based plan)
};
```

#### API Endpoint:

```
GET /api/nutrition/dashboard/summary
Headers: Authorization: Bearer {token}
Response: 200 OK { data: DailySummary }
```

[MealPlanController.java](backend/src/main/java/com/erikallas/ndl/nutrition/mealplan/MealPlanController.java#L24-L35):

```java
@GetMapping("/api/nutrition/dashboard/summary")
public ApiSuccess<NutritionSummaryModels.DailySummary> getTodaysSummary(JwtAuthenticationToken auth) {
    var user = userService.ensureUserFromJwt(auth);
    var summary = nutritionDashboardService.getDailySummary(user.getId());
    return ApiSuccess.of(summary);
}
```

---

## 3. Are Meals from meal_plan_meal Table Directly Counted?

### ✅ **YES - Directly Counted as "Eaten"**

**The Problem:**
The system treats **all planned meals as automatically eaten**. There's no distinction between:

- **Planned** (user agreed to eat this)
- **Logged** (user actually ate it)
- **Skipped** (user didn't eat it)

**Why This Matters:**

- If user generates a meal plan but only eats 2 of 3 meals, the dashboard still shows nutrition as if all 3 were eaten
- Users can't deviate from the plan without the dashboard becoming inaccurate
- No accountability for actual consumption

**Code Flow:**

```java
// NutritionDashboardService.aggregateTodaysMeals()
for (var dayPlan : mealPlan.days()) {
    if (dayPlan.date().isEqual(targetDate)) {
        for (var meal : dayPlan.meals()) {
            // ↓ Every meal in the plan is summed, no consumption check
            totalCalories = totalCalories.add(meal.caloriesKcal());
            totalProtein = totalProtein.add(meal.proteinG());
            totalCarbs = totalCarbs.add(meal.carbsG());
            totalFats = totalFats.add(meal.fatsG());
        }
    }
}
```

---

## 4. Demo Data Initializer: What Gets Created?

### Created Data:

[DemoDataInitializer.java](backend/src/main/java/com/erikallas/ndl/data/DemoDataInitializer.java#L280-L330):

✅ **Created:**

- Demo user (id: `00000000-0000-0000-0000-000000000001`, email: `demo@example.com`)
- Health profile (175cm, "Office" occupation, intermediate fitness)
- 2 active goals + 1 archived goal
- 30 days of weight entries (downward trend)
- Goal progress history (8 weekly snapshots for trend charts)
- 35+ days of activity check-ins
- Nutrition preferences (2000 kcal target, flexible macro targets)
- **2 meal plans** (each 7 days): one from 7 days ago, one starting today
  - 3 meals per day (breakfast, lunch, dinner)
  - Recipes cycled from catalog

❌ **NOT Created:**

- No meal consumption logs
- No `meal_intake_logs` entries
- Demo shows plans as if all meals were eaten (dashboard shows all nutrition from planned meals)

**Key Code:**

```java
private void createSingleMealPlan(UUID userId, LocalDate startDate, int numDays, ...) {
    MealPlanEntity plan = new MealPlanEntity(UUID.randomUUID(), userId, "WEEKLY",
        startDate, endDate, "ACTIVE", "DEMO", createdAt, now);
    MealPlanEntity savedPlan = mealPlanRepo.save(plan);

    String[] mealTypes = { "BREAKFAST", "LUNCH", "DINNER" };
    for (int day = 0; day < numDays; day++) {
        LocalDate dayDate = startDate.plus(day, ChronoUnit.DAYS);
        for (int mealIdx = 0; mealIdx < mealTypes.length; mealIdx++) {
            // ↓ Creates meal_plan_meals entries only
            RecipeCatalogModels.RecipeListItem recipe = recipes.get(...);
            MealPlanMealEntity meal = new MealPlanMealEntity(UUID.randomUUID(),
                savedPlan.getId(), dayDate, mealTypes[mealIdx], recipe.id(),
                mealIdx, mealCreatedAt, now);
            // ↓ NO meal_intake_logs entry created
            mealPlanMealRepo.save(meal);
        }
    }
}
```

---

## 5. API Endpoints for Meal Logging/Check-In/Completion

### Current Endpoints: ❌ **NONE**

**What exists:**

```
POST   /api/meal-plans/generate           ← Generate a new plan
GET    /api/meal-plans/{mealPlanId}       ← View a plan
GET    /api/meal-plans/latest             ← Get most recent plan
GET    /api/nutrition/dashboard/summary   ← Get nutrition summary (reads plans)
```

**What's missing:**

```
POST   /api/nutrition/intake              ← Log/check-in a meal (Implementation Plan mentions this)
POST   /api/meals/{mealId}/consume        ← Alternative endpoint pattern
PATCH  /api/nutrition/intake/{logId}      ← Update logged meal
GET    /api/nutrition/intake?date=...     ← Get logged meals for a date
```

**Implementation Plan Reference:**
[CAL_IMPLEMENTATION_PLAN.md](assignment-docs/CAL_IMPLEMENTATION_PLAN.md#L129-L145) mentions:

```
Tracking + analytics
  - `POST /api/nutrition/intake`
  - `GET /api/nutrition/summary/daily?date=...`
  - `GET /api/nutrition/summary/weekly?weekStart=...`
  - `GET /api/nutrition/trends?range=weekly|monthly`
```

These are **planned but not implemented**.

---

## 6. Frontend Check-In Pages

### Current Check-In UI:

[CheckInPage.tsx](frontend/src/features/checkin/CheckInPage.tsx):

- ✅ Weight entry form
- ✅ Activity check-in form (cardio, strength, walking, etc.)
- ✅ Timeline view (weight + activity entries)
- ❌ **NO meal check-in section**

```typescript
type TimelineItem = {
  id: string;
  type: "weight" | "activity"; // ← Only these two types
  at: string;
  title: string;
  subtitle?: string;
};
```

### Dashboard Page:

[DashboardPage.tsx](frontend/src/features/dashboard/DashboardPage.tsx) + [useDashboardData.ts](frontend/src/features/dashboard/useDashboardData.ts):

- Fetches `getDailySummary()` from `/api/nutrition/dashboard/summary`
- Displays nutrition progress (calories, macros) with visual bars
- Shows `totalMeals` from the plan
- **No indication that these aren't logged/eaten meals**

---

## 7. Database Schema

### Current Tables (V23 is latest migration):

```sql
-- meal_plans: Template for a day/week of eating
CREATE TABLE meal_plans (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    period_type TEXT NOT NULL,  -- 'daily' or 'weekly'
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status TEXT NOT NULL,       -- 'generated', 'ACTIVE', etc.
    source TEXT NOT NULL,       -- 'rule_based', 'DEMO', 'ai'
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

-- meal_plan_meals: Individual meals in a plan (NOT LOGS)
CREATE TABLE meal_plan_meals (
    id UUID PRIMARY KEY,
    meal_plan_id UUID NOT NULL REFERENCES meal_plans(id),
    day_date DATE NOT NULL,
    meal_type TEXT NOT NULL,    -- 'BREAKFAST', 'LUNCH', 'DINNER'
    recipe_id UUID REFERENCES recipes(id),
    position_index INTEGER,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);
```

### Missing Tables (from Implementation Plan):

```sql
-- Should exist but doesn't:
CREATE TABLE meal_intake_logs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    meal_plan_meal_id UUID REFERENCES meal_plan_meals(id),  -- nullable, can log ad-hoc meals
    consumed_at TIMESTAMPTZ NOT NULL,  -- ISO 8601
    recipe_snapshot_json JSONB,  -- snapshot of recipe nutrition at time of logging
    nutrition_snapshot_json JSONB,  -- calculated nutrition for this consumption
    created_at TIMESTAMPTZ,
    notes TEXT
);

-- Daily/weekly aggregates (could be views or materialized)
CREATE TABLE nutrition_daily_summary (
    user_id UUID,
    day_date DATE,
    total_calories DECIMAL,
    total_protein_g DECIMAL,
    total_carbs_g DECIMAL,
    total_fats_g DECIMAL,
    efficiency_ratio DECIMAL,  -- actual vs daily target
    PRIMARY KEY (user_id, day_date)
);
```

---

## What Needs to Be Built

### Phase 2A - Critical: Meal Consumption Logging

#### 1. Database Schema (V24 migration)

```java
// MealIntakeLogEntity
@Entity
@Table(name = "meal_intake_logs")
public class MealIntakeLogEntity {
    @Id UUID id;
    @Column UUID userId;
    @Column UUID mealPlanMealId;  // nullable for ad-hoc meals
    @Column OffsetDateTime consumedAt;
    @Column String recipeSnapshotJson;  // store recipe state at consumption time
    @Column String nutritionSnapshotJson;  // pre-calculated nutrition
    @Column OffsetDateTime createdAt;
}
```

#### 2. Service Layer

```java
// MealIntakeService
public MealIntakeLog logMealConsumption(
    UUID userId,
    UUID mealPlanMealId,  // which meal was eaten
    OffsetDateTime consumedAt
);

public List<MealIntakeLog> getLogsForDate(UUID userId, LocalDate date);

public void deleteMealLog(UUID userId, UUID logId);  // user can remove accidentally logged meal
```

#### 3. Updated Dashboard Service

```java
// Instead of: aggregateTodaysMeals(mealPlan, today)
// Use: aggregateTodaysMealsFromLogs(userId, today)

// New logic:
// 1. Query meal_intake_logs WHERE user_id = ? AND consumed_at BETWEEN today 00:00 and today 23:59
// 2. SUM nutrition from actual logs instead of plan
```

#### 4. API Endpoints

```
POST /api/nutrition/intake
  Body: { mealPlanMealId: UUID, consumedAt: ISO8601 }
  Returns: MealIntakeLog

GET /api/nutrition/intake?date=2026-03-17
  Returns: List<MealIntakeLog>

DELETE /api/nutrition/intake/{logId}
  Returns: 204
```

#### 5. Frontend Check-In UI

- Add meal section to [CheckInPage.tsx](frontend/src/features/checkin/CheckInPage.tsx)
- Show today's planned meals
- Allow clicking to mark as eaten with timestamp
- Show eaten meals in timeline

---

## Risk Assessment

### Current State Issues:

| Issue                                    | Severity    | Impact                                                   |
| ---------------------------------------- | ----------- | -------------------------------------------------------- |
| Dashboard shows planned, not eaten meals | 🔴 Critical | Users can't track actual consumption; dashboards mislead |
| No meal logging API                      | 🔴 Critical | Can't build check-in UI without backend support          |
| Demo doesn't log meals                   | 🟡 Medium   | Demo doesn't demonstrate real functionality              |
| Check-In page missing meals              | 🟡 Medium   | Users expect to check in meals in one place              |

### Dependency Chain:

```
Meal Intake Logging ← blocks → Dashboard Accuracy
                   ← blocks → Check-In UI completion
                   ← blocks → Nutrition Trends (accurate historical data)
                   ← blocks → AI Insights (would use real consumption data)
```

---

## Recommendations

### Immediate (Before Deploying to Users):

1. **Build MealIntakeLog entity** (1-2 hours)
2. **Create meal logging API endpoints** (1-2 hours)
3. **Update NutritionDashboardService** to read from logs instead of plans (1-2 hours)
4. **Add meal check-in UI** to CheckInPage (2-3 hours)
5. **Update demo data initializer** to create sample meal logs (1 hour)

### Short-term (Phase 2B):

1. Meal customization UI (swap, reorder, delete in plan)
2. Shopping list generation
3. Nutrition trends with real consumption data

### Long-term (Phase 3):

1. AI insights based on actual consumption vs. plans
2. Recipe substitution recommendations
3. Weekly adherence reports

---

## Appendix: File Locations

### Key Backend Files:

- **Service Layer** [NutritionDashboardService.java](backend/src/main/java/com/erikallas/ndl/nutrition/dashboard/NutritionDashboardService.java)
- **Controller** [NutritionDashboardController.java](backend/src/main/java/com/erikallas/ndl/nutrition/dashboard/NutritionDashboardController.java)
- **Models** [NutritionSummaryModels.java](backend/src/main/java/com/erikallas/ndl/nutrition/dashboard/NutritionSummaryModels.java)
- **Meal Plan Service** [MealPlanService.java](backend/src/main/java/com/erikallas/ndl/nutrition/mealplan/MealPlanService.java)
- **Database Migration** [V23\_\_meal_plans_foundation.sql](backend/src/main/resources/db/migration/V23__meal_plans_foundation.sql)
- **Demo Initializer** [DemoDataInitializer.java](backend/src/main/java/com/erikallas/ndl/data/DemoDataInitializer.java)

### Key Frontend Files:

- **Dashboard Page** [DashboardPage.tsx](frontend/src/features/dashboard/DashboardPage.tsx)
- **Dashboard Data Hook** [useDashboardData.ts](frontend/src/features/dashboard/useDashboardData.ts)
- **Check-In Page** [CheckInPage.tsx](frontend/src/features/checkin/CheckInPage.tsx)
- **Nutrition API** [frontend/src/shared/api/nutrition.ts](frontend/src/shared/api/nutrition.ts)
- **Types** [frontend/src/shared/types.ts](frontend/src/shared/types.ts)

### Documentation:

- [CAL_ASSIGNMENT.md](assignment-docs/CAL_ASSIGNMENT.md) - Functional requirements
- [CAL_IMPLEMENTATION_PLAN.md](assignment-docs/CAL_IMPLEMENTATION_PLAN.md) - Detailed API surface & DB design
