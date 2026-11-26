package id.tunarasa.backend.platform.auth

import org.jetbrains.exposed.v1.core.eq

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import id.tunarasa.backend.platform.db.DatabaseFactory
import id.tunarasa.backend.platform.db.InstitutionsTable
import id.tunarasa.backend.platform.db.RolesTable
import id.tunarasa.backend.platform.db.UsersTable
import io.ktor.server.auth.Principal
import io.ktor.util.logging.KtorSimpleLogger
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.jdbc.selectAll

data class UserPrincipal(
    val userId: String,
    val email: String,
    val roleName: String,
    val institutionId: Int? = null,
    val fullName: String? = null,
) : Principal

object SupabaseJwtVerifier {
    private val logger = KtorSimpleLogger("SupabaseJwtVerifier")
    private val secret = System.getenv("SUPABASE_JWT_SECRET") ?: "default_dev_supabase_jwt_secret_32chars_min"
    private val algorithm = Algorithm.HMAC256(secret)

    val verifier = JWT.require(algorithm)
        .build()

    suspend fun resolvePrincipal(token: String): UserPrincipal? {
        return try {
            val decodedJwt = verifier.verify(token)
            val userId = decodedJwt.subject ?: return null
            val email = decodedJwt.getClaim("email")?.asString() ?: ""

            // Resolve role and institution from local database
            DatabaseFactory.dbQuery {
                val userRow = UsersTable
                    .join(RolesTable, JoinType.INNER, UsersTable.roleId, RolesTable.id)
                    .selectAll()
                    .where { UsersTable.id eq userId }
                    .singleOrNull()

                if (userRow != null) {
                    UserPrincipal(
                        userId = userRow[UsersTable.id],
                        email = userRow[UsersTable.email],
                        roleName = userRow[RolesTable.name],
                        institutionId = userRow[UsersTable.institutionId],
                        fullName = userRow[UsersTable.fullName],
                    )
                } else {
                    // User not synced yet; return basic principal with default 'user' role
                    UserPrincipal(
                        userId = userId,
                        email = email,
                        roleName = "user",
                        institutionId = null,
                    )
                }
            }
        } catch (e: Exception) {
            logger.warn("JWT Verification failed: ${e.message}")
            null
        }
    }
}
