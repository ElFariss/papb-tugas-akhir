package id.tunarasa.backend.platform.admin

import org.jetbrains.exposed.v1.core.eq

import id.tunarasa.backend.platform.db.AdminInvitationsTable
import id.tunarasa.backend.platform.db.AppSettingsTable
import id.tunarasa.backend.platform.db.DatabaseFactory
import id.tunarasa.backend.platform.db.InstitutionsTable
import id.tunarasa.backend.platform.db.UsersTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class CoreManagementTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            DatabaseFactory.init()
        }
    }

    @Test
    fun testInstitutionAndSettingsManagement(): Unit = runBlocking {
        // 1. Create Institution
        val instId = DatabaseFactory.dbQuery {
            InstitutionsTable.insert {
                it[name] = "Dinas Kependudukan dan Pencatatan Sipil"
                it[slug] = "disdukcapil-kota-malang"
                it[status] = "active"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }[InstitutionsTable.id]
        }
        assertNotNull(instId)

        // 2. Insert & Query Custom App Setting
        DatabaseFactory.dbQuery {
            AppSettingsTable.insert {
                it[key] = "test_custom_config"
                it[value] = "enabled"
                it[description] = "Test Configuration"
                it[updatedAt] = Instant.now()
            }
        }

        val customVal = DatabaseFactory.dbQuery {
            AppSettingsTable
                .selectAll()
                .where { AppSettingsTable.key eq "test_custom_config" }
                .single()[AppSettingsTable.value]
        }
        assertEquals("enabled", customVal)

        // 3. Create & Accept Invitation
        val testUserId = UUID.randomUUID().toString()
        val inviteToken = UUID.randomUUID().toString()

        DatabaseFactory.dbQuery {
            UsersTable.insert {
                it[id] = testUserId
                it[email] = "admin_invite@disdukcapil.go.id"
                it[roleId] = 3 // default 'user'
                it[status] = "active"
            }

            AdminInvitationsTable.insert {
                it[email] = "admin_invite@disdukcapil.go.id"
                it[roleId] = 2 // 'institution_admin'
                it[institutionId] = instId
                it[token] = inviteToken
                it[status] = "pending"
                it[expiresAt] = Instant.now().plus(48, ChronoUnit.HOURS)
                it[createdAt] = Instant.now()
            }
        }

        // Simulate Acceptance
        DatabaseFactory.dbQuery {
            val invite = AdminInvitationsTable
                .selectAll()
                .where { AdminInvitationsTable.token eq inviteToken }
                .single()

            UsersTable.update({ UsersTable.id eq testUserId }) {
                it[roleId] = invite[AdminInvitationsTable.roleId]
                it[institutionId] = invite[AdminInvitationsTable.institutionId]
            }

            AdminInvitationsTable.update({ AdminInvitationsTable.token eq inviteToken }) {
                it[status] = "accepted"
            }
        }

        // Verify user promoted to institution_admin
        val updatedUser = DatabaseFactory.dbQuery {
            UsersTable.selectAll().where { UsersTable.id eq testUserId }.single()
        }
        assertEquals(2, updatedUser[UsersTable.roleId])
        assertEquals(instId, updatedUser[UsersTable.institutionId])
    }
}
