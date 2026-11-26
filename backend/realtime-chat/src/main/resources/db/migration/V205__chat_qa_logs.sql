CREATE TABLE IF NOT EXISTS qa_logs (
    qa_id                   SERIAL PRIMARY KEY,
    conversation_id         INTEGER NOT NULL REFERENCES conversations (conversation_id) ON DELETE CASCADE,
    institution_id          INTEGER NOT NULL DEFAULT 1,
    question                TEXT NOT NULL,
    answer                  TEXT NOT NULL,
    confidence              INTEGER,
    response_time           INTEGER,                          -- ms
    gesture_input           TEXT,
    context_used            TEXT,
    evaluation_score        INTEGER,                          -- 0-100, written by AI-2 evaluateResponse
    service_mode            VARCHAR(30) NOT NULL DEFAULT 'full_llm_bot',
    responded_by            VARCHAR(20) NOT NULL DEFAULT 'llm', -- llm | admin
    admin_id                INTEGER REFERENCES users (user_id),
    llm_recommendation_used BOOLEAN DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS qa_logs_conversation_id_idx ON qa_logs (conversation_id);
CREATE INDEX IF NOT EXISTS qa_logs_institution_id_idx ON qa_logs (institution_id);
CREATE INDEX IF NOT EXISTS qa_logs_service_mode_idx ON qa_logs (service_mode);
CREATE INDEX IF NOT EXISTS qa_logs_responded_by_idx ON qa_logs (responded_by);
CREATE INDEX IF NOT EXISTS qa_logs_admin_id_idx ON qa_logs (admin_id);
CREATE INDEX IF NOT EXISTS qa_logs_created_at_idx ON qa_logs (created_at);
CREATE INDEX IF NOT EXISTS qa_logs_confidence_idx ON qa_logs (confidence);
CREATE INDEX IF NOT EXISTS qa_logs_evaluation_score_idx ON qa_logs (evaluation_score);
CREATE INDEX IF NOT EXISTS qa_logs_institution_created_idx ON qa_logs (institution_id, created_at);
