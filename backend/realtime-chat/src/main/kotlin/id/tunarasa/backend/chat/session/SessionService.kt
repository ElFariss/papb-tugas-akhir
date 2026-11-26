package id.tunarasa.backend.chat.session

import id.tunarasa.backend.chat.ChatConfig
import id.tunarasa.backend.chat.db.SessionsTable
import id.tunarasa.backend.chat.db.tx
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.time.Clock

/** Active-session metrics (store) + durable `sessions` rows (DB, best effort). */
internal class SessionService(
    private val db: Database,
    private val store: ActiveSessionStore,
    @Suppress("unused") private val config: ChatConfig,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(SessionService::class.java)

    suspend fun count(): Int = store.count()

    /** Admin: replace the active set with what the client reports. */
    suspend fun replace(ids: Set<String>): Int {
        store.replaceWith(ids)
        return store.count()
    }

    /** Public: only adds / refreshes. A public caller must not be able to evict other users' sessions. */
    suspend fun track(ids: Set<String>): Int {
        ids.forEach { store.touch(it) }
        return store.count()
    }

    suspend fun start(sessionId: String, userAgent: String?, ip: String?): Int {
        store.touch(sessionId)
        persist("start $sessionId") {
            db.tx {
                val open = SessionsTable.selectAll()
                    .where { (SessionsTable.anonymousSessionId eq sessionId) and (SessionsTable.isActive eq true) }
                    .limit(1).count() > 0
                if (!open) {
                    val now = clock.instant()
                    SessionsTable.insert {
                        it[anonymousSessionId] = sessionId
                        it[sessionType] = "anonymous"
                        it[sessionStart] = now
                        it[isActive] = true
                        it[SessionsTable.userAgent] = userAgent
                        it[ipAddress] = ip
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }
            }
        }
        return store.count()
    }

    suspend fun end(sessionId: String): Int {
        store.remove(sessionId)
        persist("end $sessionId") {
            db.tx {
                val now = clock.instant()
                SessionsTable.update({ (SessionsTable.anonymousSessionId eq sessionId) and (SessionsTable.isActive eq true) }) {
                    it[isActive] = false
                    it[sessionEnd] = now
                    it[updatedAt] = now
                }
            }
        }
        return store.count()
    }

    /** Metrics must keep working when the DB hiccups; the reference had no DB in this path at all. */
    private suspend fun persist(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("sessions row not persisted ({}): {}", what, e.toString())
        }
    }
}
