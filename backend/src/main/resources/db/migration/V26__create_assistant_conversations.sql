-- Assistant conversation history
-- Stores per-user chat turns (user / assistant / system_summary roles).
-- system_summary rows are compressed summaries written by AssistantContextWindowService
-- when history approaches the token budget.

CREATE TABLE assistant_conversations (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role           VARCHAR(32)  NOT NULL CHECK (role IN ('user', 'assistant', 'system_summary')),
    content        TEXT         NOT NULL,
    token_estimate INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_assistant_conv_user_created
    ON assistant_conversations (user_id, created_at DESC);
