-- expires_at is new vs the reference: the reference advertised a 7-day QR expiry but never enforced it.
CREATE TABLE IF NOT EXISTS notes (
    note_id         SERIAL PRIMARY KEY,
    conversation_id INTEGER NOT NULL REFERENCES conversations (conversation_id) ON DELETE CASCADE,
    note_content    TEXT NOT NULL,
    title           VARCHAR(255),
    url_access      VARCHAR(255),
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS notes_conversation_id_idx ON notes (conversation_id);
CREATE UNIQUE INDEX IF NOT EXISTS notes_url_access_uidx ON notes (url_access);
CREATE INDEX IF NOT EXISTS notes_created_at_idx ON notes (created_at);
