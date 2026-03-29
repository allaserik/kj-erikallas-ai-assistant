# Meal Planner UI Architecture - Clarified

## Problem Solved

The original UI was confusing because it mixed multiple separate workflows into one giant component. Here's what was unclear:

- Generation vs. viewing vs. logging were all tangled together
- It wasn't clear if data was "demo" or "real"
- The flow wasn't obvious to users

## New Architecture - 3 Clear Workflows

### 1. **Meal Plans Hub** (`/planner`)

The home page for meal planning with 3 action buttons:

```
┌─────────────────────────────────┐
│    🍽️ Meal Plans               │
│  Generate, View, or Log meals   │
├─────────────────────────────────┤
│                                 │
│  [✨ Generate New]  [📅 View]   │
│     Plan             Current      │
│                      Plan         │
│  [🍽️ Log Meals Today]          │
│                                 │
├─────────────────────────────────┤
│ Current Plan Preview (3 days)   │
└─────────────────────────────────┘
```

**What it shows:**

- Quick action buttons
- Preview of your current meal plan (if you have one)
- Summary of meals, calories, protein

---

### 2. **Generate Meal Plan** (`/planner/generate-meal-plan`)

A simple form to create a new AI meal plan:

```
┌─────────────────────────────────┐
│ Generate Your Meal Plan         │
├─────────────────────────────────┤
│                                 │
│ How many days? [7]──────────    │
│                                 │
│ Target Calories: [2000] kcal    │
│                                 │
│ Target Protein: [150] g         │
│ (30% of calories)               │
│                                 │
│ Dietary Restrictions (optional) │
│ [vegetarian, gluten-free]       │
│                                 │
│ [Cancel]  [Generate Plan ✨]    │
│                                 │
└─────────────────────────────────┘
```

**Form inputs:**

- `daysToGenerate` - How many days of meals
- `targetCalories` - Daily calorie target
- `targetProtein` - Daily protein target
- `dietaryRestrictions` - Comma-separated list

**Backend flow:**

1. Form → `POST /api/meal-plans/ai/generate-and-save`
2. Backend runs **Phase 3-4** (AI orchestration + persistence)
3. Returns `MealPlanId`
4. UI navigates to detail page

---

### 3. **Meal Plan Detail** (`/planner/meal-plan/:mealPlanId`)

Shows a complete generated plan:

```
┌─────────────────────────────────┐
│ Meal Plan Jan 15 → Jan 22       │
│ 7 days of meals                 │
├─────────────────────────────────┤
│                                 │
│ [Day1] [Day2] [Day3] ...       │
│                                 │
│ Calories: 2000 | Protein: 150g  │
│ Meals: 4                        │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ BREAKFAST - Oatmeal Bowl    │ │
│ │ 350 cal • 12g protein       │ │
│ │ 52g carbs • 8g fat          │ │
│ └─────────────────────────────┘ │
│ ┌─────────────────────────────┐ │
│ │ LUNCH - Grilled Chicken     │ │
│ │ 450 cal • 45g protein       │ │
│ │ 35g carbs • 12g fat         │ │
│ └─────────────────────────────┘ │
│                                 │
└─────────────────────────────────┘
```

**Features:**

- Day selector buttons
- Daily nutrition summary
- All 4 meals per day
- Full macronutrient breakdown
- Meal type, title, cuisine, time

---

### 4. **Log Meals Today** (`/planner/today`)

_Existing CheckInPage - for tracking what you actually eat_

---

## The 4 Backend Phases

This is now **crystal clear in the UI**:

| Phase   | What                 | When                                   | Endpoint                                    |
| ------- | -------------------- | -------------------------------------- | ------------------------------------------- |
| **1**   | Function Calling     | Calculate nutrition for recipes        | (internal)                                  |
| **2**   | Semantic Search      | Find recipes matching preferences      | (internal)                                  |
| **3**   | Sequential Prompting | Orchestrate: search → score → assemble | `POST /api/meal-plans/ai/generate`          |
| **3+4** | AI + Persistence     | Generate + save to database            | `POST /api/meal-plans/ai/generate-and-save` |

**SimpleMealGeneratorPage uses Phase 3+4** because :

- User generates a plan → it should be saved
- No need for intermediate step
- Clear expectation: "Generate" = "Save"

---

## Data Flow

```
User fills form
  ↓
[Calories: 2000, Protein: 150, Days: 7, Dietary: "vegan"]
  ↓
POST /api/meal-plans/ai/generate-and-save
  ├─ Phase 1: Function calling (nutrition math)
  ├─ Phase 2: Semantic search (find recipes)
  ├─ Phase 3: Sequential prompting (CLI orchestrates)
  └─ Phase 4: Save to database
  ↓
Returns: { mealPlanId: "123", days: [...], startDate, endDate }
  ↓
Navigate to /planner/meal-plan/123?newlyGenerated=true
  ↓
Show MealPlanDetailPage with success banner
```

---

## Key Files

- **MealPlansHubPage.tsx** - Hub with action buttons
- **SimpleMealGeneratorPage.tsx** - Generation form
- **MealPlanDetailPage.tsx** - Plan display
- **App.tsx** - Routes updated to wire them together

All use existing API helpers:

- `getLatestMealPlan(token)` - Get latest plan for user
- `getMealPlanById(mealPlanId, token)` - Get specific plan
- Backend endpoint `/api/meal-plans/ai/generate-and-save` - Do generation + save

---

## No More Confusion

Before:

- "What do I click to generate?" ← Mixed into one giant component
- "Is this real data or demo?" ← No clear indication
- "Wait, where's my plan?" ← UI didn't show it

After:

- **Hub** = overview of all actions
- **Generate** = dedicated form, obvious what it does
- **View** = displays saved plan with clarity
- **Log** = separate workflow for tracking meals
