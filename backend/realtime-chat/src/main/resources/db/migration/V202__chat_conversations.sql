-- BE-2. Requires BE-1's numeric users.user_id and institutions.id.
CREATE TABLE IF NOT EXISTS conversations (
    conversation_id   SERIAL PRIMARY KEY,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    session_id        VARCHAR(255) NOT NULL,
    user_agent        TEXT,
    ip_address        VARCHAR(45),
    service_mode      VARCHAR(30) NOT NULL DEFAULT 'full_llm_bot',
    assigned_admin_id INTEGER REFERENCES users (user_id),
    institution_id    INTEGER REFERENCES institutions (id),
    status            VARCHAR(20) NOT NULL DEFAULT 'active',
    priority          VARCHAR(10) NOT NULL DEFAULT 'normal',
    last_message_at   TIMESTAMPTZ DEFAULT now(),
    resolved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS conversations_session_id_idx ON conversations (session_id);
CREATE INDEX IF NOT EXISTS conversations_is_active_idx ON conversations (is_active);
CREATE INDEX IF NOT EXISTS conversations_service_mode_idx ON conversations (service_mode);
CREATE INDEX IF NOT EXISTS conversations_assigned_admin_id_idx ON conversations (assigned_admin_id);
CREATE INDEX IF NOT EXISTS conversations_institution_id_idx ON conversations (institution_id);
CREATE INDEX IF NOT EXISTS conversations_status_idx ON conversations (status);
CREATE INDEX IF NOT EXISTS conversations_last_message_at_idx ON conversations (last_message_at);
CREATE INDEX IF NOT EXISTS conversations_created_at_idx ON conversations (created_at);
