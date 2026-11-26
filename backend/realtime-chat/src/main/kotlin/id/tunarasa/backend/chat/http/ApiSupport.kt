package id.tunarasa.backend.chat.http

import id.tunarasa.backend.chat.AdminPrincipal
import id.tunarasa.backend.chat.AdminTokenVerifier
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant

internal val ApiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private const val MAX_BODY_BYTES = 1_000_000L
internal val SESSION_ID_REGEX = Regex("^[a-zA-Z0-9_-]{1,255}$")

/** Thrown anywhere below a route; [handle] turns it into a JSON error response. */
internal class ApiException(
    val status: HttpStatusCode,
    override val message: String,
    val code: String = status.description.uppercase().replace(' ', '_'),
) : RuntimeException(message)

internal fun badRequest(message: String): Nothing = throw ApiException(HttpStatusCode.BadRequest, message)
internal fun unauthorized(message: String = "Authentication required"): Nothing =
    throw ApiException(HttpStatusCode.Unauthorized, message)
internal fun forbidden(message: String = "Forbidden - Admin access required"): Nothing =
    throw ApiException(HttpStatusCode.Forbidden, message)
internal fun notFound(message: String): Nothing = throw ApiException(HttpStatusCode.NotFound, message)
internal fun conflict(message: String): Nothing = throw ApiException(HttpStatusCode.Conflict, message)
internal fun unavailable(message: String): Nothing = throw ApiException(HttpStatusCode.ServiceUnavailable, message)

internal suspend fun ApplicationCall.respondJson(
    body: JsonElement,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respondText(ApiJson.encodeToString(JsonElement.serializer(), body), ContentType.Application.Json, status)

/**
 * Runs [block]; maps [ApiException] to `{success:false,error,detail,code}`.
 * `error` is what the Next.js routes returned, `detail` what FastAPI returned; clients may read either.
 * Anything unexpected becomes a generic 500 (no exception text leaks, unlike the reference).
 */
internal suspend fun ApplicationCall.handle(envelope: Boolean = false, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        respondJson(if (envelope) envelopeError(e.code, e.message) else errorBody(e.message, e.code), e.status)
    } catch (e: Throwable) {
        application.log.error("Unhandled error on ${request.local.method.value} ${request.local.uri}", e)
        respondJson(
            if (envelope) envelopeError("INTERNAL_SERVER_ERROR", "Internal server error")
            else errorBody("Internal server error", "INTERNAL_SERVER_ERROR"),
            HttpStatusCode.InternalServerError,
        )
    }
}

/** Reference `ApiResponse[T]` envelope (session + gesture endpoints): `{success,data,error,metadata}`. */
internal fun envelopeOk(data: JsonElement): JsonObject = buildJsonObject {
    put("success", true)
    put("data", data)
    put("error", JsonNull)
    put("metadata", buildJsonObject { put("timestamp", Instant.now().toString()); put("version", "1.0.0") })
}

internal fun envelopeError(code: String, message: String): JsonObject = buildJsonObject {
    put("success", false)
    put("data", JsonNull)
    put("error", buildJsonObject { put("code", code); put("message", message) })
    put("metadata", buildJsonObject { put("timestamp", Instant.now().toString()); put("version", "1.0.0") })
}

internal fun errorBody(message: String, code: String): JsonObject = buildJsonObject {
    put("success", false)
    put("error", message)
    put("detail", message)
    put("code", code)
}

/** Reads and parses the body as a JSON object. 400 on empty / malformed / non-object / oversized. */
internal suspend fun ApplicationCall.readJsonObject(): JsonObject {
    val declared = request.contentLength()
    if (declared != null && declared > MAX_BODY_BYTES) throw ApiException(HttpStatusCode.PayloadTooLarge, "Body too large")
    val text = receiveText()
    if (text.length > MAX_BODY_BYTES) throw ApiException(HttpStatusCode.PayloadTooLarge, "Body too large")
    if (text.isBlank()) badRequest("Request body is required")
    val parsed = try {
        ApiJson.parseToJsonElement(text)
    } catch (_: SerializationException) {
        badRequest("Malformed JSON body")
    } catch (_: IllegalArgumentException) {
        badRequest("Malformed JSON body")
    }
    return parsed as? JsonObject ?: badRequest("JSON object expected")
}

// ---------- field readers ----------

private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

/** First present key among [keys] (camelCase and snake_case are both accepted by the callers). */
private fun JsonObject.primAny(vararg keys: String): Pair<String, JsonPrimitive>? =
    keys.firstNotNullOfOrNull { k -> prim(k)?.let { k to it } }

internal fun JsonObject.string(
    vararg keys: String,
    required: Boolean = false,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    pattern: Regex? = null,
): String? {
    val label = keys.first()
    val found = primAny(*keys)
    if (found == null) {
        if (required) badRequest("$label is required")
        return null
    }
    val p = found.second
    if (!p.isString) badRequest("$label must be a string")
    val v = p.content
    if (v.length < min) badRequest(if (min == 1) "$label must not be empty" else "$label must be at least $min characters")
    if (v.length > max) badRequest("$label must be at most $max characters")
    if (pattern != null && !pattern.matches(v)) badRequest("$label has an invalid format")
    return v
}

internal fun JsonObject.int(vararg keys: String, required: Boolean = false, min: Int? = null, max: Int? = null): Int? {
    val label = keys.first()
    val found = primAny(*keys)
    if (found == null) {
        if (required) badRequest("$label is required")
        return null
    }
    val p = found.second
    // Accept "12" as well as 12: the Next.js clients send ids as strings.
    val v = p.intOrNull ?: badRequest("$label must be an integer")
    if (min != null && v < min) badRequest("$label must be >= $min")
    if (max != null && v > max) badRequest("$label must be <= $max")
    return v
}

internal fun JsonObject.double(vararg keys: String, min: Double? = null, max: Double? = null): Double? {
    val label = keys.first()
    val p = primAny(*keys)?.second ?: return null
    val v = p.doubleOrNull ?: badRequest("$label must be a number")
    if (min != null && v < min) badRequest("$label must be >= $min")
    if (max != null && v > max) badRequest("$label must be <= $max")
    return v
}

internal fun JsonObject.bool(vararg keys: String): Boolean? {
    val p = primAny(*keys)?.second ?: return null
    return p.booleanOrNull ?: badRequest("${keys.first()} must be a boolean")
}

internal fun JsonObject.array(key: String, required: Boolean = false): JsonArray? {
    val e = this[key]
    if (e == null || e is JsonNull) {
        if (required) badRequest("$key is required")
        return null
    }
    return e as? JsonArray ?: badRequest("$key must be an array")
}

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun enumOf(label: String, value: String?, allowed: Set<String>, default: String): String {
    if (value == null) return default
    if (value !in allowed) badRequest("$label must be one of ${allowed.joinToString("|")}")
    return value
}

// ---------- request helpers ----------

internal fun ApplicationCall.pathInt(name: String): Int =
    parameters[name]?.toIntOrNull()?.takeIf { it > 0 } ?: badRequest("Invalid $name")

internal fun ApplicationCall.queryInt(name: String, default: Int, min: Int, max: Int): Int {
    val raw = request.queryParameters[name] ?: return default
    val v = raw.toIntOrNull() ?: badRequest("$name must be an integer")
    return v.coerceIn(min, max)
}

internal fun ApplicationCall.queryIntOrNull(name: String): Int? =
    request.queryParameters[name]?.takeIf { it.isNotBlank() }?.let { it.toIntOrNull() ?: badRequest("$name must be an integer") }

internal fun ApplicationCall.queryInstantOrNull(name: String): Instant? =
    request.queryParameters[name]?.takeIf { it.isNotBlank() }?.let {
        try {
            Instant.parse(it)
        } catch (_: Exception) {
            badRequest("$name must be an ISO-8601 instant (e.g. 2025-07-18T10:30:00Z)")
        }
    }

/** Anonymous caller identity: `X-Session-Id` header, or `sessionId` / `session_id` query parameter. */
internal fun ApplicationCall.sessionIdOrNull(): String? {
    val raw = request.headers["X-Session-Id"]
        ?: request.queryParameters["sessionId"]
        ?: request.queryParameters["session_id"]
    return raw?.takeIf { SESSION_ID_REGEX.matches(it) }
}

internal fun ApplicationCall.clientIp(): String? =
    (request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()
        ?: request.headers["X-Real-IP"]
        ?: request.local.remoteAddress)?.take(45)

internal fun ApplicationCall.userAgent(): String? = request.headers[HttpHeaders.UserAgent]?.take(1000)

// ---------- admin auth ----------

internal class ChatAuth(private val verifier: AdminTokenVerifier) {

    fun bearerToken(call: ApplicationCall): String? {
        val h = call.request.headers[HttpHeaders.Authorization] ?: return null
        if (!h.startsWith("Bearer ", ignoreCase = true)) return null
        return h.substring(7).trim().takeIf { it.isNotEmpty() }
    }

    /** Valid admin/superadmin principal, or null when the caller is anonymous or not an admin. */
    suspend fun adminOrNull(call: ApplicationCall, queryTokenFallback: Boolean = false): AdminPrincipal? {
        val token = bearerToken(call)
            ?: if (queryTokenFallback) call.request.queryParameters["token"]?.takeIf { it.isNotBlank() } else null
        token ?: return null
        val principal = verifier.verify(token) ?: return null
        return principal.takeIf { it.isAdmin }
    }

    suspend fun requireAdmin(call: ApplicationCall, queryTokenFallback: Boolean = false): AdminPrincipal {
        val token = bearerToken(call)
            ?: if (queryTokenFallback) call.request.queryParameters["token"]?.takeIf { it.isNotBlank() } else null
        token ?: unauthorized()
        val principal = verifier.verify(token) ?: unauthorized("Invalid or expired token")
        if (!principal.isAdmin) forbidden()
        return principal
    }
}

internal fun JsonObjectBuilder.putInstant(key: String, value: Instant?) {
    put(key, value?.toString())
}
