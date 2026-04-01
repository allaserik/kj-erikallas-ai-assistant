### Testing

Ensures that software works as expected by validating features against requirements. It helps catch bugs early, improves reliability, and maintains high-quality standards in development.

How to do testing?

1. Clone the repository, then build and run the submitted code.
2. Agree on your teamwork: how do you divide testing between reviewers?
3. Test functionality and check compliance with the requirements.
4. Provide feedback in the group chat and request fixes if necessary.
5. Clearly state what changes are mandatory and what are optional fixes.
6. Repeat the testing cycle after submitters make the requested changes for as many times as is needed.

#### Mandatory

- [x] The README file contains a clear project overview, setup instructions, and usage guide.
  - README includes a full project overview, Docker setup and local dev sections, environment variable reference, run modes, and a dedicated "AI & Nutrition System" section covering prompt engineering strategy, AI model selection rationale, data model decisions, and error handling methods.

- [x] Nutritional planning functionality supports at least 15 different dietary preferences and 10 food allergies/intolerances.
  - `dietary_preference_options` seeds exactly 15 options: vegetarian, vegan, pescatarian, keto, low-carb, high-protein, Mediterranean, paleo, gluten-free, dairy-free, halal, kosher, whole-food, DASH, and flexitarian. `allergy_options` seeds exactly 10 entries: peanut, tree nut, milk, egg, soy, wheat, fish, shellfish, sesame, and lactose intolerance.

- [x] The platform collects and utilizes user preferences: dietary preferences, allergies, disliked ingredients, cuisine preferences, calorie/macro targets, meal frequency and timing.
  - The health profile setup and nutrition consent flow collect all six preference categories. Every generated meal plan reads these fields from the profile and passes them as filters to recipe selection and as context to the AI generation prompt.

- [x] System reuses information from Project 1 without requiring duplicate input.
  - Calorie targets are derived from existing BMI, weight goal, and activity data saved in Project 1. Dietary preferences and restrictions collected in the initial consent/profile flow are pre-populated in the nutrition setup; the user only confirms rather than re-entering.

- [x] All dates and times follow the ISO 8601 standard format.
  - All `OffsetDateTime` fields are serialized by Jackson with full ISO 8601 offset notation (e.g. `2025-04-01T12:00:00+00:00`). The frontend API layer formats dates as `YYYY-MM-DD` strings for date-only parameters.

- [x] System generates complete daily and weekly meal plans based on user preferences.
  - `POST /api/meal-plans/generate` accepts a `duration` field supporting `DAILY` (1 day) and `WEEKLY` (7 days). Each plan is grouped by date, with breakfast, lunch, dinner, and snack slots selected from the recipe catalog filtered by the user's dietary/allergy preferences.

- [x] System allows customizing meal structures (e.g. number of meals and snacks per day).
  - The generate request includes `mealsPerDay` and `snacksPerDay` fields. The plan builder creates the corresponding number of named slots before recipe selection runs, so the generated plan structure matches what the user specified.

- [x] Each meal has basic information: name, meal type, nutritional value.
  - Every `meal_plan_meals` row stores `meal_type` (breakfast/lunch/dinner/snack), `name`, and the linked recipe's `calories_kcal`, `protein_g`, `carbs_g`, `fats_g`. All fields are exposed in the API response.

- [x] System provides alternative meal options and allows users to select replacements.
  - `GET /api/meal-plans/meals/{mealId}/alternatives` returns ranked recipes matching the same slot type and dietary constraints. `PUT /api/meal-plans/meals/{mealId}/replace` swaps in the selected alternative.

- [x] System allows reordering meals within a day or swapping meals between different days.
  - `POST /api/meal-plans/meals/reorder` accepts an ordered list of meal IDs on the same day and updates their `sort_order`. `POST /api/meal-plans/meals/swap` accepts two meal IDs from any days and atomically exchanges their date assignment and position.

- [x] System allows users to manually add custom meals.
  - `POST /api/meal-log/custom` logs a free-text meal with user-supplied name, meal type, and calorie/macro values. The entry appears in the daily tracking view and its calories are included in deficit/surplus calculations.

- [x] System allows regenerating individual meals or entire meal plans while preserving preferences.
  - `POST /api/meal-plans/meals/{mealId}/regenerate` replaces one slot using the same stored preference constraints while leaving all other meals intact. A whole-plan regeneration path reapplies the same preferences to produce a fresh plan.

- [x] Shopping list can be generated from a specific meal or meal plan.
  - `GET /api/meal-plans/{mealPlanId}/shopping-list` aggregates all ingredient quantities across every meal in the plan and returns a consolidated shopping list. Single-meal generation is also available through the meal log path.

- [x] Shopping list items are automatically categorized by food group to at least 5 meaningful categories.
  - `ShoppingListService.FoodCategory` defines five categories — PROTEINS, DAIRY, PRODUCE, GRAINS, and PANTRY STAPLES — with keyword arrays for auto-classification. Unrecognized ingredients default to PANTRY, so every item always lands in a category.

- [x] Shopping list allows both quantity adjustments and item exclusions.
  - `adjustQuantity()` updates an item's quantity in place; `excludeItem()` soft-removes it with an optional exclusion note. Both changes are reflected in the categorized view returned to the frontend.

- [x] Sequential prompting with at least 3 distinct steps is used for meal plan generation.
  - Three distinct prompts run in sequence: (1) profile analysis extracts calorie target and macro split, (2) meal structure determines per-slot kcal distribution, (3) per-slot recipe generation with function calling produces the actual recipe. Each step receives the previous step's output as structured context. See `CAL_REQUIREMENTS_FULFILLMENT_EXPLANATIONS.md` for the full breakdown.

- [x] Retrieval-Augmented Generation is used to generate recipes and nutritional values.
  - `RAGRetrieverService` embeds the user's current slot requirement using `text-embedding-3-small`, runs a pgvector cosine similarity search against the `recipe_embeddings` table, and injects the top-k results into the generation prompt as grounding context. See `CAL_REQUIREMENTS_FULFILLMENT_EXPLANATIONS.md` for the full pipeline.

- [x] Recipe and ingredient databases have at least 500 entries each.
  - `RAW_recipes.csv` imports ~19 000 recipe rows on first startup. `cleaned_nutrition_dataset_per100g.csv` imports over 800 ingredient nutrition entries. Both CSVs are bundled in `src/main/resources/recipe-csv/` and loaded by the data initializer.

- [x] RAG implementation includes vector embeddings and relevance-based search.
  - `VectorEmbeddingService` calls `text-embedding-3-small` to produce 1536-dimensional vectors stored in the `recipe_embeddings` table via the pgvector extension. Retrieval uses the `<->` operator for cosine distance ranking, returning the most semantically relevant recipes.

- [x] Response to the user is generated from an augmented prompt.
  - Retrieved recipe candidates are injected into a structured system prompt before the generation call. The model output is constrained by a strict JSON schema and is grounded in catalog entries, producing results consistent with the user's actual recipe database rather than hallucinated recipes.

- [x] System uses clearly defined functions to provide accurate nutritional calculations.
  - `calculate_nutrition` is registered as a named OpenAI tool definition with a typed parameter schema. `FunctionCallingHandler.executeFunction()` routes invocations to `NutritionCalculationService`, which performs deterministic unit-conversion and per-100g multiplication rather than relying on model estimation.

- [x] Function calling implementation includes proper error handling.
  - `FunctionCallingHandler` wraps every execution in structured error handling covering JSON parse failures, missing or null parameters, out-of-bound values, and runtime exceptions. Each case returns a typed error response so callers can fall back gracefully rather than propagating exceptions to the user.

- [x] Recipes can be searched by name, ingredients, or cuisine.
  - `GET /api/recipes/search?q={query}` runs a PostgreSQL full-text similarity search across the `title`, ingredient names, and `cuisine` columns. The query is indexed with a `tsvector` for performance on the large recipe dataset.

- [x] Recipes can be filtered by dietary restrictions, allergies, ingredients, calorie/macro amounts, and preparation time.
  - `RecipeSearchFilters` supports dietary tag, allergy tag, maximum prep time, maximum calories, and minimum protein as optional query parameters. All active filters compose into a parameterized WHERE clause in `JdbcRecipeCatalogQueryRepository`.

- [x] Recipes display ingredients, step-by-step instructions, and visualized nutritional information.
  - The recipe detail endpoint returns the full `ingredients` array, `preparation` steps with descriptions, and a nutrition summary. The UI renders a macro breakdown pie chart alongside the ingredient list and step-by-step preparation section.

- [x] Variety of custom recipes can be generated with AI based on user preferences.
  - The AI generation step in the meal plan pipeline creates recipes tailored to the user's dietary tags, allergies, cuisine preference, and target calories. RAG retrieval is run first to ground each generation, so repeated calls produce topically varied outputs rather than duplicating results.

- [x] Ingredients in a recipe can be substituted with AI.
  - `POST /api/recipes/{recipeId}/substitute` accepts a target ingredient and optional constraints, sends the full recipe and ingredient list as context to GPT-4o, and returns ranked substitutes with justification and approximate quantity adjustments.

- [x] Portions can be adjusted by modifying serving size. Function calling is used for nutritional recalculation.
  - `POST /api/recipes/{recipeId}/scale` recalculates all ingredient quantities proportionally to `targetServings`, then calls `calculate_nutrition` via the function calling path to recompute the resulting nutrition totals. An arithmetic fallback is used if the function call fails.

- [x] All measurements are standardized to common units in data structures.
  - All solid ingredient quantities are stored in grams, liquids in millilitres, energy in kcal, and time in minutes. Unit normalization is enforced at CSV import time in `RawRecipeImportInitializer` so no mixed-unit data enters the schema.

- [x] Recipe data structure has all required fields.
  - `RecipeCatalogModels.RecipeDetail` includes: `id`, `title`, `cuisine`, `meal`, `servings`, `ingredients` (array with `id`, `name`, `quantity`), `summary`, `timeMinutes`, `difficultyLevel`, `dietaryTags`, `source`, `img`, and `preparation` (array with `stepNumber`, `step`, `description`).

- [x] Ingredient data structure has all required fields.
  - Ingredient records in recipe detail include `id`, `label`, `unit`, `quantity`, and a `nutrition` object containing `caloriesKcal`, `carbsG`, `proteinG`, and `fatsG` — sourced via the `nutrition_per100g` join at query time.

- [x] Nutritional analysis includes calorie (kcal) and macronutrient (grams and % of daily target) tracking per meal/day.
  - Daily intake aggregates calories, protein, carbs, and fats from all logged meals. Macro percentages of daily target are computed in the frontend and displayed as a macro breakdown pie chart in the Nutrition section.

- [x] Nutritional analysis tracks users nutritional intake and compares it to nutritional intake goals on a daily and weekly basis with deficit/surplus calculations.
  - `GET /api/nutrition/trends/daily` returns consumed vs target per day with deficit/surplus amounts. Weekly summaries aggregate the same data over 7-day windows. Both views are visualized in the Nutrition Trends page.

- [x] Nutritional intake tracking is visualized with a color-coded progress bar indicating caloric surplus or deficit.
  - The Today's Intake section shows a calorie progress bar that fills green when consumption is at or below target and turns red when the target is exceeded. The bar label shows the remaining or exceeded amount in kcal.

- [x] Nutritional intake tracking includes visualized trend lines for daily/weekly/monthly deficit/surplus.
  - `NutritionTrendChart` renders daily intake as color-coded bars — green when at/under target, red when over — with a dashed amber target line in `ComposedChart`. Weekly and monthly modes use the same Cell-level coloring on a `BarChart`.

- [x] AI-driven nutritional analysis offers regular summaries including key achievements, potential concerns, and macronutrient balance analysis.
  - `GET /api/nutrition/insights` calls GPT-4o with the user's tracked intake, macro balance, and goal progress to produce a structured insight covering achievements, concerns, and macro analysis, cached for 24 hours per user per day.

- [x] AI-driven nutritional analysis provides improvement suggestions including food recommendations, meal timing adjustments, portion modifications, alternative ingredients, and meal plan optimizations.
  - The insight response JSON includes a structured `suggestions` array covering all five improvement categories, grounded in the user's current deficit/surplus trends and active nutritional goals.

- [x] Nutritional analysis features are seamlessly integrated (dashboard, historical analysis, AI insights grouped together).
  - Today's nutrition summary card appears on the main dashboard. Detailed historical analysis and trends are in the Nutrition page. AI-driven nutritional insights are displayed via the shared `InsightCard` component alongside other health insights.

- [x] Meal planner uses relevant user data: BMI and weight goals for calorie targets, activity level for portions/macros, wellness score updated based on nutrition.
  - `CalorieTargetService` derives the daily kcal target from BMI classification and weight goal direction. Activity level adjusts the TDEE multiplier. Nutrition log events trigger wellness score recalculation so the score reflects recent dietary behavior.

- [x] Student can explain and justify AI model choices for recipe generation vs. nutritional analysis.
  - Explanation prepared in `CAL_REQUIREMENTS_FULFILLMENT_EXPLANATIONS.md`.

- [x] Few-shot examples are used in prompts.
  - The Step 1 profile analysis prompt includes two inline few-shot examples: a balanced maintenance user and a high-protein/keto user. See `CAL_REQUIREMENTS_FULFILLMENT_EXPLANATIONS.md` for the example selection rationale.

- [x] Parameter adjustments are used for AI-generated outputs.
  - `OpenAiClient` exposes per-request `temperature` and `top_p` settings. Recipe generation uses temperature 0.7 for creative variety; nutritional analysis and substitution calls use 0.3 for factual consistency. See `CAL_REQUIREMENTS_FULFILLMENT_EXPLANATIONS.md`.

- [x] System handles API errors gracefully with clear user-friendly error messages.
  - `OpenAiClient` catches timeouts, 429 rate-limit responses, and malformed JSON responses, converting each to a structured `ApiError`. The frontend displays inline alert banners for each error type rather than crashing or showing raw stack traces.

- [x] System implements recovery mechanisms for failed AI requests.
  - AI insight generation falls back to the most recent cached insight (24-hour TTL) if the live call fails. Meal plan generation falls back to the deterministic `MealSelectionStrategy` if the AI-based selection times out or returns an unusable response.

- [x] Content versioning allows restoring previous meal plans.
  - `MealPlanVersionEntity` stores a JSON snapshot of the meal plan's meals at each modification with an incrementing `versionNumber`. `GET /api/meal-plans/{id}/versions` lists available snapshots; `POST /api/meal-plans/{id}/versions/{versionId}/restore` restores the selected version. The frontend version history panel exposes both actions.

#### Extra

- [x] Project runs entirely through Docker with a single command.
  - `docker compose -f infra/docker-compose.yml --env-file .env up -d` starts backend, frontend, PostgreSQL, and MailHog. Docker is the only required dependency; no manual JDK, Node, or database installation needed.

- [ ] System maintains user preference history to inform future recommendations.
  - Not implemented. Dietary and cuisine preferences set in the profile are applied to every generation, but there is no separate preference-history or decision-feedback table to inform future ranking.

- [ ] Nutritional analysis includes micronutrients.
  - Not implemented. Nutrition tracking covers the four macro fields (calories, protein, carbs, fats). Micronutrient tracking was considered out of scope for this project phase.

- [ ] System includes a mechanism for user feedback to improve RAG database quality and size.
  - Not implemented. RAG retrieval is currently read-only; there is no feedback loop to mark recipes as preferred or rejected and surface those signals in future retrieval ranking.

- [x] Nutritional data has been extended with additional fields.
  - Recipe schema includes `difficulty_level`, `source`, `img`, and `dietary_tags` beyond the stated required fields. The `nutrition_per100g` ingredient table stores full per-100g macro values imported from a validated Kaggle dataset, giving the ingredient database accurate nutritional grounding.

- [x] Student has implemented additional technologies, security enhancements and/or features beyond core requirements.
  - Notable extras: pgvector for semantic recipe search, application-layer at-rest encryption for sensitive profile fields, optional TOTP 2FA, multi-provider OAuth identity linking with email-collision guidance, per-scope rate limiting with RFC 7231 `Retry-After`, full data export endpoint, and meal plan content versioning with point-in-time restore.

- [x] Overall implementation quality demonstrates exceptional attention to detail and thoroughness.
  - Backend is structured in feature-based bounded modules (auth, nutrition, mealplan, meallog, shopping, AI, insights) with consistent service/repository/controller layering. Frontend uses feature-based folder structure with shared API and hook abstractions. Error handling, unit standardization, ISO 8601 dates, and demo/clean run modes are consistent throughout.
