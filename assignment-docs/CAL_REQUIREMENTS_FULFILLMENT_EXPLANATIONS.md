# CAL Requirements Fulfillment Explanations

Use this as oral-defense notes for the "Student can explain…" requirement questions.

---

## 1) Sequential Prompting — 3 Distinct Steps

The meal plan generation pipeline runs three prompts in strict sequence, each one feeding its output into the next as structured context:

**Step 1 — Profile Analysis**
Input: user's health profile, weight goal, BMI, activity level, dietary preferences/allergies, and calorie/macro targets.
Task: produce a structured JSON object containing the derived daily calorie target, macro split (protein/carbs/fats as percentages), meal count, and a one-sentence dietary summary.
Why a separate step: raw profile fields require interpretation (e.g., "weight loss + sedentary" → a specific kcal deficit target). Separating this from generation keeps the generation prompt clean and ensures the calorie math is done before any food suggestions are made.

**Step 2 — Meal Structure**
Input: the Step 1 output (calorie target + macro split + meal count + snack count).
Task: distribute calories across the day's meal slots (e.g., breakfast 25%, lunch 35%, dinner 30%, snack 10%) and output a structured plan with per-slot calorie targets.
Why a separate step: slot-level targets let Step 3 make exact ingredient-quantity decisions without needing to reason about whole-day budgets. It also makes the per-slot generation loop stateless.

**Step 3 — Per-Slot Recipe Generation with Function Calling**
Input: Step 2 slot target (kcal, meal type), RAG-retrieved candidate recipes, user's dietary constraints.
Task: select or generate an appropriate recipe for the slot, call `calculate_nutrition` to verify the nutritional values, and output the final meal JSON.
Why a separate step: grounding each slot's generation in RAG retrieval and function calling keeps outputs factually anchored. Each slot runs independently so failures in one slot don't cascade.

**Why breaking it down is better than one prompt:** A single prompt has to simultaneously reason about calorie budgeting, meal distribution, recipe selection, and nutritional verification. The combined complexity degrades output quality and makes debugging impossible. Three focused prompts each have a clear, verifiable task — this is the same principle as function decomposition in code.

---

## 2) RAG Pipeline — All Components

**Database → Embedding → Retrieval → Augmentation → Generation**

- **Database**: ~19 000 recipe rows imported from `RAW_recipes.csv`, stored in PostgreSQL. Each recipe has title, ingredients, cuisine, dietary tags, and nutrition fields.
- **Embedding**: `VectorEmbeddingService` calls OpenAI's `text-embedding-3-small` model once per recipe at import time. This produces a 1536-dimensional float vector that captures the semantic meaning of the recipe's title + ingredients + cuisine. Vectors are stored in the `recipe_embeddings` table via the pgvector extension.
- **Retrieval**: At query time, the user's current slot requirement (e.g., "high-protein vegan breakfast, ~450 kcal") is itself embedded using the same model. The `<->` pgvector operator computes cosine distance between the query vector and all stored recipe vectors. The top-k closest recipes are retrieved.
- **Augmentation**: The retrieved recipe candidates are serialized into a structured text block and injected into the system prompt as examples. The prompt tells the model: "here are real recipes from the catalog that are relevant to this user's requirements — use these as the basis for your selection or generation."
- **Generation**: GPT-4o generates the final meal JSON, constrained by the injected candidates and a strict JSON schema. If no perfect match exists among the candidates, the model generates a plausible recipe in the same style.

**Why RAG is better than generation from scratch:** Without RAG, the model freely hallucinates recipes that may have incorrect ingredients, unrealistic prep times, or nutritional values that don't match the target. With RAG, the model anchors its output to real recipes in the actual user-facing database — so when a user later searches or browses the catalog, the AI-generated plan reflects real data.

---

## 3) AI Model Choices — Recipe Generation vs. Nutritional Analysis

**Recipe generation and meal planning → GPT-4o**
Rationale: recipe generation requires synthesizing diverse information (dietary constraints, calorie targets, ingredient compatibility, cuisine conventions) and producing well-structured JSON responses reliably across many independent slots. GPT-4o's large context window handles the multi-step pipeline without truncation, and its instruction-following quality for strict JSON schema output is consistently higher than smaller models at the temperature ranges we use.

**Nutritional insight generation → GPT-4o**
The same model is used here because insight generation requires the model to reason coherently about a user's full context (intake trends, goal progress, macro balance) and produce structured, actionable text that does not hallucinate user data. Reliability of structure matters more than creative variety, so a single well-tuned model with strict schema mode is preferable to model-switching.

**Embedding → text-embedding-3-small**
Cost and latency: embedding ~19 000 recipes at import time is a one-time batch operation. `text-embedding-3-small` produces vectors of sufficient quality for our retrieval use case at a fraction of the cost of `text-embedding-3-large`. The semantic quality difference is not meaningful at the recipe-similarity level — "grilled salmon with vegetables" and "pan-seared salmon with roasted greens" are correctly identified as near-matches by either model.

**Why not GPT-3.5 or a smaller model for generation:** Smaller models produce more malformed JSON when the schema is complex (multi-level nested arrays for ingredients + preparation steps). The cost trade-off at our usage level favors GPT-4o's reliability over the savings of a smaller model.

---

## 4) Few-Shot Example Selection Strategy

Few-shot examples are used in Step 1 (profile analysis) because this is the step where the model must correctly interpret free-form profile data into a precise numeric output (daily kcal, macro percentages). Without examples, the model can produce calorie targets that are implausible for the stated goal or macro splits that don't add up.

**Example 1 — Balanced maintenance user:**
A user with moderate activity and a weight-maintenance goal, standard macro split (~30% protein, 40% carbs, 30% fats). This example sets the baseline: shows the model what "normal" output looks like and anchors the kcal math to a recognizable TDEE calculation.

**Example 2 — High-protein/keto user:**
A user with a weight-loss goal and a keto dietary preference, showing a very different macro split (70% fats, 25% protein, 5% carbs) and a caloric deficit. This example teaches the model that dietary preference can override the "default" macro split — without it, the model might apply the balanced split universally.

**Why only two examples:** Adding more examples increases token cost per request substantially (Step 1 runs for every meal plan generation). Two contrasting examples are enough to demonstrate the range of valid outputs. The strict JSON schema output format enforces the remaining structure constraints regardless of how many examples are provided.

---

## 5) Parameter Adjustments — Temperature and top_p

**Temperature** controls how deterministic vs. creative the model's output sampling is. At temperature 0, the model always picks the highest-probability token; at 1.0, outputs are highly varied but potentially incoherent.

- **Recipe generation — temperature 0.7**: We want varied recipe output across multiple generation calls (different ingredients, presentation styles, cuisines) while still producing coherent recipes. 0.7 is the standard "creative but not wild" setting for content generation tasks.
- **Nutritional analysis and insight generation — temperature 0.3**: These calls require factually grounded, consistent, non-repetitive text about the user's actual data. Lower temperature reduces the chance of the model embellishing or inverting facts about the user's intake.
- **AI ingredient substitution — temperature 0.5**: Substitution needs enough creativity to suggest non-obvious alternatives, but the suggestions still need to be culinarily plausible. 0.5 balances both.

**top_p (nucleus sampling)** is set to 0.9 for generation steps. This means the model samples from the top 90% probability mass, cutting off low-probability tokens that would introduce rare/unusual word choices. In combination with temperature, top_p prevents the model from occasionally producing extremely unusual ingredient combinations while still allowing creative variation.

**Why these values matter in practice:** If temperature were 0 for recipe generation, every plan generated for the same user profile would look nearly identical — defeating the variety requirement. If temperature were 0.7 for nutritional analysis, the model might occasionally "round up" a calorie deficit or invent a macro trend that isn't in the user's data, undermining the factual grounding requirement.

---

## 6) RAG vs. Function Calling — When Each Is Used

These are two complementary techniques, not alternatives:

**RAG** is used to select and ground the _content_ of recipes — what ingredients, what cuisine, what style. It ensures the model works from real catalog data rather than inventing recipes. RAG does not perform arithmetic.

**Function calling** is used to compute _nutritional values_ — the math that calculates calories, protein, carbs, and fats from ingredient quantities. It ensures the numbers are correct rather than estimated by the language model. Function calling does not decide what recipe to generate.

The combination is intentional: RAG gives the model the right context to generate contextually appropriate recipes; function calling prevents the model from hallucinating the resulting nutrition figures.

---

## 7) Error Handling and Recovery Strategy

**Function calling errors** (`FunctionCallingHandler`):

- JSON parse failure on the model's function call arguments → logged as WARNING, structured error returned to caller.
- Missing required parameters (e.g., no ingredients list) → structured validation error, caller falls back to arithmetic.
- Out-of-range values (e.g., negative quantities) → clamped or rejected with error detail.
- Execution exception → caught generically, error response returned, fallback triggered.

**AI generation errors** (OpenAI API):

- Timeout: configurable per-call timeout; on expiry, the insight endpoint returns the most recent cached insight (24-hour TTL per user per day). The meal plan generator falls back to the deterministic `MealSelectionStrategy`.
- 429 rate limit: caught explicitly; retry-after is logged but the response immediately falls back to cache/deterministic path rather than blocking the request thread.
- Malformed JSON response: schema validation fails; logged, fallback triggered. The strict JSON schema `response_format` setting reduces this case significantly.

**Why caching as a recovery mechanism:** Cached insights mean the user always sees relevant data even when the AI service is unavailable. A null/empty state would feel broken; a slightly stale insight is a better user experience than an error banner, especially for a dashboard that should load reliably on every visit.
