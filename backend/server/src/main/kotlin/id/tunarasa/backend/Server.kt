package id.tunarasa.backend

import id.tunarasa.backend.platform.admin.AdminRoutes
import id.tunarasa.backend.platform.auth.AuthRoutes
import id.tunarasa.backend.platform.auth.SupabaseJwtVerifier
import id.tunarasa.backend.platform.db.DatabaseFactory
import id.tunarasa.backend.platform.db.RedisClient
import id.tunarasa.backend.platform.health.HealthRoutes
import id.tunarasa.backend.platform.institutions.InstitutionRoutes
import id.tunarasa.backend.platform.institutions.RagUploadRoutes
import id.tunarasa.backend.platform.middleware.RateLimitingPlugin
import id.tunarasa.backend.chat.AdminDirectory
import id.tunarasa.backend.chat.AdminPrincipal
import id.tunarasa.backend.chat.AdminTokenVerifier
import id.tunarasa.backend.chat.ChatDependencies
import id.tunarasa.backend.chat.chatModule
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.json.Json

private val logger = KtorSimpleLogger("Server")

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"

    logger.info("Starting Tunarasa Backend Server on $host:$port...")

    // 1. Initialize Database & Migrations
    DatabaseFactory.init()

    // 2. Initialize Redis Client
    try {
        RedisClient.init()
    } catch (e: Exception) {
        logger.warn("Redis initialization skipped or failed: ${e.message}")
    }

    // 3. Start Embedded Ktor Netty Server
    embeddedServer(Netty, port = port, host = host) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    val ragLlmService = createRagLlmService()
    val appPrometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    // Prometheus Metrics
    install(MicrometerMetrics) {
        registry = appPrometheusRegistry
    }

    // Content Negotiation (JSON)
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // Logging
    install(CallLogging)

    // Global Rate Limiting
    install(RateLimitingPlugin)

    // CORS Configuration
    install(CORS) {
        val allowedOrigins = System.getenv("ALLOWED_ORIGINS")?.split(",")
        if (allowedOrigins.isNullOrEmpty()) {
            anyHost() // Development mode fallback
        } else {
            allowedOrigins.forEach { allowHost(it.trim()) }
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
    }

    // Authentication (Supabase JWT)
    install(Authentication) {
        jwt("supabase-jwt") {
            realm = "Tunarasa Backend"
            verifier(SupabaseJwtVerifier.verifier)
            validate { _ ->
                val authHeader = request.header(HttpHeaders.Authorization)
                val token = authHeader?.removePrefix("Bearer ") ?: return@validate null
                SupabaseJwtVerifier.resolvePrincipal(token)
            }
        }
    }

    // Error Handling
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Unhandled Exception: ${cause.message}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.localizedMessage ?: "Internal Server Error")),
            )
        }
    }

    // Wiring Routes
    routing {
        get("/metrics") {
            call.respondText(appPrometheusRegistry.scrape())
        }
        HealthRoutes.register(this)
        AuthRoutes.register(this)
        AdminRoutes.register(this)
        InstitutionRoutes.register(this)
        RagUploadRoutes.register(this, ragLlmService)
    }

    chatModule(
        ChatDependencies(
            db = org.jetbrains.exposed.v1.jdbc.Database.connect(DatabaseFactory.pooledDataSource()),
            ragLlm = ragLlmService,
            adminAuth = AdminTokenVerifier { token ->
                val principal = SupabaseJwtVerifier.resolvePrincipal(token)
                if (principal == null || principal.roleName != "super_admin") {
                    null
                } else {
                    DatabaseFactory.chatUserId(principal.userId)?.let { userId ->
                        AdminPrincipal(
                            userId = userId,
                            email = principal.email,
                            fullName = principal.fullName,
                            role = "superadmin",
                        )
                    }
                }
            },
            adminDirectory = AdminDirectory(DatabaseFactory::chatAdminNames),
        ),
    )
}
