# :backend:realtime-chat

Owner: **BE-2**. Chat (dual mode), admin queue, sessions, QA logs, QR/PDF summary, AI gateway routes.

**Status: written, NOT compiled against real dependencies.** Run first:

```
./gradlew :backend:realtime-chat:build
```

## Wiring (BE-1 handoff)

```kotlin
fun Application.module() {
    chatModule(
        ChatDependencies(
            db = database,                       // Exposed Database, Flyway already applied
            ragLlm = ragLlmService,              // AI-2 implementation of RagLlmService
            adminAuth = AdminTokenVerifier { token -> /* -> AdminPrincipal? */ },
            adminDirectory = AdminDirectory { ids -> /* userId -> fullName */ },
            activeSessions = null,               // or RedisActiveSessionStore(...)
            config = ChatConfig(publicBaseUrl = "https://app.example"),
        )
    )
}
```

Ports BE-1 must provide: `AdminTokenVerifier`, `AdminPrincipal(userId,email,fullName,role)`, `AdminDirectory`, `Database`.
Migrations `V201..V206` require BE-1 tables `users(user_id)` and `institutions(institution_id)`.

## Endpoints (all under `/api/v1`)

| Area | Routes |
|---|---|
| Chat | `POST /chat/send-message`, `POST /chat/save-conversation`, `GET /chat/conversation/{id}`, `GET\|POST /chat/conversation/{id}/messages`, `POST /chat/conversation/{id}/resolve`, `GET /chat/admin/conversations`, `POST /chat/admin/conversations/{id}/assign`, `POST /chat/admin/respond` |
| Aliases | `POST /conversation/save`, `GET /conversation/{id}` |
| Live | WS `/chat/ws/conversation/{id}`, `/chat/ws/admin`; SSE `/chat/sse/conversation/{id}`, `/chat/sse/admin` (token: Bearer or `?token=`) |
| Session | admin: `/session/{update-active,metrics,start/{id},end/{id}}`; public: `/public-session/{track-sessions,start-session/{id},end-session/{id},session-count}` |
| QA | `/qa-log/*` and `/qa/*`: `log`, `admin-validation`, `gesture`, `institution/{id}`, `admin`, `{qaId}/evaluate`, `health` |
| Summary | `POST /summary/generate`, `GET /summary/{token}` (PDF), `GET /summary/note/{token}` (JSON) |
| AI gateway | `/rag/ask`, `/gesture/ask`, `/gesture/health`, `/faq/{recommendations/{id},refresh/{id},metrics/{id},health}` |

## Deviations from the reference (deliberate)

- Anonymous callers must send `X-Session-Id` (header, or `sessionId` query) for reads, QA writes, summary; foreign conversations return 404.
- Pending `llm_recommendation` messages are admin-only (REST and live events).
- Admin identity comes from the verified principal, never the request body.
- `save-conversation` for anonymous callers rejects `admin`/`system` message types.
- `/session/*` mutation is admin-only; public tracking only adds, never evicts.
- Notes expire after 7 days; regenerate rotates the token.
- Per-IP rate limit (30/min) on public AI routes; queue claim is atomic (409 on conflict); 500s are generic.
- Summary HTML is escaped.

## Open items

- Ktor chosen (3.6.2); BE-1 has not decided Ktor vs Spring. Services are framework-agnostic, only `*Routes.kt` and `ChatModule.kt` are Ktor.
- Contract has no summarize/title method: `SummaryTextGenerator` port (default extractive).
- `askGesture`, `clusterFaqs`, `evaluateResponse` are `TODO()` in AI-2: mapped to 503. AI-2 needs read access to `qa_logs` for `clusterFaqs`.
- Live hub is in-process; multi-instance needs Redis pub/sub.
- Versions pinned inline in `build.gradle.kts` (catalog change needs approval).
- Not ported: `faq/dummy-categories`, Micrometer metrics. Admin names are not joined into QA logs.

## Tests

`./gradlew :backend:realtime-chat:test` — unit tests (title sanitizer, TTL store, limiter, QR/PDF) and H2 + `testApplication` flow tests.
