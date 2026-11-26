package id.tunarasa.backend.platform.health

import id.tunarasa.backend.platform.db.DatabaseFactory
import id.tunarasa.backend.platform.db.RedisClient
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class HealthStatus(
    val status: String,
    val database: String,
    val redis: String,
    val timestamp: Long = System.currentTimeMillis(),
)

object HealthRoutes {
    fun register(route: Route) {
        route.route("/api/v1/health") {
            get {
                var dbStatus = "up"
                try {
                    DatabaseFactory.dbQuery { }
                } catch (e: Exception) {
                    dbStatus = "down: ${e.message}"
                }

                val redisStatus = if (RedisClient.isConnected()) "up" else "down/unavailable"
                val overallStatus = if (dbStatus == "up") "UP" else "DEGRADED"

                call.respond(
                    if (overallStatus == "UP") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                    HealthStatus(
                        status = overallStatus,
                        database = dbStatus,
                        redis = redisStatus,
                    ),
                )
            }

            get("/liveness") {
                call.respond(HttpStatusCode.OK, mapOf("status" to "alive"))
            }

            get("/readiness") {
                call.respond(HttpStatusCode.OK, mapOf("status" to "ready"))
            }
        }
    }
}
