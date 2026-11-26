package id.tunarasa.backend.platform.db

import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test

class DatabaseFactoryTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            DatabaseFactory.init()
        }
    }

    @Test
    fun testDatabaseInitializationAndFlywayMigrations() {
        // Verify default roles seeded by V101 migration
        kotlinx.coroutines.runBlocking {
            DatabaseFactory.dbQuery {
                val roles = RolesTable.selectAll().toList()
                assertEquals(3, roles.size)

                val superAdminRole = roles.find { it[RolesTable.name] == "super_admin" }
                assertNotNull(superAdminRole)

                val appSettings = AppSettingsTable.selectAll().toList()
                val serviceMode = appSettings.find { it[AppSettingsTable.key] == "service_mode" }
                assertNotNull(serviceMode)
                assertEquals("full_llm_bot", serviceMode!![AppSettingsTable.value])
            }
        }
    }
}
