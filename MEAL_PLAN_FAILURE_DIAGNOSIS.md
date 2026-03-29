# Meal Plan Generation Failure Diagnosis

## The Problem 🐛

When you try to generate a meal plan, it fails silently. The backend catches the exception but returns `null` wrapped in a success response, so the UI gets:

```json
{
  "data": null
}
```

This is happening because of **poor error handling** in `AIRecipeGenerationController.java` at line 185:

```java
} catch (Exception e) {
    return ApiSuccess.of(null);  // ❌ Silent failure!
}
```

---

## How the Generation Flow Actually Works

Let me map out the exact flow and where failures can happen:

### **Step 1: User Request**

```json
{
  "query": "Generate vegetarian meal plan",
  "numberOfDays": 7,
  "calorieTarget": 2000,
  "proteinTargetG": 150,
  "dietaryPreferences": ["vegetarian"],
  "cuisinePreferences": null,
  "allergies": null
}
```

**Potential Failure #1:** Null or empty query

- **Effect:** RAG semantic search has nothing to search for
- **Current Error:** Logged as warning, continues with empty results

---

### **Step 2: Sequential Prompting - Phase 3, Step 1**

```java
// Extract and normalize preferences
var preferences = extractPreferences(request);
```

**What happens:**

```java
Map<String, Object> preferences = Map.of(
    "numberOfDays", 7,
    "mealType", "all",
    "calorieTarget", 2000,
    "proteinTargetG", 150,
    "dietaryPreferences", ["vegetarian"],
    "cuisinePreferences", [],
    "allergies", []
);
```

**Potential Failure #2:** Invalid calorieTarget (null, negative, zero)

- **Current behavior:** Defaults to 2000
- **Status:** ✓ Handled

---

### **Step 3: RAG Retrieval - Phase 2, Step 2**

```java
// Build queries: baseQuery + breakfast, lunch, dinner
var queries = [
    "Generate vegetarian meal plan breakfast",
    "Generate vegetarian meal plan lunch",
    "Generate vegetarian meal plan dinner"
];

// For each query, retrieve top 15 recipes
for (String query : queries) {
    var recipes = ragRetrieverService.retrieveRecipesByQuery(query, 15);
}
```

**Potential Failure #3:** No recipes found in database

- **Symptom:** `recipes.size() == 0`
- **Current handling:** Continues, but subsequent steps see empty list
- **Effect:** Meal plan has no meals, all nutritional values = 0

**Let's trace what happens next if NO recipes retrieved:**

```java
List<RecipeDetail> allRecipes = new ArrayList<>();  // Empty!

// Later: try to group by meal type
Map<String, List<ScoredRecipe>> recipesByMealType =
    scoredRecipes  // Empty!
    .stream()
    .collect(Collectors.groupingBy(ScoredRecipe::mealType));

// Later: try to assemble meals
for (int day = 1; day <= numberOfDays; day++) {
    for (Map.Entry<String, List<ScoredRecipe>> entry : recipesByMealType.entrySet()) {
        if (entry.getValue().isEmpty()) continue;  // ← Skips all meals!
    }
    // Result: dailyMeals is EMPTY
    days.add(new DailyNutritionSummary(..., dailyMeals));  // ← Empty meals!
}
```

**Result:** Meal plan with 7 empty days, zero calories, zero protein

---

### **Step 4: Save to Database - Phase 4**

```java
// Create MealPlanEntity
var mealPlan = createMealPlanEntity(userId, response);
mealPlan = mealPlanRepository.save(mealPlan);  // ✓ Saved

// Create MealPlanMealEntity for each day/meal
var mealEntities = new ArrayList<MealPlanMealEntity>();
for (var dayNutrition : response.days()) {
    for (var meal : dayNutrition.meals()) {  // ← Empty!
        var mealEntity = new MealPlanMealEntity(...);
        mealEntities.add(mealEntity);
    }
}
```

**Result:** Meal plan saved with 0 meals

---

### **Step 5: Build Response**

```java
// This reconstructs the meal response
var response = mealPlanAIService.buildGenerateResponse(savedPlan, mealPlan);
```

**Result:** Returns a GenerateResponse with:

```json
{
  "mealPlanId": "550e8400-...",
  "startDate": "2024-01-15",
  "endDate": "2024-01-21",
  "totalMeals": 0,  // ← ZERO!
  "days": [
    {
      "date": "2024-01-15",
      "meals": []  // ← EMPTY!
    },
    ...
  ]
}
```

---

## Where Failures Actually Happen

### **Failure Scenario A: No Recipes in Database**

**Detection:**

```
Check: SELECT COUNT(*) FROM recipe WHERE meal_type = 'breakfast'
If 0 → Generation will fail silently
```

**What to check:**

```sql
-- Check recipe counts
SELECT meal, COUNT(*) FROM recipe GROUP BY meal;
-- Expected: breakfast > 0, lunch > 0, dinner > 0, snack > 0

-- Check nutrition data
SELECT COUNT(*) FROM recipe WHERE nutrition IS NOT NULL;
-- Expected: close to total recipe count

-- Check recipe with all fields
SELECT * FROM recipe LIMIT 5;
```

**Fix:**

- Load recipe data: `INSERT INTO recipe ...` (from CSV)
- Verify nutrition data is populated

---

### **Failure Scenario B: Recipe Missing Nutrition Data**

**Detection:**

```java
// In scoreRecipe()
if (recipe.nutrition() == null) {
    // Can't score, skips recipe
}
```

**Effect:**

- Recipe retrieved but nutrition() is null
- Scoring gives default values
- Nutrition aggregation shows 0 calories, 0 protein

**Fix:**

- Ensure all recipes have nutrition data
- Add default nutrition values if missing

---

### **Failure Scenario C: Poor Error Handling (Silent Failure)**

**Current Code:**

```java
@PostMapping("/generate-and-save")
public ApiSuccess<MealPlanModels.GenerateResponse> generateAndSaveMealPlan(...) {
    try {
        var mealPlan = sequentialPromptingService.generateMealPlan(request);
        var savedPlan = mealPlanAIService.saveMealPlan(user.getId(), mealPlan);
        var response = mealPlanAIService.buildGenerateResponse(savedPlan, mealPlan);
        return ApiSuccess.of(response);
    } catch (Exception e) {
        return ApiSuccess.of(null);  // ❌ Returns null, client doesn't know why!
    }
}
```

**Better Error Handling:**

```java
@PostMapping("/generate-and-save")
public ResponseEntity<?> generateAndSaveMealPlan(...) {
    try {
        var mealPlan = sequentialPromptingService.generateMealPlan(request);
        if (mealPlan.days().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiError(
                "No recipes found matching your criteria. Try adjusting filters."
            ));
        }

        var savedPlan = mealPlanAIService.saveMealPlan(user.getId(), mealPlan);
        var response = mealPlanAIService.buildGenerateResponse(savedPlan, mealPlan);
        return ResponseEntity.ok(ApiSuccess.of(response));
    } catch (Exception e) {
        log.error("Meal plan generation failed", e);
        return ResponseEntity.status(500).body(new ApiError(
            "Meal plan generation failed: " + e.getMessage()
        ));
    }
}
```

---

## What We're Using in Each Phase

### **Phase 1: Function Calling**

- **Tool:** `FunctionCallingHandler` + `NutritionCalculationService`
- **Not currently used in flow** (could be for individual recipe nutrition)
- **Status:** ✓ Implemented

### **Phase 2: Semantic Search + RAG**

- **Tool:** `RAGRetrieverService` + `VectorEmbeddingService`
- **How:**
  1. Convert recipe text to vectors (768-dimensional embeddings)
  2. Search database for keyword matches (15 results per meal type)
  3. Rank by vector similarity (cosine similarity)
  4. Return top-ranked recipes
- **Uses:** SentenceTransformers for embeddings
- **Status:** ✓ Implemented

### **Phase 3: Sequential Prompting (Orchestration)**

- **Tool:** `SequentialPromptingService`
- **What it does (NOT using Claude AI):**
  1. Extract preferences (normalize)
  2. Retrieve recipes via RAG
  3. Score recipes based on nutrition alignment
  4. Assemble into daily combinations (round-robin for variety)
  5. Aggregate nutrition across days
- **Scoring formula:**
  - Base: 1.0
  - Calorie alignment: scale by deviation
  - Protein bonus: +20% if meets target
  - Dietary match: +10% per matching tag
  - Cuisine match: +15% bonus
  - Cap at 2.0, filter < 0.3
- **Uses:** Pure Java logic, NO AI
- **Status:** ✓ Implemented

### **Phase 4: Persistence**

- **Tool:** `MealPlanAIService` + Spring Data JPA
- **What it saves:**
  - MealPlanEntity: 1 record (overall plan)
  - MealPlanMealEntity: N records (individual meals)
- **Status:** ✓ Implemented

---

## Current Integration Architecture

```
┌─────────────────────────────────────────────┐
│ User Request                                │
│ POST /api/meal-plans/ai/generate-and-save   │
└────────────────┬────────────────────────────┘
                 │
        ┌────────▼────────┐
        │ Phase 3 START   │
        │ Sequential      │
        │ Prompting       │
        └────────┬────────┘
                 │
        ┌────────▼────────┐
        │ Phase 2 CALL    │
        │ RAG Retrieval   │
        │ - embed query   │
        │ - keyword search│
        │ - rank by sim   │
        └────────┬────────┘
                 │
        ┌────────▼────────┐
        │ Recipe DB       │
        │ 500+ recipes    │
        │ + nutrition     │
        └────────┬────────┘
                 │
        ┌────────▼────────┐
        │ Phase 3 CONT    │
        │ - Score recipes │
        │ - Assemble plan │
        │ - Aggregate     │
        └────────┬────────┘
                 │
        ┌────────▼────────┐
        │ Phase 4 START   │
        │ Persistence     │
        │ - Save entities │
        │ - Save meals    │
        └────────┬────────┘
                 │
        ┌────────▼────────┐
        │ Database        │
        │ Saved!          │
        └────────┬────────┘
                 │
    ┌────────────▼─────────────┐
    │ Return generated plan    │
    │ with mealPlanId + meals  │
    └─────────────────────────┘
```

---

## Summary of What's Being Used

| Component                | Purpose          | Technology                               | AI?        |
| ------------------------ | ---------------- | ---------------------------------------- | ---------- |
| **RecipeDB**             | Knowledge base   | PostgreSQL, 500+ recipes                 | ❌         |
| **RAG Service**          | Recipe retrieval | Vector embeddings (SentenceTransformers) | ✓ Semantic |
| **Sequential Prompting** | Meal assembly    | Java logic (scoring, selection)          | ❌         |
| **Function Calling**     | Nutrition calc   | Database lookup                          | ❌         |
| **Persistence**          | Save plan        | JPA, Database                            | ❌         |

**Important:** We're **NOT** using Claude AI for meal planning. We're using:

- **Vector embeddings** (semantic similarity)
- **Deterministic scoring** (logic-based)
- **Database lookups** (reliable nutrition data)

If generation fails, it's likely because:

1. ❌ No recipes in database
2. ❌ Recipes missing nutrition data
3. ❌ Database connectivity issue
4. ❌ Exception swallowed by poor error handling

---

## Next Steps to Debug

1. **Check database:**

   ```bash
   docker exec postgres psql -U erikallas -d numbers_dont_lie -c "SELECT COUNT(*) FROM recipe;"
   ```

2. **Check recipe data:**

   ```bash
   docker exec postgres psql -U erikallas -d numbers_dont_lie -c "SELECT * FROM recipe LIMIT 3;"
   ```

3. **Enable debug logging:**

   ```yaml
   # application.yaml
   logging:
     level:
       com.erikallas.ndl.ai: DEBUG
   ```

4. **Test the endpoint:**

   ```bash
   curl -X POST http://localhost:8080/api/meal-plans/ai/generate-and-save \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $TOKEN" \
     -d '{
       "query": "Generate meal plan",
       "numberOfDays": 3,
       "calorieTarget": 1500
     }'
   ```

5. **Check logs:**
   ```bash
   docker logs backend | grep -i "meal plan\|error\|exception"
   ```
