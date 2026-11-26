package id.tunarasa.backend.platform.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import id.tunarasa.backend.platform.db.DatabaseFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.Date
import java.util.UUID

class AuthRoutesTest {

    companion object {
        private const val SECRET = "default_dev_supabase_jwt_secret_32chars_min"

        @BeforeClass
        @JvmStatic
        fun setup() {
            DatabaseFactory.init()
        }

        fun generateTestToken(userId: String = UUID.randomUUID().toString(), email: String = "test@tunarasa.id"): String {
            val algorithm = Algorithm.HMAC256(SECRET)
            return JWT.create()
                .withSubject(userId)
                .withClaim("email", email)
                .withExpiresAt(Date(System.currentTimeMillis() + 3600_000))
                .sign(algorithm)
        }
    }

    @Test
    fun testSupabaseJwtVerifierAndSync(): Unit = runBlocking {
        val userId = UUID.randomUUID().toString()
        val email = "user_$userId@tunarasa.id"
        val token = generateTestToken(userId, email)

        // 1. Verify token resolution before DB sync (default role 'user')
        val initialPrincipal = SupabaseJwtVerifier.resolvePrincipal(token)
        assertNotNull(initialPrincipal)
        assertEquals(userId, initialPrincipal!!.userId)
        assertEquals(email, initialPrincipal.email)
        assertEquals("user", initialPrincipal.roleName)

        // 2. Test RBAC permissions
        val superAdmin = UserPrincipal(userId = "1", email = "admin@tunarasa.id", roleName = "super_admin", institutionId = null)
        val instAdmin = UserPrincipal(userId = "2", email = "inst@tunarasa.id", roleName = "institution_admin", institutionId = 10)
        val regularUser = UserPrincipal(userId = "3", email = "user@tunarasa.id", roleName = "user", institutionId = null)

        assertTrue(superAdmin.canAccessInstitution(10))
        assertTrue(superAdmin.canAccessInstitution(99))

        assertTrue(instAdmin.canAccessInstitution(10))
        assertFalse(instAdmin.canAccessInstitution(11))

        assertFalse(regularUser.canAccessInstitution(10))
    }
}
