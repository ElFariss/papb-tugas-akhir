package id.tunarasa.backend.platform.middleware

import id.tunarasa.backend.platform.auth.userPrincipal
import id.tunarasa.backend.platform.db.RedisClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.plugins.origin
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

class RateLimitConfig {
    var maxRequestsPerMinute: Int = 60
}

val RateLimitingPlugin = createRouteScopedPlugin(
    name = "RateLimitingPlugin",
    createConfiguration = ::RateLimitConfig,
) {
    val limit = pluginConfig.maxRequestsPerMinute

    onCall { call ->
        val principal = call.userPrincipal()
        val clientKey = principal?.userId
            ?: call.request.headers["X-Forwarded-For"]
            ?: call.request.origin.remoteHost

        val currentMinute = System.currentTimeMillis() / 60000
        val redisKey = "ratelimit:$clientKey:$currentMinute"

        val currentCount = try {
            val countStr = RedisClient.get(redisKey)
            val count = (countStr?.toIntOrNull() ?: 0) + 1
            RedisClient.set(redisKey, count.toString(), ttlSeconds = 60)
            count
        } catch (_: Exception) {
            1 // If Redis is unavailable, fallback gracefully
        }

        call.response.header("X-RateLimit-Limit", limit.toString())
        call.response.header("X-RateLimit-Remaining", (limit - currentCount).coerceAtLeast(0).toString())

        if (currentCount > limit) {
            call.response.header(HttpHeaders.RetryAfter, "60")
            call.respond(
                HttpStatusCode.TooManyRequests,
                mapOf("error" to "Too Many Requests. Rate limit exceeded ($limit req/min). Please try again in 60 seconds."),
            )
        }
    }
}

fun Route.rateLimit(maxRequestsPerMinute: Int = 60, build: Route.() -> Unit) {
    install(RateLimitingPlugin) {
        this.maxRequestsPerMinute = maxRequestsPerMinute
    }
    build()
}
