# Calories Project (CAL) - Assignment Progress Checklist

## Documentation & Setup

- [ ] README contains clear overview, setup instructions, usage guide
- [ ] README documents prompt engineering strategy
- [ ] README documents AI model selection rationale
- [ ] README documents data model decisions
- [ ] README documents error handling methods

## User Preferences & Nutrition

- [ ] Supports at least 15 different dietary preferences
- [ ] Supports at least 10 food allergies/intolerances
- [ ] Collects dietary preferences
- [ ] Collects allergies and intolerances
- [ ] Collects disliked ingredients
- [ ] Collects cuisine preferences
- [ ] Collects calorie and macronutrient targets
- [ ] Collects meal frequency and timing
- [ ] Reuses info from Project 1 (pre-fills input fields, no duplicates)
- [ ] All dates/times use ISO 8601 standard
- [ ] Users can specify timezone

## Meal Planning - Generation & Structure

- [ ] System generates daily meal plans
- [ ] System generates weekly meal plans
- [ ] Customizable meal structures (number of meals/snacks)
- [ ] Each meal has: name, meal type (breakfast/lunch/dinner/snack), nutritional info
- [ ] Flexible meal structures supported (e.g., 3 meals + 2 snacks)
- [ ] Meal details include default time information
- [ ] Sequential prompting with at least 3 distinct steps implemented
- [ ] Student can explain logical progression of prompt steps

## Meal Planning - Customization

- [x] Alternative meal suggestions provided
- [x] Users can swap generated meals in plan
- [x] Users can manually add custom meals
- [ ] Users can regenerate entire meal plan while preserving preferences (PHASE 4+)
- [ ] Users can regenerate individual meals while preserving preferences (PHASE 4+)
- [x] Users can reorder meals within a day
- [x] Users can swap meals between different days
- [x] **NEW:** Users can delete meals from plan

## Shopping List

- [ ] Shopping list can be generated from specific meal
- [ ] Shopping list can be generated from entire meal plan
- [ ] Items automatically categorized to at least 5 meaningful categories
- [ ] Users can adjust quantities in shopping list
- [ ] Users can exclude items from shopping list

## Recipe Management

- [ ] Recipes searchable by name
- [ ] Recipes searchable by ingredients
- [ ] Recipes searchable by cuisine
- [ ] Recipes filterable by dietary restrictions
- [ ] Recipes filterable by allergies
- [ ] Recipes filterable by ingredients
- [ ] Recipes filterable by calories
- [ ] Recipes filterable by macronutrients
- [ ] Recipes filterable by preparation time
- [ ] Recipes display ingredients
- [ ] Recipes display step-by-step instructions
- [ ] Recipes display nutritional information
- [ ] Nutritional information is visualized (chart/graph)
- [ ] Variety of custom recipes can be generated with AI
- [ ] Ingredients in recipes can be substituted with AI
- [ ] Substitution can be based on availability preference
- [ ] Substitution can be based on user preferences
- [ ] Portions adjustable by modifying serving size
- [ ] Ingredient quantities auto-recalculate on serving change

## Data Structures - Measurements

- [ ] All measurements standardized: grams for solids
- [ ] All measurements standardized: milliliters for liquids
- [ ] All measurements standardized: kilocalories for energy
- [ ] All measurements standardized: minutes for time

## Data Structures - Recipe Model

- [ ] Recipe has: id
- [ ] Recipe has: title
- [ ] Recipe has: cuisine
- [ ] Recipe has: meal
- [ ] Recipe has: servings
- [ ] Recipe has: ingredients (array with id, name, quantity)
- [ ] Recipe has: summary
- [ ] Recipe has: time
- [ ] Recipe has: difficulty_level
- [ ] Recipe has: dietary_tags
- [ ] Recipe has: source
- [ ] Recipe has: img
- [ ] Recipe has: preparation (array with step, description, ingredients)

## Data Structures - Ingredient Model

- [ ] Ingredient has: id
- [ ] Ingredient has: label
- [ ] Ingredient has: unit
- [ ] Ingredient has: quantity
- [ ] Ingredient nutrition has: calories
- [ ] Ingredient nutrition has: carbs
- [ ] Ingredient nutrition has: protein
- [ ] Ingredient nutrition has: fats

## RAG - Database & Embeddings

- [ ] Recipe database has at least 500 entries
- [ ] Ingredient database has at least 500 entries
- [ ] RAG uses vector embeddings (at least rudimentary)
- [ ] RAG uses relevance-based search
- [ ] Recipe/ingredient databases utilize vector similarity for retrieval
- [ ] Student can explain function of all RAG components
- [ ] Student can explain importance of each RAG component in pipeline
- [ ] Student can explain how RAG improves quality vs generation from scratch

## RAG - Implementation

- [ ] Response generated from augmented prompt
- [ ] Retrieval-Augmented Generation used to generate recipes
- [ ] RAG used to generate nutritional values

## AI - Function Calling & Nutrition

- [ ] System uses defined functions for nutritional calculations
- [ ] All nutritional calculations done via function calling
- [ ] Error handling covers parsing errors
- [ ] Error handling covers missing parameters
- [ ] Error handling covers invalid values
- [ ] Error handling covers execution errors
- [ ] Error handling covers timeout errors
- [ ] Error handling covers rate limits
- [ ] Error handling covers connectivity issues

## Summary

**Total Checkboxes:** 119
**Completed:** 0
**Remaining:** 119

---

## Current Implementation Status

### ✅ Already Complete (from NDL project):

1. Demo data seeder with nutrition preferences and meal plans
2. Recipe catalog with 500+ recipes from CSV
3. Nutrition dashboard with summary
4. Basic nutrition tracking

### � Recently Completed (This Sprint):

1. **Meal Customization Tools:**
   - ✅ Delete meal from plan with position index adjustment
   - ✅ Swap meals between days (both on same day or different days)
   - ✅ Reorder meals within a day
   - ✅ Replace meal recipe from library
   - ✅ Manually add meals to specific day/time with recipe selection

2. **Backend APIs Added:**
   - `POST /api/meal-plans/meals/{mealId}/delete` - Delete meal from plan
   - `POST /api/meal-plans/{mealPlanId}/add-meal` - Add new meal to plan
   - `POST /api/meal-plans/ai/meals/{mealId}/regenerate` - Regenerate single meal (stub)

3. **Frontend UI Components:**
   - Updated MealManagementUI with delete buttons
   - Added "+ Add Meal" modal with recipe selection
   - Added meal type selector (breakfast/lunch/dinner/snack)
   - Recipe scrollable list selector

4. **Database Enhancements:**
   - Added `findByMealPlanIdAndDayDate()` method to MealPlanMealRepository
   - Support for position index management on deletion

### 🟡 Partially Complete:

- User profile data model (needs dietary preferences, allergies, intolerances)
- Meal plan structure (basic structure exists, needs full customization) ✅
- Customization Tools (Swap/Reorder/Replace) ✅ | Missing: Regenerate (PHASE 4+)

### ❌ Not Started (Remaining from CAL_ASSIGNMENT):

- Shopping list generation and categorization
- Advanced recipe search and filtering (by calories, macros, prep time)
- Regenerate entire meal plan (PHASE 4+)
- Regenerate individual meals with AI (PHASE 4+)
- Alternative meal suggestions UI (PHASE 4+)
- Ingredient substitution AI
- Comprehensive error handling improvements
- Full documentation updates
