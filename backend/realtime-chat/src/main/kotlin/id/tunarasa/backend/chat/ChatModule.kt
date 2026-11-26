package id.tunarasa.backend.chat

import id.tunarasa.backend.chat.chat.ChatRoutes
import id.tunarasa.backend.chat.chat.ChatService
import id.tunarasa.backend.chat.chat.LiveUpdateHub
import id.tunarasa.backend.chat.chat.chatLiveRoutes
import id.tunarasa.backend.chat.gateway.AiGatewayRoutes
import id.tunarasa.backend.chat.http.ChatAuth
import id.tunarasa.backend.chat.queue.AdminQueueRoutes
import id.tunarasa.backend.chat.queue.AdminQueueService
import id.tunarasa.backend.chat.qa.QaLogRoutes
import id.tunarasa.backend.chat.qa.QaLogService
import id.tunarasa.backend.chat.session.ActiveSessionStore
import id.tunarasa.backend.chat.session.InMemoryActiveSessionStore
import id.tunarasa.backend.chat.session.SessionRoutes
import id.tunarasa.backend.chat.session.SessionService
import id.tunarasa.backend.chat.summary.ExtractiveSummaryTextGenerator
import id.tunarasa.backend.chat.summary.PdfSummaryGenerator
import id.tunarasa.backend.chat.summary.QrCodeGenerator
import id.tunarasa.backend.chat.summary.SummaryRoutes
import id.tunarasa.backend.chat.summary.SummaryService
import id.tunarasa.backend.chat.summary.SummaryTextGenerator
import id.tunarasa.contracts.RagLlmService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.websocket.WebSockets
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

/** Who is calling. Produced by BE-1's Supabase JWT verification (`SupabaseJwtVerifier`). */
data class AdminPrincipal(
    /** `users.user_id`. Written to `messages.admin_id`, `qa_logs.admin_id`, `conversations.assigned_admin_id`. */
    val userId: Int,
    val email: String?,
    val fullName: String?,
    /** `roles.role_name`: `superadmin`, `admin` or `user`. */
    val role: String,
) {
    val isAdmin: Boolean get() = role == "admin" || role == "superadmin"
}

/**
 * PORT implemented by BE-1. Returns null for an invalid / expired token.
 * BE-2 never parses JWTs itself.
 */
fun interface AdminTokenVerifier {
    suspend fun verify(bearerToken: String): AdminPrincipal?
}

class ChatConfig(
    /** Base URL embedded in QR codes. Reference: `NEXT_PUBLIC_APP_URL` (default `http://localhost:3000`). */
    val publicBaseUrl: String = "http://localhost:3000",
    /** Reference advertised 7 days but never enforced it; BE-2 enforces it. */
    val noteTtlDays: Long = 7,
    /** Reference: `AbortSignal.timeout(30000)` on the RAG call in send-message. */
    val ragTimeoutMillis: Long = 30_000,
    /** Reference admin list: conversations created in the last 24 hours, max 20. */
    val adminListWindowHours: Long = 24,
    val adminListLimit: Int = 20,
    /** Active-session TTL when a client stops reporting (reference had none: sessions leaked forever). */
    val activeSessionTtlSeconds: Long = 30 * 60,
    /** Default institution when a client sends none (reference default: 1). */
    val defaultInstitutionId: Int = 1,
    /** Max entries accepted by `/public-session/track-sessions`. */
    val maxTrackedSessionsPerRequest: Int = 1000,
    /** Per-IP budget for the public AI endpoints (`/rag/ask`, `/gesture/ask`, `/faq/...`); every call costs LLM money. */
    val publicAiRequestsPerMinute: Int = 30,
)

/**
 * PORT implemented by BE-1 (owns `users`). Resolves `users.user_id` -> display name for admin replies.
 * Unknown ids may be omitted from the map; callers fall back to "Admin".
 */
fun interface AdminDirectory {
    suspend fun displayNames(userIds: Set<Int>): Map<Int, String>
}

/**
 * Everything BE-2 needs from the outside. Construct once in `:backend:server` / BE-1's bootstrap:
 *
 * ```kotlin
 * fun Application.module() {
 *     chatModule(
 *         ChatDependencies(
 *             db = database,                       // Exposed Database already migrated by Flyway (V1xx then V2xx)
 *             ragLlm = ragLlmService,              // AI-2
 *             adminAuth = { token -> jwtVerifier.verifyAdmin(token) },   // BE-1
 *             activeSessions = RedisActiveSessionStore(redisCommands),   // optional, BE-1's Redis
 *             config = ChatConfig(publicBaseUrl = env("APP_URL")),
 *         )
 *     )
 * }
 * ```
 */
class ChatDependencies(
    val db: Database,
    val ragLlm: RagLlmService,
    val adminAuth: AdminTokenVerifier,
    val activeSessions: ActiveSessionStore = InMemoryActiveSessionStore(),
    /** Contract gap: `RagLlmService` has no summarize/title. Default is extractive and needs no LLM. */
    val summaryText: SummaryTextGenerator = ExtractiveSummaryTextGenerator(),
    val config: ChatConfig = ChatConfig(),
    val clock: Clock = Clock.systemUTC(),
    val adminDirectory: AdminDirectory = AdminDirectory { emptyMap() },
)

/**
 * Installs WebSockets + SSE (idempotent) and every BE-2 route under `/api/v1`.
 * Does not install ContentNegotiation / StatusPages / CORS: those belong to the host app (BE-1).
 */
fun Application.chatModule(deps: ChatDependencies) {
    install(WebSockets) {
        pingPeriodMillis = 20_000
        timeoutMillis = 30_000
        maxFrameSize = 64L * 1024
    }
    install(SSE)

    val auth = ChatAuth(deps.adminAuth)
    val hub = LiveUpdateHub()
    val qaLog = QaLogService(deps.db, deps.clock)
    val chat = ChatService(deps.db, deps.ragLlm, hub, qaLog, deps.config, deps.clock, deps.adminDirectory)
    val queue = AdminQueueService(deps.db, hub, qaLog, deps.config, deps.clock)
    val sessions = SessionService(deps.db, deps.activeSessions, deps.config, deps.clock)
    val summary = SummaryService(deps.db, deps.summaryText, QrCodeGenerator(), PdfSummaryGenerator(), deps.config, deps.clock)

    routing {
        route("/api/v1") {
            SessionRoutes.install(this, sessions, auth, deps.config)
            ChatRoutes.install(this, chat, queue, auth)
            chatLiveRoutes(chat, hub, auth)
            AdminQueueRoutes.install(this, queue, auth)
            QaLogRoutes.install(this, qaLog, chat, deps.ragLlm, auth, deps.config)
            SummaryRoutes.install(this, summary, chat, auth)
            AiGatewayRoutes.install(this, deps.ragLlm, qaLog, deps.config, auth)
        }
    }
}
