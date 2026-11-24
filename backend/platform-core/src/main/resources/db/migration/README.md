# Migrations

Owner: BE-1 runs them; each table's owner writes its file. **Never edit a merged migration, add a new one.**

| Prefix | Owner | Tables |
|---|---|---|
| `V1xx__*.sql` | BE-1 | roles, users, admin_invitations, user_sync_log, app_settings, institutions, rag_files |
| `V2xx__*.sql` | BE-2 | sessions, conversations, messages, admin_queue, qa_logs, notes (in `realtime-chat`) |

BE-2's tables reference BE-1's (`users`, `institutions`), so V1xx always runs first.
