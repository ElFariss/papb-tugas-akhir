# Module contracts

Who owns what, and what each module promises the others.

| Role | Module | Status | Provides | Consumes |
|---|---|---|---|---|
| FE | `:app` | placeholder | Android UI | BE-1 and BE-2 HTTP/WebSocket, AI-1 |
| AI-1 | `:ai-client` | **implemented** | `GestureRecognizer`, `SpeechToTextEngine`, `GestureQueryFormatter`, `GestureValidator` | nothing |
| BE-1 | `:backend:server`, `:backend:platform-core` | placeholder | `/api/v1/auth`, `/admin`, `/institutions`, `/service-config`, `/health` | AI-2 `ingestDocument` |
| BE-2 | `:backend:realtime-chat` | placeholder | `/api/v1/chat`, `/session`, `/public-session`, `/qa`, `/summary`, AI gateway | BE-1 auth and database, AI-2 |
| AI-2 | `:backend:rag-llm` | placeholder | `RagLlmService` | Groq, Pinecone, Redis |
| all | `:contracts` | draft | shared enums, `RagLlmService` | nothing |

## Dependency rules

```
:app  ->  :ai-client
:app  ->  :contracts
:backend:server  ->  platform-core, realtime-chat, rag-llm
:backend:realtime-chat  ->  platform-core
every backend module  ->  :contracts
```

- `:ai-client` stays standalone: no dependency on any other module.
- Only `:backend:server` knows `DefaultRagLlmService`; BE-1 and BE-2 depend on the `RagLlmService` interface.
- The app never talks to AI-2 directly.

## Tables

| Owner | Tables |
|---|---|
| BE-1 | `roles` `users` `admin_invitations` `user_sync_log` `app_settings` `institutions` `rag_files` |
| BE-2 | `sessions` `conversations` `messages` `admin_queue` `qa_logs` `notes` |

Only the owner writes to a table. Others go through the owner's module.

## AI-1 to FE

Interfaces and an example are in the [`:ai-client` README](../../ai-client/README.md).

FE must:
- request `CAMERA` / `RECORD_AUDIO` before `start()`,
- call `stop()` when the screen is hidden and `close()` when it is destroyed,
- treat `Failed` and `SpeechError` as states to show, with text input always available as a fallback.

AI-1 guarantees that no camera frame or landmark leaves the device. Speech uses the phone's recognition service,
which may send audio to its provider; FE must say so in the UI.

## AI-1 to BE-2: `POST /api/v1/gesture/ask`

Built by `GestureQueryFormatter`, so FE only forwards it.

| Field | Type | Rule |
|---|---|---|
| `text` | string | required, 1..500 characters |
| `session_id` | string | optional, at most 255, `^[a-zA-Z0-9_-]+$`, no `--`, no SQL keyword as a whole word |
| `language` | string | `id` (default) or `en` |
| `gesture_confidence` | number | optional, 0.0..1.0 |

**Changed from the web app:** there, `gesture_confidence` was practically always 1.0. It is now a real 0..1 value,
so thresholds based on old data need to be revisited.

## AI-2 to BE-1 and BE-2

`id.tunarasa.contracts.RagLlmService`: `ingestDocument`, `askRag`, `askGesture`, `clusterFaqs`,
`evaluateResponse`. The method names come from the task table; **the return types are a draft** that AI-2, BE-1 and
BE-2 still have to confirm.

### BE-1 document ingestion

BE-1 supplies a `RagDocumentSource` when wiring `DefaultRagLlmService`. Resolve the upload using
both `ragFileId` and `institutionSlug`, returning a readable local path and the original filename.
Each upload ID identifies immutable file contents. BE-1 owns the job and persists
`IngestionResult.status` and the error or chunk count. Keep the file available for retries and
prevent a duplicate job from overwriting a completed status.
Only completed uploads are eligible for retrieval because a failed job may have written some vectors.
See [AI-2 ingestion configuration](../../backend/rag-llm/README.md#document-ingestion).

### BE-2 institution Q&A

Once BE-2 authorizes the session and resolves its institution, call
`askRag(question, sessionId, institutionSlug)` and forward the returned answer and sources.
BE-1 supplies `CompletedRagDocuments` to restrict retrieval to current completed uploads
from that institution. Treat `IllegalArgumentException` as invalid input and `IOException`
as a failure to complete the request. Empty sources represent an abstention or a prompt to
select an institution. The response confidence is derived from retrieval similarity.
See [AI-2 Q&A configuration](../../backend/rag-llm/README.md#institution-qa).
