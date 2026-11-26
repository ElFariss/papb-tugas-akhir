package id.tunarasa.backend.platform.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext

fun ApplicationCall.userPrincipal(): UserPrincipal? = principal<UserPrincipal>()

/**
 * Validates whether the authenticated user has authorization for the given institution ID.
 * Multi-tenant isolation rule:
 * - super_admin can access any institution.
 * - institution_admin can ONLY access their assigned institution.
 * - user cannot manage institution settings.
 */
fun UserPrincipal.canAccessInstitution(targetInstitutionId: Int): Boolean {
    if (roleName == "super_admin") return true
    if (roleName == "institution_admin") return institutionId == targetInstitutionId
    return false
}

class RoleAuthorizationConfig {
    var roles: List<String> = emptyList()
}

val RoleAuthorizationPlugin = createRouteScopedPlugin(
    name = "RoleAuthorizationPlugin",
    createConfiguration = ::RoleAuthorizationConfig,
) {
    val allowedRoles = pluginConfig.roles
    on(AuthenticationChecked) { call ->
        val principal = call.userPrincipal()
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized: Authentication required"))
            return@on
        }

        if (principal.roleName !in allowedRoles) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("error" to "Forbidden: Insufficient role permissions. Required: ${allowedRoles.joinToString()}"),
            )
            return@on
        }
    }
}

/**
 * Route Extension for Role-Based Access Control (RBAC).
 */
fun Route.authorizeRoles(vararg allowedRoles: String, build: Route.() -> Unit) {
    // Each call needs its own route node: multiple guards commonly share the
    // same URL prefix, and installing a route-scoped plugin on that parent
    // causes Ktor to reject the duplicate plugin at application startup.
    val guardedRoute = createChild(object : RouteSelector() {
        override suspend fun evaluate(
            context: RoutingResolveContext,
            segmentIndex: Int,
        ): RouteSelectorEvaluation = RouteSelectorEvaluation.Transparent
    })
    guardedRoute.install(RoleAuthorizationPlugin) {
        this.roles = allowedRoles.toList()
    }
    guardedRoute.build()
}
