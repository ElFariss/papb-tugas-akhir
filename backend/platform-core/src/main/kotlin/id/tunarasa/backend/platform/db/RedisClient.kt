package id.tunarasa.backend.platform.db

import io.ktor.util.logging.KtorSimpleLogger
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

/**
 * Shared Redis client managed by BE-1, consumed by BE-2 and AI-2.
 */
object RedisClient {
    private val logger = KtorSimpleLogger("RedisClient")
    private var pool: JedisPool? = null

    fun init() {
        val host = System.getenv("REDIS_HOST") ?: "localhost"
        val port = System.getenv("REDIS_PORT")?.toIntOrNull() ?: 6379
        val password = System.getenv("REDIS_PASSWORD")

        logger.info("Initializing Redis Client at $host:$port")

        val config = JedisPoolConfig().apply {
            maxTotal = 16
            maxIdle = 8
            minIdle = 2
        }

        pool = if (password.isNullOrEmpty()) {
            JedisPool(config, host, port)
        } else {
            JedisPool(config, host, port, 2000, password)
        }
    }

    fun get(key: String): String? {
        return try {
            pool?.resource?.use { it.get(key) }
        } catch (e: Exception) {
            logger.warn("Redis GET failed for key $key: ${e.message}")
            null
        }
    }

    fun set(key: String, value: String, ttlSeconds: Long? = null) {
        try {
            pool?.resource?.use { jedis ->
                if (ttlSeconds != null) {
                    jedis.setex(key, ttlSeconds, value)
                } else {
                    jedis.set(key, value)
                }
            }
        } catch (e: Exception) {
            logger.warn("Redis SET failed for key $key: ${e.message}")
        }
    }

    fun delete(key: String) {
        try {
            pool?.resource?.use { it.del(key) }
        } catch (e: Exception) {
            logger.warn("Redis DEL failed for key $key: ${e.message}")
        }
    }

    fun isConnected(): Boolean {
        return try {
            pool?.resource?.use { it.ping() == "PONG" } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun close() {
        pool?.close()
        pool = null
    }
}
