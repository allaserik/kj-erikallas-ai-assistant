# UI Navigation Flow - New Structure

## Quick Start

When users click "Planner" in the sidebar:

```
║ SIDEBAR
║ ├─ Dashboard
║ ├─ Planner ──────────────────────────────────────┐
║ ├─ Trends                                         │
║ └─ ...                                            │
║                                                    │
║ MAIN CONTENT AREA                                │
║──────────────────────────────────────────────────┘
║
║ [Overview] [Generate Plan] [Log Today] [Goals] ...
║
║ ┌───────────────────────────────────────────────┐
║ │        MEAL PLANS HUB (First screen)          │
║ │                                               │
║ │   ✨ Generate    📅 View Current   🍽️ Log   │
║ │                                               │
║ │   Current Plan Preview (3 days shown)        │
║ └───────────────────────────────────────────────┘
```

---

## 3 Main Workflows

### Workflow 1: Generate a New Plan

```
Click [✨ Generate Plan]
         ↓
User fills form:
  - Days: 7
  - Calories: 2000
  - Protein: 150g
  - Dietary: "vegan"
         ↓
Click "Generate Plan"
         ↓
Backend: POST /api/meal-plans/ai/generate-and-save
  ├─ Phase 1: Calculate nutrition
  ├─ Phase 2: Search recipes
  ├─ Phase 3: AI orchestrates meal selection
  └─ Phase 4: Save to database
         ↓
Success! View generated plan
├─ Day 1: Breakfast, Lunch, Dinner, Snack
├─ Day 2: ...
└─ Day 3: ...
```

### Workflow 2: View Current Plan

```
Hub shows:
  [📅 View Current Plan]
         ↓
Click it → See full 7-day plan
     Day 1 | Day 2 | Day 3 | ... Day 7
         ↓ (select day)
Shows all meals + nutrition for that day
```

### Workflow 3: Log What You Ate Today

```
Click [🍽️ Log Meals]
         ↓
Goes to: /planner/today (CheckInPage)
         ↓
User logs meals as they eat
```

---

## File Structure

```
frontend/src/features/planner/
├─ MealPlansHubPage.tsx              ← Overview with 3 actions
├─ SimpleMealGeneratorPage.tsx        ← Generation form
├─ MealPlanDetailPage.tsx             ← Plan display
├─ PlannerPage.tsx (updated)          ← Layout + tabs
├─ CheckInPage.tsx (existing)         ← Log meals today
├─ (other existing pages...)
```

---

## What Changed

| Before                         | After                                        |
| ------------------------------ | -------------------------------------------- |
| Confusing giant component      | 3 focused pages                              |
| Unclear what "demo" means      | Clear: "Generate" means "AI creates + saves" |
| `/planner/today` as main       | `/planner` as hub first                      |
| 8 tabs trying to do everything | 6 focused tabs + dedicated workflows         |
| User asks: "Where do I start?" | Clear entry point: Overview hub              |

---

## Backend Integration

| Page          | Endpoint                               | Method | Description                |
| ------------- | -------------------------------------- | ------ | -------------------------- |
| **Hub**       | `/api/meal-plans/latest`               | GET    | Fetch latest plan for user |
| **Generator** | `/api/meal-plans/ai/generate-and-save` | POST   | AI generation + save       |
| **Detail**    | `/api/meal-plans/{mealPlanId}`         | GET    | Fetch specific plan        |
| **Log**       | Existing endpoints                     | -      | (no changes)               |

---

## Testing the Flow

1. Go to app → Click "Planner" (sidebar)
2. See **MealPlansHubPage** with 3 action buttons
3. Click "Generate New Plan"
4. Fill form → Click Generate
5. Backend runs Phases 1-4
6. UI shows generated plan details

---

## Why This Is Better

✅ **Clear Separation**: Generate vs. View vs. Log are separate workflows
✅ **Obvious UX**: Hub shows exactly what you can do
✅ **No Confusion**: No mixing "demo" vs "real" - you generate → it's saved
✅ **Scalable**: Easy to add new features (e.g., "Regenerate this meal")
✅ **User-Friendly**: Beginner can understand flow immediately
