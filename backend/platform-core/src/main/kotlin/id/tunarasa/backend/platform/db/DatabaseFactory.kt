package id.tunarasa.backend.platform.db

import org.jetbrains.exposed.v1.core.eq

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.util.logging.KtorSimpleLogger
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

object DatabaseFactory {
    private val logger = KtorSimpleLogger("DatabaseFactory")
    @Volatile
    private var dataSource: HikariDataSource? = null

    fun init() {
        val jdbcUrl = System.getenv("JDBC_URL")
            ?: "jdbc:h2:mem:tunarasa_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
        val driverClass = System.getenv("DB_DRIVER")
            ?: if (jdbcUrl.contains("h2")) "org.h2.Driver" else "org.postgresql.Driver"
        val user = System.getenv("DB_USER") ?: "sa"
        val password = System.getenv("DB_PASSWORD") ?: ""

        logger.info("Initializing Database Connection Pool to: $jdbcUrl")

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.driverClassName = driverClass
            this.username = user
            this.password = password
            maximumPoolSize = System.getenv("DB_MAX_POOL_SIZE")?.toIntOrNull() ?: 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        val dataSource = HikariDataSource(config)
        this.dataSource = dataSource

        // The chat migrations target PostgreSQL's TIMESTAMPTZ spelling. H2's
        // PostgreSQL compatibility mode does not recognize that alias, so
        // define it as a domain before Flyway parses the shared migrations.
        // Keep this local to H2; PostgreSQL must continue using its native type.
        if (jdbcUrl.startsWith("jdbc:h2:", ignoreCase = true)) {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        "CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE",
                    )
                }
            }
        }

        // Execute Flyway migrations
        runFlywayMigration(dataSource)

        // Connect Exposed ORM
        Database.connect(dataSource)
        logger.info("Database initialized successfully.")
    }

    /** Shared JDBC pool used by the Exposed 1.x chat module. */
    fun pooledDataSource(): DataSource =
        checkNotNull(dataSource) { "DatabaseFactory.init() must run before chat is configured" }

    private fun runFlywayMigration(dataSource: HikariDataSource) {
        logger.info("Starting Flyway DB Migration...")
        try {
            val flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
            val migrationResult = flyway.migrate()
            logger.info("Flyway migration completed. Total migrations applied: ${migrationResult.migrationsExecuted}")
        } catch (e: Exception) {
            logger.error("Flyway migration failed!", e)
            throw e
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction { block() }

    suspend fun chatUserId(userUuid: String): Int? = dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.id eq userUuid }
            .singleOrNull()
            ?.get(UsersTable.userId)
    }

    suspend fun chatAdminNames(userIds: Set<Int>): Map<Int, String> = dbQuery {
        if (userIds.isEmpty()) return@dbQuery emptyMap()
        UsersTable.selectAll()
            .where { UsersTable.userId inList userIds }
            .associate { row -> row[UsersTable.userId] to (row[UsersTable.fullName] ?: "Admin") }
    }

    suspend fun ragDocument(fileId: Int, institutionSlug: String): StoredRagDocument? = dbQuery {
        RagFilesTable.join(InstitutionsTable, JoinType.INNER, RagFilesTable.institutionId, InstitutionsTable.id)
            .selectAll()
            .where { (RagFilesTable.id eq fileId) and (InstitutionsTable.slug eq institutionSlug) }
            .singleOrNull()
            ?.let { StoredRagDocument(it[RagFilesTable.filePath], it[RagFilesTable.fileName]) }
    }

    suspend fun completedRagDocumentIds(institutionSlug: String): Set<Int> = dbQuery {
        RagFilesTable.join(InstitutionsTable, JoinType.INNER, RagFilesTable.institutionId, InstitutionsTable.id)
            .selectAll()
            .where {
                (InstitutionsTable.slug eq institutionSlug) and (RagFilesTable.processingStatus eq "completed")
            }
            .map { it[RagFilesTable.id] }
            .toSet()
    }
}

data class StoredRagDocument(val filePath: String, val fileName: String)
