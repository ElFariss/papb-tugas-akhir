package id.tunarasa.backend.platform.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.util.logging.KtorSimpleLogger
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object DatabaseFactory {
    private val logger = KtorSimpleLogger("DatabaseFactory")

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

        // Execute Flyway migrations
        runFlywayMigration(dataSource)

        // Connect Exposed ORM
        Database.connect(dataSource)
        logger.info("Database initialized successfully.")
    }

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
}
