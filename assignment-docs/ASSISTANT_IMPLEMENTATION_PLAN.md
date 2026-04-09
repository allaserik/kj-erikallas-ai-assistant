# ASSISTANT Implementation Plan

Practical implementation roadmap for the AI assistant feature (Project 3), aligned to `assignment-docs/ASSISTANT_ASSIGNMENT.md` and `assignment-docs/ASSISTANT_ASSIGNMENT_TEST.md`.

## 0. Plan Summary

This project adds a conversational AI assistant on top of the existing wellness and nutrition platform. The assistant is the user-facing interface to data already modelled in Projects 1 and 2.

Implementation priorities:

1. Backend assistant infrastructure (session store, system prompt, function registry, chat endpoint).
2. Four required function calls covering health metrics, nutrition, meal plans, and recipes.
3. Frontend chat UI with message history, input validation, and response rendering.
4. Multi-turn context management (≥5 turns, token windowing, history persistence).
5. Response formatting (concise / detailed mode, structured output per query type).
6. Extra requirements: chart generation from natural language, viz recommendations, context summarisation.

Decision defaults:

- One backend chat endpoint: `POST /api/assistant/chat` — stateless per-message, history sent in body.
- Conversation history persisted in the database per user, loaded on page open.
- Function calling uses the existing `OpenAiClient` and `FunctionCallingHandler` patterns, extended with assistant-specific tools.
- System prompt is server-side; clients never see or can override it.
- No new AI model or new API key required — reuses `OPENAI_API_KEY` / `OPENAI_MODEL`.

---

## 1. Scope and Principles

1. Build mandatory requirements first, extras after baseline is stable.
2. The assistant accesses data via internal Java service calls (not HTTP to itself) — keeps it fast and avoids auth loops.
3. Security: all data access inside function handlers checks `userId` from the authenticated JWT — no cross-user leakage possible.
4. No PII except name exposed to the AI: email, DOB, credentials, other users are never passed to the model.
5. Graceful degradation: if OpenAI is unavailable, return a friendly fallback message; conversation history still works.
6. Keep the chat interface consistent with existing app design (Mantine components, existing auth flow).

---

## 2. Architecture (Target)

### 2.1 Backend modules

1. **`ai/assistant`** — new module
   - `AssistantController` — `POST /api/assistant/chat`, `GET /api/assistant/history`, `DELETE /api/assistant/history`
   - `AssistantService` — orchestrates: build context → call functions if needed → call OpenAI → persist and return
   - `AssistantConversationEntity` / `AssistantConversationRepository` — stores message history (role, content, timestamp) per user
   - `AssistantSystemPromptBuilder` — builds the system prompt injecting user name, capabilities, few-shot examples, guardrails
   - `AssistantFunctionRegistry` — registers and dispatches the 4+ function definitions
   - `AssistantContextWindowService` — manages token budget: trims/summarises history when approaching limit

2. **`ai/assistant/functions`** — function handlers (Data Access Layer)
   - `GetHealthMetricsFunction` — health profile, BMI, weight trend, wellness score
   - `GetGoalsFunction` — active goals, progress snapshots, milestone history
   - `GetMealPlanFunction` — today's / this week's meal plan with recipe details
   - `GetNutritionSummaryFunction` — daily/weekly intake vs. targets (calories, protein, carbs, fats)
   - _(extra)_ `GetWeightTrendFunction` — weight history data formatted for chart generation

### 2.2 Frontend

1. **`features/assistant`** — new feature directory
   - `AssistantPage.tsx` — full-page chat view, route `/assistant`
   - `AssistantChat.tsx` — message list rendering (user bubbles, assistant bubbles, markdown support)
   - `AssistantInput.tsx` — textarea with send button, character counter, validation
   - `AssistantMessage.tsx` — single message renderer (handles text, structured cards, embedded charts)
   - `useAssistant.ts` — hook managing local message state, API calls, loading/error state
   - `assistant.ts` (in `shared/api/`) — typed API client for the chat endpoint

2. **Chart rendering** (extra)
   - Reuse existing Recharts components from the nutrition/trends features
   - Assistant responses can include a `chart` field in the JSON payload; `AssistantMessage.tsx` renders it inline

---

## 3. Data Model

### 3.1 Conversation history

```
assistant_conversations
  id            UUID PK
  user_id       UUID FK users
  role          VARCHAR  -- 'user' | 'assistant' | 'system_summary'
  content       TEXT
  token_count   INTEGER  -- estimated, for windowing
  created_at    TIMESTAMPTZ
```

Index: `(user_id, created_at DESC)` for efficient history load.

`system_summary` role is a compressed summary entry written by the context-window service when history is trimmed.

### 3.2 Flyway migration

One new migration: `V{next}__create_assistant_conversations.sql`

---

## 4. API Surface

| Method   | Path                     | Description                                    |
| -------- | ------------------------ | ---------------------------------------------- |
| `POST`   | `/api/assistant/chat`    | Send a message; receive assistant reply        |
| `GET`    | `/api/assistant/history` | Load conversation history for the current user |
| `DELETE` | `/api/assistant/history` | Clear conversation history                     |

### 4.1 Chat request/response

**Request:**

```json
{
  "message": "What's my current BMI?",
  "mode": "concise"
}
```

`mode`: `"concise"` (default) | `"detailed"` — passed to system prompt to control verbosity.

**Response:**

```json
{
  "reply": "Your current BMI is **24.2**, which is in the **Normal** range (18.5–24.9). ...",
  "chart": null,
  "functionCalled": "get_health_metrics",
  "timestamp": "2026-04-02T10:00:00Z"
}
```

`chart`: null or `{ "type": "line|bar|pie", "data": [...], "config": {...} }` for inline rendering.

### 4.2 History response

Returns ordered list of `{ role, content, timestamp }`.

---

## 5. Function Calling Design

### 5.1 Function definitions (≥4 required)

#### `get_health_metrics`

Retrieves health profile data, current BMI, weight (current + recent trend), and wellness score.

- Parameters: none required (always returns for authenticated user)
- Returns: name, BMI, weight, height, wellness score, category classifications
- Security: userId from JWT only

#### `get_goals`

Retrieves active goals and recent progress snapshots.

- Parameters: `{ "include_history": boolean }` (optional, default false)
- Returns: goal type, target value, current value, progress %, recent snapshots
- Security: userId from JWT only

#### `get_meal_plan`

Retrieves meal plan for a given timeframe.

- Parameters: `{ "period": "today" | "tomorrow" | "week" }` (required)
- Returns: meals with recipe name, calories, macros, prep time, steps on request
- Security: userId from JWT only

#### `get_nutrition_summary`

Retrieves intake totals vs. targets.

- Parameters: `{ "period": "today" | "week" }` (required)
- Returns: calories consumed/target, protein/carbs/fats consumed/target, surplus/deficit
- Security: userId from JWT only

#### `get_weight_trend` _(extra — for chart generation)_

Retrieves weight history data formatted for chart rendering.

- Parameters: `{ "range": "week" | "month" | "3months" }` (required)
- Returns: array of `{ date, weight_kg }` plus trend direction description

### 5.2 Parameter validation

- All function handlers validate parameters before executing: missing required params → structured error response with message.
- Out-of-range values, unknown enums → validation error returned to AI so it can re-ask gracefully.
- Data-not-found cases → structured empty response with explanation (e.g., "no meal plan for today").

### 5.3 Function dispatch flow

```
AssistantService.chat(userId, message, mode)
  → build conversation context (history + system prompt + new message)
  → call OpenAI with tool_choice=auto and function definitions
  → if response has tool_call:
      → dispatch to AssistantFunctionRegistry.execute(userId, funcName, args)
      → append tool result to messages
      → call OpenAI again to generate final reply
  → persist user message + assistant reply
  → return AssistantChatResponse
```

---

## 6. System Prompt Design

### 6.1 Core components (required)

- **Role definition**: wellness assistant with access to the user's own health, goal, nutrition, and meal data.
- **Capabilities list**: what the assistant can and cannot do (no diagnosis, no advice on medications, no other users' data).
- **Tone and personality**: friendly, encouraging, metric-focused; never alarmist.
- **Response formatting rules**: use markdown bold for key numbers, bullet points for lists, headings for multi-section answers; standardised metric units (kg, cm, kcal, g).
- **Concise vs. detailed mode instructions**: concise = 1–3 sentences per topic; detailed = full breakdown with sections.
- **Ethical boundaries and safety guardrails**: refer to healthcare professionals for medical symptoms; refuse to access other users' data; decline out-of-scope requests politely.

### 6.2 Few-shot examples (required per conversation type)

The system prompt includes one before/after example pair for each of the 6 core conversation types:

1. Health metrics query
2. Progress question
3. Meal plan inquiry
4. Recipe information
5. Nutritional analysis
6. General wellness question

### 6.3 Personalisation injection

At request time, `AssistantSystemPromptBuilder` injects:

- User's first name (from auth/profile)
- Current date (for time-relative questions like "today", "this week")
- User's unit preferences and timezone

---

## 7. Conversation Memory and Context Management

### 7.1 History persistence

- Conversation history stored in `assistant_conversations` table.
- Loaded on page open via `GET /api/assistant/history`.
- Each `POST /api/assistant/chat` appends to history and returns the full updated reply.

### 7.2 Token windowing (≥5 turns required, context summarisation extra)

- `AssistantContextWindowService` estimates token count per message (character count ÷ 4 as approximation).
- Token budget: 80% of model context (to leave room for system prompt + function results + reply).
- When total history exceeds budget:
  1. Keep the most recent N complete exchange pairs (always ≥5 turns).
  2. Condense earlier turns into a single `system_summary` message via a separate OpenAI summarisation call (low temperature, concise instruction).
  3. Persist the summary as a `system_summary` row; delete the condensed messages.
- Summary format example: _"User previously asked about BMI (24.2, Normal), weight trend (−1.5 kg over 30d), and vegetarian breakfast options from their meal plan."_

### 7.3 Dynamic context detail (extra)

- When topic shifts significantly (detected by simple keyword classification: health-metric keywords vs. nutrition keywords vs. general), earlier turns on the previous topic are further compressed in the window to give more space to the current topic.

---

## 8. Response Formatting

### 8.1 Four query category templates (required)

1. **Health metrics** — lead with the key number in bold, classification in parentheses, brief interpretation, 1 contextual note.
2. **Goals/progress** — goal name + target, current value, progress bar as text (e.g., `[████░░] 68%`), next milestone.
3. **Meal plan / recipe** — meal name + emoji, macros inline, then steps as numbered list if detailed mode.
4. **Nutritional analysis** — consumed vs. target for each macro, deficit/surplus highlighted, one recommendation.

### 8.2 Concise vs. detailed mode

- Controlled by `mode` field in request and injected into system prompt.
- Concise: key answer only, max ~3 sentences, no headings.
- Detailed: full breakdown with `###` headings, all available data, chart offer if applicable.

### 8.3 Visual data descriptions

When the assistant references trend data (weight over time, calorie adherence), it describes the pattern in natural language: _"Your weight has declined steadily over the last 30 days, dropping from 82.5 kg to 81.0 kg — an average of about 50g per day."_

### 8.4 Chart payload (extra)

When a chart-generating function is called (e.g., `get_weight_trend`), the response includes a `chart` object alongside the text reply, which the frontend renders using the existing Recharts components.

---

## 9. Frontend Plan

### 9.1 Pages and routing

- Add `/assistant` route in `App.tsx` behind `ProtectedRoute`.
- Add "Assistant" entry to `BottomNav` and `Sidebar`.

### 9.2 Chat UI components

- `AssistantPage.tsx` — page wrapper, loads history on mount, renders `AssistantChat` + `AssistantInput`.
- `AssistantChat.tsx` — scrollable message list, auto-scrolls to latest, shows typing indicator during fetch.
- `AssistantInput.tsx` — controlled textarea, validates: no empty sends, max 1000 chars, disable during loading.
- `AssistantMessage.tsx` — renders markdown (use `react-markdown` or manually handle bold/bullets), renders inline chart if `chart` field present, shows timestamp on hover.
- Mode toggle (concise / detailed) — small segmented control above input, persisted in local state.

### 9.3 Error states

- Network error / AI unavailable → inline error message with retry button; does not lose message history.
- Empty / too-long input → inline validation hint on the input field.
- AI fallback message (when OpenAI key absent) → displayed as a normal assistant bubble.

### 9.4 API client (`shared/api/assistant.ts`)

```ts
sendMessage(message: string, mode: 'concise' | 'detailed'): Promise<AssistantChatResponse>
getHistory(): Promise<AssistantMessage[]>
clearHistory(): Promise<void>
```

---

## 10. Security Checklist

- [ ] All assistant endpoints require valid JWT (`@SecurityRequirement(name = "bearerAuth")`).
- [ ] Every function handler extracts `userId` from the JWT; never accepts `userId` as a client parameter.
- [ ] System prompt explicitly instructs the model to refuse cross-user data requests.
- [ ] PII filter: email address, DOB, password hashes, OAuth tokens never included in context built for the AI.
- [ ] Rate limit on `POST /api/assistant/chat` (e.g., 20 requests/minute per user) using existing `RateLimitService`.
- [ ] Input sanitisation: message content length capped server-side (1000 chars); SQL injection not possible — all data access via JPA/JDBC with parameterised queries.

---

## 11. Implementation Order

### Phase 1 — Backend foundation

1. Flyway migration: `assistant_conversations` table.
2. `AssistantConversationEntity` + repository.
3. `AssistantSystemPromptBuilder` — static first (no name injection), expandable.
4. `AssistantFunctionRegistry` — wires function definitions and dispatch.
5. Four function handlers: `GetHealthMetricsFunction`, `GetGoalsFunction`, `GetMealPlanFunction`, `GetNutritionSummaryFunction`.
6. `AssistantService` — full dispatch loop (context build → tool call → second call → persist).
7. `AssistantController` — `chat`, `history`, `deleteHistory` endpoints.
8. Unit tests for each function handler (mock service responses).

### Phase 2 — Frontend baseline

9. `shared/api/assistant.ts` — typed API client.
10. `useAssistant.ts` hook.
11. `AssistantInput.tsx` — input with validation.
12. `AssistantMessage.tsx` — text rendering + timestamp.
13. `AssistantChat.tsx` — message list + scroll behaviour.
14. `AssistantPage.tsx` — page wrapper, history load, route wiring, nav entries.
15. Concise/detailed mode toggle.

### Phase 3 — Context management

16. `AssistantContextWindowService` — token estimation + trimming to ≥5 turns.
17. Summarisation call integration (compress old turns into `system_summary`).
18. Dynamic topic-shift compression (extra).

### Phase 4 — Extra requirements

19. `GetWeightTrendFunction` + chart payload in response model.
20. Frontend inline chart rendering in `AssistantMessage.tsx`.
21. Proactive visualisation suggestion logic in system prompt + service.
22. README + documentation updates (system prompt strategy, function calling details, model rationale).

---

## 12. What Is Reused vs. New

| Component                       | Status         | Notes                                                            |
| ------------------------------- | -------------- | ---------------------------------------------------------------- |
| `OpenAiClient`                  | Reused         | Add `generateWithTools()` method for multi-turn function calling |
| `FunctionCallingHandler`        | Pattern reused | New assistant-specific handlers follow the same pattern          |
| `InsightContextBuilder`         | Reference only | `GetHealthMetricsFunction` reuses the same service dependencies  |
| `RateLimitService`              | Reused         | New key prefix for assistant endpoint                            |
| `PrivacyPreferencesService`     | Reused         | Check consent before building context                            |
| Recharts components             | Reused         | `LineChart`, `BarChart`, `PieChart` already in codebase          |
| Auth JWT extraction pattern     | Reused         | Same `userService.ensureUserFromJwt(auth)` pattern               |
| `assistant_conversations` table | New            | New Flyway migration                                             |
| `AssistantService` + controller | New            |                                                                  |
| Function handlers (4+)          | New            |                                                                  |
| `AssistantContextWindowService` | New            |                                                                  |
| Frontend `features/assistant`   | New            |                                                                  |
