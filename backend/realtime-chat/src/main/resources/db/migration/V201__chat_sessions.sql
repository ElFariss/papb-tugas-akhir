-- BE-2. Numeric users.user_id is a surrogate for the UUID in users.id.
-- Active-count tracking itself lives in the ActiveSessionStore (Redis).
CREATE TABLE IF NOT EXISTS sessions (
    session_id           SERIAL PRIMARY KEY,
    anonymous_session_id VARCHAR(255),
    user_id              INTEGER REFERENCES users (user_id) ON DELETE CASCADE,
    session_type         VARCHAR(20) NOT NULL DEFAULT 'anonymous',
    session_start        TIMESTAMPTZ NOT NULL DEFAULT now(),
    session_end          TIMESTAMPTZ,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    user_agent           TEXT,
    ip_address           VARCHAR(45),
    device_info          JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS sessions_anonymous_session_id_idx ON sessions (anonymous_session_id);
CREATE INDEX IF NOT EXISTS sessions_user_id_idx ON sessions (user_id);
CREATE INDEX IF NOT EXISTS sessions_session_type_idx ON sessions (session_type);
CREATE INDEX IF NOT EXISTS sessions_is_active_idx ON sessions (is_active);
CREATE INDEX IF NOT EXISTS sessions_session_start_idx ON sessions (session_start);
CREATE INDEX IF NOT EXISTS sessions_created_at_idx ON sessions (created_at);
