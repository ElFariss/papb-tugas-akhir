package id.tunarasa.backend.platform.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import java.time.Instant

/**
 * Exposed Table mappings for BE-1 Platform Core tables.
 */

object RolesTable : Table("roles") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50).uniqueIndex()
    val createdAt = timestamp("created_at").default(Instant.now())

    override val primaryKey = PrimaryKey(id)
}

object InstitutionsTable : Table("institutions") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val slug = varchar("slug", 100).uniqueIndex()
    val status = varchar("status", 50).default("active")
    val createdAt = timestamp("created_at").default(Instant.now())
    val updatedAt = timestamp("updated_at").default(Instant.now())

    override val primaryKey = PrimaryKey(id)
}

object UsersTable : Table("users") {
    val id = varchar("id", 100) // Supabase Auth UUID
    val userId = integer("user_id").autoIncrement().uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val roleId = integer("role_id").references(RolesTable.id)
    val institutionId = integer("institution_id").references(InstitutionsTable.id).nullable()
    val fullName = varchar("full_name", 255).nullable()
    val status = varchar("status", 50).default("active")
    val createdAt = timestamp("created_at").default(Instant.now())
    val updatedAt = timestamp("updated_at").default(Instant.now())

    override val primaryKey = PrimaryKey(id)
}

object AdminInvitationsTable : Table("admin_invitations") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 255)
    val roleId = integer("role_id").references(RolesTable.id)
    val institutionId = integer("institution_id").references(InstitutionsTable.id).nullable()
    val token = varchar("token", 255).uniqueIndex()
    val status = varchar("status", 50).default("pending")
    val expiresAt = timestamp("expires_at")
    val createdBy = varchar("created_by", 100).references(UsersTable.id).nullable()
    val createdAt = timestamp("created_at").default(Instant.now())

    override val primaryKey = PrimaryKey(id)
}

object UserSyncLogTable : Table("user_sync_log") {
    val id = integer("id").autoIncrement()
    val userId = varchar("user_id", 100).references(UsersTable.id)
    val eventType = varchar("event_type", 50)
    val status = varchar("status", 50)
    val syncedAt = timestamp("synced_at").default(Instant.now())

    override val primaryKey = PrimaryKey(id)
}

object AppSettingsTable : Table("app_settings") {
    val key = varchar("key", 100)
    val value = text("value")
    val description = text("description").nullable()
    val updatedAt = timestamp("updated_at").default(Instant.now())

    override val primaryKey = PrimaryKey(key)
}

object InstitutionSettingsTable : Table("institution_settings") {
    val id = integer("id").autoIncrement()
    val institutionId = integer("institution_id").references(InstitutionsTable.id)
    val key = varchar("key", 100)
    val value = text("value")
    val updatedAt = timestamp("updated_at").default(Instant.now())

    override val primaryKey = PrimaryKey(id)
}

object RagFilesTable : Table("rag_files") {
    val id = integer("id").autoIncrement()
    val institutionId = integer("institution_id").references(InstitutionsTable.id)
    val fileName = varchar("file_name", 255)
    val filePath = text("file_path")
    val fileSize = long("file_size")
    val processingStatus = varchar("processing_status", 50).default("pending")
    val errorMessage = text("error_message").nullable()
    val uploadedBy = varchar("uploaded_by", 100).references(UsersTable.id).nullable()
    val createdAt = timestamp("created_at").default(Instant.now())
    val updatedAt = timestamp("updated_at").default(Instant.now())

    override val primaryKey = PrimaryKey(id)
}
