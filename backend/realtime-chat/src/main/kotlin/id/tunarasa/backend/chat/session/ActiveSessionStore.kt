package id.tunarasa.backend.chat.session

import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Replaces the reference's process-local `metrics_service.active_sessions` set.
 * Entries carry a last-seen time and expire after a TTL, so a client that never calls `end` does not
 * inflate the count forever (the reference leaked them).
 */
interface ActiveSessionStore {
    /** Adds the session or refreshes its last-seen time. */
    suspend fun touch(sessionId: String)

    suspend fun remove(sessionId: String)

    /** Reference `cleanup_expired_sessions` + re-add: after this call exactly [sessionIds] are active. */
    suspend fun replaceWith(sessionIds: Set<String>)

    suspend fun count(): Int
}

class InMemoryActiveSessionStore(
    private val ttl: Duration = Duration.ofMinutes(30),
    private val clock: Clock = Clock.systemUTC(),
) : ActiveSessionStore {
    private val lastSeen = ConcurrentHashMap<String, Long>()

    private fun prune() {
        val cutoff = clock.millis() - ttl.toMillis()
        lastSeen.entries.removeIf { it.value < cutoff }
    }

    override suspend fun touch(sessionId: String) {
        lastSeen[sessionId] = clock.millis()
    }

    override suspend fun remove(sessionId: String) {
        lastSeen.remove(sessionId)
    }

    override suspend fun replaceWith(sessionIds: Set<String>) {
        val now = clock.millis()
        lastSeen.keys.retainAll(sessionIds)
        sessionIds.forEach { lastSeen[it] = now }
    }

    override suspend fun count(): Int {
        prune()
        return lastSeen.size
    }
}

/**
 * Shared across server instances. One sorted set: member = session id, score = last-seen epoch millis.
 * Pass BE-1's Lettuce `connection.sync()`. Lettuce's sync API is blocking, so calls hop to Dispatchers.IO.
 */
class RedisActiveSessionStore(
    private val redis: RedisCommands<String, String>,
    private val ttl: Duration = Duration.ofMinutes(30),
    private val key: String = "tunarasa:active_sessions",
    private val clock: Clock = Clock.systemUTC(),
) : ActiveSessionStore {

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun prune() {
        redis.zremrangebyscore(key, "-inf", (clock.millis() - ttl.toMillis()).toString())
    }

    override suspend fun touch(sessionId: String) {
        io { redis.zadd(key, clock.millis().toDouble(), sessionId) }
    }

    override suspend fun remove(sessionId: String) {
        io { redis.zrem(key, sessionId) }
    }

    override suspend fun replaceWith(sessionIds: Set<String>) {
        io {
            val stale = redis.zrange(key, 0, -1).filterNot { it in sessionIds }
            if (stale.isNotEmpty()) redis.zrem(key, *stale.toTypedArray())
            val now = clock.millis().toDouble()
            sessionIds.forEach { redis.zadd(key, now, it) }
        }
    }

    override suspend fun count(): Int = io {
        prune()
        redis.zcard(key).toInt()
    }
}
