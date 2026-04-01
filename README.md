# Numbers Don't Lie | Counting Calories

Wellness tracking app with health profile collection, goal tracking, wellness scoring, AI-generated insights, and trend visualizations. Meal planning and recipe generation features with OpenAI function calling for nutrition calculations.

## Table Of Contents

- [Project Overview](#project-overview)
- [Tech Stack](#tech-stack)
- [Quick Start](#quick-start)
- [Usage Guide](#usage-guide)
- [Local Development](#local-development)
- [Environment Variables](#environment-variables)
- [AI Insights Notes](#ai-insights-notes)
- [AI & Nutrition System](#ai--nutrition-system-counting-calories--project-2)
- [Further Development Ideas](#further-development-ideas)

## Project Overview

Main capabilities:

- Account system: email/password, Google OAuth, GitHub OAuth
- Email verification, refresh-token based sessions, password reset
- Optional user-enabled 2FA (TOTP + QR setup)
- Health profile + fitness assessment
- Weight + activity check-ins and historical tracking
- Goals, progress snapshots, and milestone tracking
- BMI + wellness score analytics
- Active-days (last 7d) activity indicator
- AI-generated wellness insights with caching and response guardrails
- Privacy preferences, data export, account deletion
- Nutrition recipe explorer with search, protein filter, and difficulty ratings
- AI meal plan generation with scaling and shopping list export
- RAG-powered recipe recommendations using pgvector similarity search
- Calorie trend charts with target-based color coding (daily/weekly/monthly)

Bonus/extra functionality implemented:

- Multi-provider account identity linking with email-collision guidance
- AI guardrails (grounding, safety checks, anti-repeat, restriction checks)
- OpenAI function calling for dynamic serving-size scaling in recipes
- Demo data mode for end-to-end visualization testing
- Application-layer at-rest protection for sensitive auth secrets

## Tech Stack

- Backend: Java, Spring Boot, Spring Security, JPA/Hibernate, Flyway, PostgreSQL
- Frontend: React, TypeScript, Vite, Recharts
- Auth: Local JWT + Auth0 resource-server validation for OAuth tokens
- AI: OpenAI Responses API with function calling (optional), strict JSON schema output
- Vector search: pgvector (PostgreSQL extension) for RAG recipe similarity search
- Email: JavaMailSender with SMTP/MailHog support
- Containerization: Docker Compose for multi-service orchestration

## Demo Data

[Recipes from Kaggle](https://www.kaggle.com/code/snowholt/chefbelle-an-ai-kitchen-assistant?select=RAW_recipes.csv)
[Nutrition Data from Kaggle](https://www.kaggle.com/code/snowholt/chefbelle-an-ai-kitchen-assistant?select=cleaned_nutrition_dataset.csv)
Activity and weight data generated with realistic trends and noise based on common patterns in health tracking datasets.

## Quick Start

Two security keys are required. Generate them once and fill both into your `.env`:

```bash
openssl rand -base64 48   # → APP_TOKEN_PEPPER
openssl rand -base64 48   # → APP_DATA_ENCRYPTION_KEY
```

### Demo — pre-seeded data + MailHog email

```bash
cp .env.demo.example .env
# Edit .env: set APP_TOKEN_PEPPER and APP_DATA_ENCRYPTION_KEY
docker compose up -d --build
```

- Frontend: `http://localhost:5173`
- Log in: `demo@example.com` / `demo@example.com`
- Email inbox (verification, reset links): `http://localhost:8025`

### Clean — empty database

```bash
cp .env.clean.example .env
# Edit .env: set APP_TOKEN_PEPPER and APP_DATA_ENCRYPTION_KEY
docker compose up -d --build
```

- Frontend: `http://localhost:5173` — register a new account
- Email verification text is logged to backend console (no mail server needed)

### Stop / Reset

```bash
docker compose down       # stop all services
docker compose down -v    # stop and wipe database volume
```

> Backend starts slower than frontend — wait a few seconds after `up` before the API is ready.

## Usage Guide

Start with demo mode for the fastest path to a populated app. Use `demo@example.com` / `demo@example.com` to skip registration.

### Core flow

1. **Sign up / log in** — email/password, Google, or GitHub. Verify email when prompted.
2. **Health profile** — fill in baseline stats (age, weight, height, activity level, dietary preferences).
3. **Check-ins** — add weight and activity entries; they appear in the timeline immediately.
4. **Goals** — create an active goal, add progress snapshots, and track milestones.
5. **Dashboard** — BMI, wellness score, active-days indicator, AI insight, weekly/monthly summary cards.
6. **Trends** — weight chart, wellness evolution, component breakdown, activity heatmap; switch date ranges.
7. **Nutrition** — explore recipes, filter by protein/difficulty, generate a meal plan, get a shopping list.
8. **Settings** — privacy consent (gates AI insight), 2FA setup, data export, account deletion.

### Reviewer fast-path (5 minutes)

Use demo mode so data is already populated:

1. Log in as `demo@example.com` and open the **Dashboard** — verify BMI, wellness score, insight, and summary cards.
2. Go to **Settings → Privacy** — toggle AI consent off; confirm insight disappears on Dashboard, toggle back on.
3. Go to **Health Profile** — update a field and save; confirm inline success without page reload.
4. Add one **weight** entry and one **activity** entry; confirm both appear in the timeline.
5. Open **Trends** — check weight chart, wellness charts, activity heatmap, and switch the date range.
6. Open **Nutrition → Recipes** — search and filter; open a recipe for detail view.
7. Open **Nutrition → Meal Plan** — generate a plan and view the shopping list.
8. Go to **Settings → Export** — trigger export and confirm the historical JSON payload downloads.

## Local Development

Start only the database using the infra compose file, then run backend and frontend natively.

The frontend also needs its own `frontend/.env.local` for Auth0 OAuth (Google/GitHub login). Copy the template and fill in your Auth0 tenant details:

```bash
cp frontend/.env.local.example frontend/.env.local
# Edit frontend/.env.local: set VITE_AUTH0_DOMAIN, VITE_AUTH0_CLIENT_ID, VITE_AUTH0_AUDIENCE
```

Email/password login works without Auth0 credentials — skip this step if you only need local auth.

### With Demo Data

```bash
cp .env.demo.example .env
# Edit .env: set APP_TOKEN_PEPPER and APP_DATA_ENCRYPTION_KEY
cp frontend/.env.local.example frontend/.env.local
docker compose -f ./infra/docker-compose.yml --env-file ./.env up -d
cd backend && ./mvnw spring-boot:run
# in a second terminal:
cd frontend && npm install && npm run dev
```

### Clean (empty database)

```bash
cp .env.clean.example .env
# Edit .env: set APP_TOKEN_PEPPER and APP_DATA_ENCRYPTION_KEY
cp frontend/.env.local.example frontend/.env.local
docker compose -f ./infra/docker-compose.yml --env-file ./.env up -d
cd backend && ./mvnw spring-boot:run
# in a second terminal:
cd frontend && npm install && npm run dev
```

## Environment Variables

Config lives in the root `.env`. Two ready-made templates cover both modes:

| Template             | Mode  | Email       | Seed data                    |
| -------------------- | ----- | ----------- | ---------------------------- |
| `.env.demo.example`  | Demo  | MailHog     | Yes — demo account + history |
| `.env.clean.example` | Clean | Console log | No                           |

Both templates have inline comments. Only two values require manual setup:

```bash
openssl rand -base64 48   # → APP_TOKEN_PEPPER
openssl rand -base64 48   # → APP_DATA_ENCRYPTION_KEY
```

`OPENAI_API_KEY` is optional — all AI features fall back gracefully when absent.

## AI Insights Notes

When `OPENAI_API_KEY` is configured:

- Insights are generated with strict JSON schema
- Responses are cached by prompt-context hash
- Guardrails enforce grounding/safety/novelty/restriction constraints

When AI is unavailable:

- Service returns latest cached insight or a safe fallback insight

## AI & Nutrition System (Counting Calories — Project 2)

### Prompt Engineering Strategy

Recipe and meal plan generation uses a **three-step sequential prompting** pipeline:

1. **Profile analysis prompt** — Extracts the user's caloric target, macro split, dietary restrictions, and meal structure from their health profile + goals. Uses few-shot examples (a balanced user and a high-protein/keto user) so the model learns the expected output schema from examples rather than schema instructions alone.
2. **Meal structure prompt** — Given the profile analysis output, determines the day's meal distribution (e.g., three meals + one snack, rough kcal targets per slot). Uses the previous output as structured context so each step builds on confirmed data.
3. **Recipe generation with function calling** — Generates actual recipes and calls `calculate_nutrition` to compute per-ingredient nutritional values. The function-calling step grounds the model's output in real calculations rather than letting it hallucinate macros.

**AI-generated insights** use a separate short-form prompt that injects the user's recent tracking data (weight trend, calorie deficit/surplus, goal progress) and asks for a single actionable observation. Temperature is varied: 0.7 for recipe creativity, 0.3 for nutritional analysis, 0.5 for substitution suggestions.

### Function Calling Architecture

The `calculate_nutrition` function is registered as an OpenAI tool definition. When the model calls it during recipe generation, the call is intercepted server-side by `FunctionCallingHandler`, which routes to `NutritionCalculationService`. That service:

- Converts ingredient quantities to grams using a unit-conversion table
- Looks up per-100g nutritional estimates from an internal ingredient map
- Returns calories, protein, carbs, and fats for the entire ingredient list

The same function-calling pathway is used when a user scales a recipe to a different serving count — the backend re-calls `calculate_nutrition` with the scaled ingredient quantities and uses the result as the authoritative nutrition total (arithmetic recalculation is used as fallback if the function call fails).

### AI Model Selection Rationale

- **Recipe generation, meal planning, and insights**: GPT-4o — chosen for its large context window (required for multi-step sequential prompts) and reliable instruction following with strict JSON schema output.
- **Embedding / RAG retrieval**: `text-embedding-3-small` — low cost, sufficient quality for semantic recipe search over the local recipe catalog.
- Strict JSON schema mode (`response_format: json_schema`) is used for all structured output to eliminate hallucinated fields or malformed responses.

### Data Model Decisions

- **Recipes** are stored with standardized units (all weights in grams, volumes in millilitres, energy in kcal, time in minutes) so the nutrition math doesn't need unit-conversion logic at query time.
- **Dietary tags** are stored as a PostgreSQL text array — simple to query with `@>` (contains) and extensible without schema changes.
- **pgvector embeddings** are stored alongside each recipe for semantic RAG retrieval, generated once on import and reused across all searches.
- **Nutrition values** are stored both on the recipe row (pre-aggregated, from import) and as a computed CTE at query time from ingredient-level data — the CTE value is preferred for accuracy; the row value is the fallback.

### Error Handling

- **Function calling**: `FunctionCallingHandler` wraps every `executeFunction` call; catches JSON parse errors, missing parameters, out-of-range values, and execution exceptions — each case logs a warning and returns a structured error response so callers can fall back gracefully.
- **AI generation timeouts**: All OpenAI calls are wrapped with a configurable timeout. On timeout or API error, the system returns cached insights (24-hour TTL) if available, or a static fallback message.
- **Retry and caching**: Insight generation uses a daily cache keyed by user ID + date, preventing redundant API calls and ensuring the UI always shows something even when the AI service is offline.
- **Recipe scaling fallback**: If function-calling-based nutrition recalculation fails for any reason, the endpoint falls back to arithmetic scaling of the stored per-ingredient values.

## Further Development Ideas

Practical next steps grouped by area:

### 1. Security and Compliance

- Move from app-layer encryption only to full infrastructure encryption-at-rest (managed volume encryption + key rotation policy).
- Run backend strictly behind HTTPS in all environments and add HSTS/reverse-proxy hardening.
- Add structured audit trail for sensitive data actions (consent changes, exports, account deletion, security settings).

### 2. AI Quality and Evaluation

- Add an internal evaluation suite with scenario-based scoring for AI insight relevance, safety, and goal alignment.
- Add stricter domain validators for nutrition (restriction-ingredient ontology, unsafe advice classes, implausible macro values).
- Support model fallback strategy by tier: primary model → cheaper model → deterministic template.
- Add embedding refresh pipeline so newly imported recipes are auto-embedded without a full re-import.

### 3. Meal Planning and Nutrition

- Add user feedback on generated meal plans (thumbs up/down per meal) to surface preference signals for future plan generation.
- Build a community-driven recipe import flow: users can submit recipes that get validated, embedded, and added to the shared catalog.
- Extend nutritional tracking to micronutrients (vitamins, minerals) using the existing per-100g ingredient dataset as a base.
- Add weekly nutrition summaries: average macros vs. targets, best and worst days, trend direction — as a scheduled AI-generated report.
- Add recipe image management: either user-uploaded images or AI-generated thumbnails to replace the current "no image" placeholder.

### 4. Goals and Progress

- Add auto-progress snapshots for weight goals on each weight check-in (matching how activity goals already auto-progress).
- Add projected completion date per active goal based on trend slope with a confidence range.
- Add per-goal timeline views correlating milestones, notes, and check-ins in a single view.

### 5. Analytics and Visualization

- Add richer comparison views with week-over-week and month-over-month component deltas.
- Add configurable dashboard widgets and saved date ranges.
- Add trend annotations for key events (goal created, milestone reached, significant weight change).
- Add a nutrition calendar heatmap (similar to activity heatmap) showing daily calorie adherence.

### 6. UX and Product Flow

- Add a first-run onboarding wizard (profile → consent → first check-in → first goal → first meal plan).
- Expand timeline interactions with inline edit/delete for check-ins using optimistic updates.
- Add explicit "data completeness" indicators to guide users toward richer AI insight context.
- Add dietary preference history so the system can learn from which generated recipes the user kept vs. replaced.

### 7. Testing and Reliability

- Increase backend integration coverage for auth, privacy, AI fallback, and meal plan generation paths.
- Add a frontend e2e smoke suite covering reviewer-critical flows (registration, consent, meal plan generation, shopping list).
- Add migration verification tests to catch schema drift and data conversion issues early.

### 8. Operations and Observability

- Add structured logs and request tracing across auth, AI, and export endpoints.
- Add metrics dashboards covering error rates, response times, AI fallback frequency, and rate-limit hit rates.
- Add environment health checks for external dependencies (email provider, Auth0, OpenAI API).
