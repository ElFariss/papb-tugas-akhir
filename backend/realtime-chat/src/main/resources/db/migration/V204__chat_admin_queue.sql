CREATE TABLE IF NOT EXISTS admin_queue (
    queue_id          SERIAL PRIMARY KEY,
    conversation_id   INTEGER NOT NULL UNIQUE REFERENCES conversations (conversation_id) ON DELETE CASCADE,
    assigned_admin_id INTEGER REFERENCES users (user_id),
    priority          VARCHAR(10) NOT NULL DEFAULT 'normal',
    status            VARCHAR(20) NOT NULL DEFAULT 'waiting', -- waiting | assigned | in_progress | resolved
    queued_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_at       TIMESTAMPTZ,
    resolved_at       TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS admin_queue_status_idx ON admin_queue (status);
CREATE INDEX IF NOT EXISTS admin_queue_priority_idx ON admin_queue (priority);
CREATE INDEX IF NOT EXISTS admin_queue_assigned_admin_id_idx ON admin_queue (assigned_admin_id);
CREATE INDEX IF NOT EXISTS admin_queue_queued_at_idx ON admin_queue (queued_at);
