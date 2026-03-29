# Assignment Requirements vs. Current Implementation

## Your Assignment (from CAL_ASSIGNMENT.md)

The assignment requires 4 main GenAI techniques:

### 1. **Sequential Prompting** (Requirement)

> "Break down complex meal planning into a series of focused steps. Your prompt sequence must have at least 3 steps."

**What we need:**

1. Initial Assessment: Analyze user profile
2. Meal Structure: Design specific meals and timing
3. Recipe Generation OR Nutritional Analysis
   4+ More steps for refinement

**Example from assignment:**

```
PROMPT 1: "Analyze this user profile and recommend a meal strategy..."
RESPONSE: [Strategy with macronutrient targets]

PROMPT 2: "Based on this strategy, create a meal structure..."
RESPONSE: [Structured meal plan]

PROMPT 3: "Generate a recipe for Monday's dinner..."
RESPONSE: [Detailed recipe with nutrition]
```

**What we're currently doing:**
✓ We have sequential steps (extract → retrieve → score → assemble)
✗ **BUT:** NOT using Claude for any of these steps!
✗ **Missing:** We're not sending prompts to Claude at all

---

### 2. **Function Calling** (Requirement)

> "Your application is required to perform all nutritional analysis using Function Calling."

**What we need:**

- Define `calculate_nutrition()` function for AI
- AI calls function with ingredient list
- We calculate and return nutrition
- AI uses result in responses

**We have:**
✓ `FunctionCallingHandler` with proper schema
✓ Function definition for `calculate_nutrition()`
✓ `NutritionCalculationService` to execute it
✗ **Missing:** Never actually calling it! No integration with Claude

---

### 3. **Retrieval-Augmented Generation (RAG)** (Requirement)

> "Combine AI with a knowledge base of recipes. **Document Storage:** Create database with at least 500 recipes and 500 ingredients. **Embedding Creation:** Convert recipes to vector embeddings. **Retrieval Strategy:** Implement relevance-based search. **Augmentation Technique:** Incorporate retrieved recipes into prompts"

**What we need:**

1. ✓ 500+ recipes in database (we have this!)
2. ✓ Vector embeddings (SentenceTransformers implemented)
3. ✓ Relevance-based search (cosine similarity)
4. ✗ **MISSING:** "Incorporate retrieved recipes into prompts" → Send to Claude!

**We have:**

```
✓ RAGRetrieverService
✓ VectorEmbeddingService
✓ Recipe database with 500+ recipes
✗ Not feeding recipes to Claude prompts!
```

---

### 4. **Prompt Engineering** (Requirement)

> "Your meal planning features must integrate:
>
> - Use few-shot examples
> - Apply iterative refinement
> - Use sequential prompting
> - Parameter optimization (temperature, top-p, max tokens)
> - Error handling and API reliability"

**We're missing all of this!**

---

## The Gap: What's Missing

## Current Architecture

```
User → Request
  ↓
[Phase 3: Sequential Logic] ← Pure Java, NO Claude
  ├─ RAG Retrieval (semantic search to database)
  ├─ Scoring (deterministic formula)
  └─ Meal Assembly (round-robin selection)
  ↓
[Phase 4: Persistence] ← Save to DB
  ↓
Return meal plan
```

**Problem:** No AI involved at all! All logic is deterministic.

---

## What **Should** Happen

```
User → Request
  ↓
[PROMPT 1: Analyze User Profile]
Claude: "Given this user (age 30, vegetarian, 2000 cal target, high protein),
         what meal strategy would you recommend?"
Response: "Strategy: 4 meals/day, 25% protein focus, Mediterranean cuisine"
  ↓
[RAG: Retrieve Recipes] (Phase 2)
RAGRetriever: Search for "Mediterranean high-protein vegetarian"
Response: [15 breakfast recipes, 15 lunch recipes, etc.]
  ↓
[PROMPT 2: Generate Meal Combinations]
Claude: "Given your strategy and these recipes [list of top recipes],
         create a 7-day meal plan that balances variety and nutrition."
Response: "Day 1: Breakfast - Greek Yogurt Bowl, Lunch - Falafel Wrap..."
  ↓
[FUNCTION CALL: Calculate Nutrition]
Claude: "calculate_nutrition(ingredients: [...], servings: 2)"
We execute: Returns {calories: 425, protein: 18, carbs: 32, fat: 15}
Claude uses result: "This meal provides 425 kcal with 18g protein..."
  ↓
[PROMPT 3: Validate & Refine]
Claude: "Review this plan for nutritional balance and make suggestions"
Response: "Plan looks good but Day 4 is 150kcal low, suggest swapping..."
  ↓
Return optimized meal plan
```

---

## How We Should Integrate AI (Claude)

### **Option 1: Full AI-Driven (Assignment Requirement)**

```java
@PostMapping("/generate-and-save")
public ApiSuccess<MealPlanModels.GenerateResponse> generateAndSaveMealPlan(...) {
    // Step 1: Use Claude to analyze user preferences
    String userAnalysis = claudeService.analyzeUserProfile(request.preferences());

    // Step 2: Retrieve relevant recipes from RAG
    List<Recipe> recipes = ragService.retrieveRecipes(userAnalysis);
    String recipeContext = ragService.formatAsContext(recipes);

    // Step 3: Use Claude to generate meal plan
    String mealPlanDraft = claudeService.generateMealPlan(
        userAnalysis,
        recipeContext,   // ← Augmented with real recipes!
        request.numberOfDays()
    );

    // Step 4: Use function calling for nutrition
    String mealPlanWithNutrition = claudeService.calculateNutrition(
        mealPlanDraft,
        functionDefinitions  // ← Claude calls our functions
    );

    // Step 5: Save to database
    var savedPlan = saveToDB(mealPlanWithNutrition);
    return ApiSuccess.of(response);
}
```

### **Option 2: Hybrid (Current + AI)**

Keep current logic BUT use Claude to:

- Validate meal combination quality
- Suggest alternatives when no recipes found
- Improve nutritional balance
- Personalize recommendations

---

## The Real Question

### **What do you want?**

1. **Fully AI-driven generation** (Claude makes all decisions)
   - Uses all 4 GenAI techniques
   - Meets assignment requirements
   - Slower, more expensive, less predictable
   - Better personalization potential

2. **Keep current deterministic approach** (Claude optional)
   - Fast, cheap, reproducible
   - Meets performance requirements
   - Missing assignment's GenAI requirements
   - Better for consistent results

---

## Assignment Compliance Checklist

### **Sequential Prompting**

- [ ] PROMPT 1: User profile analysis
- [ ] PROMPT 2: Meal strategy generation
- [ ] PROMPT 3: Recipe generation OR validation
- [ ] At least 3 prompts total

### **Function Calling**

- [ ] Define `calculate_nutrition()` (✓ Done)
- [ ] Claude calls it during generation (✗ Missing)
- [ ] Handle results and incorporate into response (✗ Missing)

### **RAG (Retrieval-Augmented Generation)**

- [ ] 500+ recipes in database (✓ Have)
- [ ] Vector embeddings (✓ Have)
- [ ] Semantic search (✓ Have)
- [ ] **Augment prompts with retrieved recipes** (✗ Missing)

### **Prompt Engineering**

- [ ] Few-shot examples (✗ Missing)
- [ ] Iterative refinement (✗ Missing)
- [ ] Temperature/top-p optimization (✗ Missing)
- [ ] Error handling (✗ Missing)

---

## Why Generation Fails (Current State)

Since we're NOT using Claude:

1. **Recipe retrieval might return 0 results** → No fallback
2. **Scoring might eliminate all recipes** → No alternative
3. **No intelligent reassembly** → Can't fix problems
4. **No validation loop** → Bad meals accepted
5. **Silent error handling** → User sees `null` with no explanation

With Claude, we could:

1. Ask: "Why are there no breakfast recipes?"
2. Have Claude suggest alternatives
3. Ask Claude to validate the plan
4. Have Claude explain any issues
5. Provide intelligent fallbacks

---

## My Recommendation

**To fix "meal plan generation failed":**

1. **Immediate fix:** Better error handling
   - Log actual errors
   - Return meaningful messages
   - Check if recipes exist in DB

2. **Medium term:** Add Claude for validation
   - Ask Claude to validate generated plan
   - Use Claude for intelligent fallbacks
   - Incorporate Claude's suggestions

3. **Long term:** Full AI integration
   - Implement all 3+ sequential prompts
   - Use function calling for nutrition
   - Full RAG augmentation
   - Meet all assignment requirements

---

## Example: What a Claude-Enhanced Version Would Look Like

```json
POST /api/meal-plans/ai/generate-and-save

REQUEST:
{
  "query": "Generate vegetarian dinner",
  "numberOfDays": 7,
  "calorieTarget": 1500,
  "proteinTargetG": 100,
  "dietaryPreferences": ["vegetarian"],
  "allergies": ["shellfish"]
}

STEP 1: Claude analyzes preferences
INPUT: "User is vegetarian, wants 1500 cal dinners, high protein, allergic to shellfish"
OUTPUT: "Strategy: Focus on legume-based proteins. Suggest Mediterranean + Asian cuisines. Target 25-35g protein per meal."

STEP 2: RAG retrieves recipes matching strategy
QUERIES: "vegetarian high-protein dinner Mediterranean", "vegetarian legumes Asian"
RESULTS: [45 recipes, ranked by relevance]

STEP 3: Claude generates meal plan with recipe context
PROMPT: "Based on this vegetarian strategy and these recipes [list], generate 7 dinners"
OUTPUT: "Day 1 Dinner: Lentil Bolognese with whole wheat pasta (matched to recipe ID: r123) ..."

STEP 4: Claude calls function for nutrition
FUNCTION_CALL: calculate_nutrition(
  ingredients: [{name: "lentils", quantity: 200, unit: "g"}, ...],
  servings: 2
)
RESULT: {calories: 520, protein: 28g, carbs: 65g, fat: 8g}

STEP 5: Claude validates plan
VALIDATION: "This plan meets your goals - 7-day average 1480 kcal and 105g protein"

RESPONSE:
{
  "mealPlanId": "...",
  "aiNotes": "Plan emphasizes legume-based proteins for optimal nutrition",
  "days": [
    {
      "date": "2024-01-15",
      "meals": [{
        "mealType": "dinner",
        "recipeTitle": "Lentil Bolognese",
        "caloriesKcal": 520,
        "proteinG": 28,
        "aiSuggestion": "Pairs well with leafy greens for micronutrient boost"
      }]
    }
  ]
}
```

---

## Bottom Line

**Current System:**

- ✓ Works when recipes exist in DB
- ✗ Fails silently when no recipes found
- ✗ Not using Claude/AI at all
- ✗ Doesn't meet assignment's GenAI requirements

**Why it fails:**

1. Recipe DB might be empty
2. Recipes might lack nutrition data
3. Query might not match any recipes
4. Error handling is hidden (returns null)

**What needs to happen:**

1. Check/load recipe data
2. Add Claude for intelligent fallbacks
3. Implement sequential prompting
4. Use function calling for nutrition
5. Better error messages
