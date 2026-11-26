CREATE TABLE IF NOT EXISTS messages (
    message_id        SERIAL PRIMARY KEY,
    conversation_id   INTEGER NOT NULL REFERENCES conversations (conversation_id) ON DELETE CASCADE,
    message_content   TEXT NOT NULL,
    message_type      VARCHAR(20) NOT NULL DEFAULT 'user',   -- user | admin | llm_bot | llm_recommendation | system
    admin_id          INTEGER REFERENCES users (user_id),
    parent_message_id INTEGER REFERENCES messages (message_id) ON DELETE SET NULL,
    confidence        INTEGER,                                -- 0-100
    is_read           BOOLEAN NOT NULL DEFAULT FALSE,
    input_method      VARCHAR(20) DEFAULT 'text',             -- text | speech | gesture
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS messages_conversation_id_idx ON messages (conversation_id);
CREATE INDEX IF NOT EXISTS messages_message_type_idx ON messages (message_type);
CREATE INDEX IF NOT EXISTS messages_admin_id_idx ON messages (admin_id);
CREATE INDEX IF NOT EXISTS messages_parent_message_id_idx ON messages (parent_message_id);
CREATE INDEX IF NOT EXISTS messages_is_read_idx ON messages (is_read);
CREATE INDEX IF NOT EXISTS messages_created_at_idx ON messages (created_at);
