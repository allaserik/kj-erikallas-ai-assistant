# Meal Plan Generation Architecture - Complete Explanation

> This document explains exactly what we're using, how AI is integrated, and how it all works together.

## The 4 Phases

Your meal plan generation happens in **4 sequential phases**:

### Phase 1: Function Calling (Nutrition Calculation)

**What:** Calculate nutrition facts for recipes
**How:** Using `FunctionCallingHandler`
**When:** After recipes are selected, calculate their macros
**Tools Used:**

- `calculate_nutrition()` function - takes ingredients + servings
- `NutritionCalculationService` - looks up ingredient nutrition from database

**Example:**

```
Input:
  - 200g chicken breast
  - 100g rice
  - 15ml olive oil
  - Servings: 2

Function Call: calculate_nutrition({...})

Output:
  - Calories: 425 kcal
  - Protein: 45g
  - Carbs: 32g
  - Fats: 12g
```

---

### Phase 2: Semantic Search + RAG (Find Recipes)

**What:** Find recipes that match user preferences
**How:** Using `RAGRetrieverService` + `VectorEmbeddingService`
**When:** When generating a meal plan, search for recipes
**Tools Used:**

- **Vector Embeddings:** Convert recipe text to vectors (e.g., "healthy breakfast" → numerical vector)
- **Cosine Similarity:** Compare vectors to find semantically similar recipes
- **Database Search:** Query recipes table for keyword matches
- **Re-ranking:** Sort recipes by semantic similarity score

**How RAG Works:**

```
User Query: "healthy high-protein breakfast"
                    ↓
Step 1: Convert query to vector embedding
Step 2: Keyword search in database → 30 results
Step 3: Convert each result to vector embedding
Step 4: Calculate cosine similarity between query vector and recipe vectors
Step 5: Sort by similarity score (highest first)
Step 6: Return top 15 recipes
```

**What Gets Retrieved:**

- Recipe title, cuisine, meal type
- Ingredients list
- Cooking time, difficulty
- Nutrition facts
- Dietary tags (vegetarian, gluten-free, etc.)

---

### Phase 3: Sequential Prompting (AI Orchestration)

**What:** Multi-step logical orchestration to assemble meal plans
**How:** Using `SequentialPromptingService`
**When:** After recipes are retrieved, intelligently combine them into daily plans
**Tools Used:**

- Step 1: Extract and normalize user preferences
- Step 2: Retrieve recipes via RAG (Phase 2)
- Step 3: Score recipes based on nutrition alignment + preferences
- Step 4: Assemble recipes into daily meal combinations
- Step 5: Aggregate nutrition across all days

**The 5-Step Process:**

```
┌─────────────────────────────────────────────────────────────┐
│ Phase 3: Sequential Prompting (Orchestration)              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ Step 1: Parse Preferences                                 │
│  Input:  numberOfDays=7, calorieTarget=2000,              │
│          proteinTarget=150g, dietaryPrefs=["vegan"]       │
│  Output: Normalized preferences map                        │
│                                                             │
│ Step 2: Retrieve Recipes via RAG (Phase 2)                │
│  Query:  "vegan breakfast", "vegan lunch", "vegan dinner" │
│  Output: ~45 recipes retrieved (15 per meal type)         │
│                                                             │
│ Step 3: Score Recipes                                      │
│  Scoring Factors:                                          │
│    - Calorie alignment (target: 500 cal for breakfast)    │
│    - Protein content (boost if meets target)              │
│    - Dietary tag matching (vegan = +boost)                │
│    - Cuisine preference matching                          │
│  Result: Each recipe gets a score (0-2.0)                │
│  Filter: Keep only scores > 0.3                           │
│                                                             │
│ Step 4: Assemble Meal Plan                                │
│  Logic: For each day 1-7:                                 │
│    - Select 1 breakfast (round-robin from top scorers)   │
│    - Select 1 lunch                                       │
│    - Select 1 dinner                                      │
│    - Select 1 snack                                       │
│  Result: 7 daily meal combinations                        │
│                                                             │
│ Step 5: Calculate Total Nutrition                         │
│  Add up all meals across all days:                        │
│    - Total calories for week                             │
│    - Total protein for week                              │
│    - Per-day averages                                    │
│  Result: Final MealPlanResponse                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Important:** This phase does **NOT** use Claude AI. It's pure logic orchestration:

- No API calls to Claude
- No prompting
- Pure deterministic scoring and selection
- Fully reproducible

---

### Phase 4: Persistence (Save to Database)

**What:** Save the generated meal plan to database
**How:** Using `MealPlanAIService`
**When:** After successful generation, persist to database
**Tools Used:**

- `MealPlanRepository` - Save the meal plan entity
- `MealPlanMealRepository` - Save individual meal associations

**What Gets Saved:**

```
MealPlanEntity (1 record):
  - ID, UserID, PeriodType (daily/weekly)
  - StartDate, EndDate
  - Status: "GENERATED"
  - Source: "AI_GENERATED"
  - CreatedAt, UpdatedAt

MealPlanMealEntity (multiple records - 28 for 7 days):
  - ID, MealPlanID, Date
  - MealType (breakfast/lunch/dinner/snack)
  - RecipeID (link to Recipe)
  - Position (order in day)
  - CreatedAt, UpdatedAt
```

---

## How All 4 Phases Work Together

```
User Request:
  {
    "numberOfDays": 7,
    "calorieTarget": 2000,
    "proteinTargetG": 150,
    "dietaryPreferences": ["vegetarian"],
    "query": "Generate vegetarian meal plan"
  }

     ↓ [PHASE 3 STARTS]

Phase 3, Step 1: Extract Preferences
  → Preferences = {days: 7, cals: 2000, protein: 150, prefs: ["vegetarian"]}

Phase 3, Step 2: Retrieve Recipes via RAG
  → Query 1: "vegetarian breakfast"
     → [Phase 2: RAG Search] → 15 breakfast recipes ✓
  → Query 2: "vegetarian lunch"
     → [Phase 2: RAG Search] → 15 lunch recipes ✓
  → Query 3: "vegetarian dinner"
     → [Phase 2: RAG Search] → 15 dinner recipes ✓
  → All recipes now have: title, ingredients, nutrition, dietary tags

Phase 3, Step 3: Score Recipes
  For each of 45 recipes:
    → Calorie score: target=500, if recipe=480 → score=0.96
    → Protein score: if meets target → +20% boost
    → Dietary score: "vegetarian" tag matches → +boost
    → Final score: e.g., 1.2 (out of 2.0 max)

Phase 3, Step 4: Assemble Meals
  Day 1:
    - Breakfast: Pick #1 breakfast from sorted recipes
    - Lunch: Pick #1 lunch from sorted recipes
    - Dinner: Pick #1 dinner from sorted recipes
    - Nutrition: 500 + 650 + 750 = 1900 cal
  Day 2:
    - Breakfast: Pick #2 breakfast (round-robin for variety)
    - Lunch: Pick #2 lunch
    - Dinner: Pick #2 dinner
    - Nutrition: 520 + 640 + 780 = 1940 cal
  ... repeat for 7 days

Phase 3, Step 5: Aggregate & Build Response
  Total Calories: 13,820 cal (for week)
  Average Per Day: 1,974 cal
  Total Protein: 1,050g
  Average Per Day: 150g

  Return: MealPlanResponse { days: [...], totalCals, totalProtein }

     ↓ [PHASE 4 STARTS]

Phase 4: Save to Database
  → Create MealPlanEntity (1 record)
  → Create MealPlanMealEntity for each meal (28 records)
  → Return saved plan with IDs

     ✓ Generation Complete!
```

---

## Where AI is Used (and NOT Used)

### ❌ **NOT Using AI:**

- **Meal Selection:** We use deterministic scoring, not AI
- **Nutrition Calculation:** We use function calling + database lookup
- **Recipe Search:** We use semantic search + vector embeddings

### ✅ **Could Use AI (but currently don't):**

1. **Alternative approach:** Use Claude to analyze preferences and generate meal combinations
2. **Quality checking:** Use Claude to validate nutritional balance
3. **User personalization:** Use Claude to suggest recipes based on past choices

**Why we don't use Claude for core generation:**

- **Reproducibility:** Deterministic scoring is predictable
- **Cost:** No API calls = lower costs
- **Speed:** No network latency
- **Accuracy:** Database lookups are reliable
- **Consistency:** Same inputs always produce same results

---

## The Recipe Database (RAG Knowledge Base)

### What We Have:

- **500+ recipes** in the database (from cleaned_nutrition_dataset_per100g.csv)
- **Nutrition info** for each: calories, protein, carbs, fats
- **Metadata:** meal type, cuisine, difficulty, cooking time, ingredients

### How RAG Uses It:

```
┌──────────────────────┐
│  Recipe Database     │
│  (500+ recipes)      │
│  - Title             │
│  - Ingredients       │
│  - Nutrition         │
│  - Dietary tags      │
│  - Cuisine           │
│  - Meal type         │
└──────────────────────┘
           ↑
           │ Keyword search + semantic ranking
           │
    ┌──────┴──────┐
    │ RAG Service │
    └──────┬──────┘
           ↓
    User query: "healthy breakfast"
           ↓
    Returns top 15 best matches
```

### Vector Embeddings (Semantic Search):

```
Recipe text: "Oatmeal with berries and honey"
     ↓
VectorEmbeddingService.embed(recipe_text)
     ↓
Vector: [0.23, 0.91, -0.45, 0.12, ...]  (768-d vector)

Query: "healthy breakfast"
     ↓
Vector: [0.19, 0.88, -0.42, 0.15, ...]

Similarity = Cosine(recipe_vector, query_vector) = 0.94 (94% similar!)
```

---

## Error Scenarios & Why Generation Fails

### Scenario 1: No Recipes Retrieved

```
Problem: RAG returns 0 recipes for "vegan lunch"
Cause: No matching recipes in database
Fix: Add more recipes to database or relax search criteria
```

### Scenario 2: Recipes Have No Nutrition Data

```
Problem: Recipe found but nutrition = null
Cause: Incomplete recipe record in database
Fix: Ensure all recipes have nutrition info
```

### Scenario 3: Invalid Request Parameters

```
Problem: calorieTarget = null or negative
Cause: User didn't provide valid input
Fix: Use defaults (2000 cal, 100g protein) and validate
```

### Scenario 4: Null Meal Type in Retrieved Recipe

```
Problem: Recipe.mealType() = null
Cause: Incomplete recipe data
Fix: Ensure all recipes have meal type assigned
```

### Scenario 5: Database Save Fails

```
Problem: Can't create MealPlanEntity
Cause: User ID invalid or database error
Fix: Validate user exists and database is accessible
```

---

## API Endpoint

```
POST /api/meal-plans/ai/generate-and-save
```

**Request:**

```json
{
  "query": "Generate healthy vegetarian meal plan",
  "numberOfDays": 7,
  "calorieTarget": 2000,
  "proteinTargetG": 150,
  "dietaryPreferences": ["vegetarian"],
  "cuisinePreferences": ["Mediterranean", "Asian"],
  "allergies": ["peanuts", "shellfish"]
}
```

**Response (Success):**

```json
{
  "data": {
    "mealPlanId": "550e8400-e29b-41d4-a716-446655440000",
    "startDate": "2024-01-15",
    "endDate": "2024-01-21",
    "days": [
      {
        "date": "2024-01-15",
        "meals": [
          {
            "mealType": "breakfast",
            "recipeTitle": "Oatmeal Bowl with Berries",
            "caloriesKcal": 480,
            "proteinG": 15,
            "carbsG": 65,
            "fatsG": 12
          },
          ...
        ]
      },
      ...
    ]
  }
}
```

**Response (Error):**

```json
{
  "error": "Meal plan generation failed: No recipes found matching vegetarian breakfast criteria"
}
```

---

## Summary

| Phase | What                | Tools                      | Uses AI? |
| ----- | ------------------- | -------------------------- | -------- |
| **1** | Calculate Nutrition | FunctionCalling + Database | ❌ No    |
| **2** | Find Recipes        | RAG + Vector Embeddings    | ❌ No    |
| **3** | Orchestrate Plan    | Sequential Logic + Scoring | ❌ No    |
| **4** | Save Plan           | Database                   | ❌ No    |

**Current State:** We use **intelligent orchestration** (semantic search, scoring, logic) but **NOT AI language models** for the core flow.

**Alternative:** Could use Claude for any step, but the current deterministic approach is better for meal planning (reproducible, cost-effective, fast).
