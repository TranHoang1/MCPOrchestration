package com.orchestrator.mcp.usermanagement.routes

import com.orchestrator.mcp.usermanagement.model.*
import com.orchestrator.mcp.usermanagement.repository.UserProjectRepository
import com.orchestrator.mcp.usermanagement.service.PermissionService
import com.orchestrator.mcp.usermanagement.service.UserService
import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * HTTP route handler for admin endpoints.
 * All endpoints require admin authentication via AdminAuthMiddleware.
 */
class AdminRoutes(
    private val userService: UserService,
    private val permissionService: PermissionService,
    private val userProjectRepo: UserProjectRepository,
    private val authMiddleware: AdminAuthMiddleware
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path.removePrefix("/admin")
        val method = exchange.requestMethod
        try {
            runBlocking { route(exchange, method, path) }
        } catch (e: UserManagementException) {
            sendError(exchange, e)
        } catch (e: Exception) {
            logger.error("Admin route error: ${e.message}", e)
            sendJson(exchange, 500, """{"error":"Internal server error","code":"INTERNAL"}""")
        }
    }

    private suspend fun route(exchange: HttpExchange, method: String, path: String) {
        val headers = exchange.requestHeaders.entries.associate { (k, v) ->
            k.lowercase() to (v.firstOrNull() ?: "")
        }
        val adminId = authMiddleware.validateAdmin(headers)
        val parts = path.split("/").filter { it.isNotBlank() }

        when {
            // GET /admin/users
            parts == listOf("users") && method == "GET" -> handleListUsers(exchange)
            // POST /admin/users
            parts == listOf("users") && method == "POST" -> handleCreateUser(exchange, adminId)
            // PUT /admin/users/{id}
            parts.size == 2 && parts[0] == "users" && method == "PUT" -> handleUpdateUser(exchange, parts[1])
            // DELETE /admin/users/{id}
            parts.size == 2 && parts[0] == "users" && method == "DELETE" -> handleDeactivateUser(exchange, parts[1])
            // GET /admin/users/{id}/projects
            parts.size == 3 && parts[0] == "users" && parts[2] == "projects" && method == "GET" ->
                handleListProjects(exchange, parts[1])
            // POST /admin/users/{id}/projects
            parts.size == 3 && parts[0] == "users" && parts[2] == "projects" && method == "POST" ->
                handleAssignProject(exchange, parts[1], adminId)
            // DELETE /admin/users/{id}/projects/{key}
            parts.size == 4 && parts[0] == "users" && parts[2] == "projects" && method == "DELETE" ->
                handleRevokeProject(exchange, parts[1], parts[3])
            // GET /admin/roles
            parts == listOf("roles") && method == "GET" -> handleGetRoles(exchange)
            // PUT /admin/roles/{role}/permissions
            parts.size == 3 && parts[0] == "roles" && parts[2] == "permissions" && method == "PUT" ->
                handleUpdatePermissions(exchange, parts[1])
            else -> sendJson(exchange, 404, """{"error":"Not found"}""")
        }
    }

    private suspend fun handleListUsers(exchange: HttpExchange) {
        val query = parseQuery(exchange)
        val filter = UserFilter(
            role = query["role"]?.let { UserRole.fromString(it) },
            active = query["active"]?.toBooleanStrictOrNull()
        )
        val users = userService.listUsers(filter)
        sendJson(exchange, 200, json.encodeToString(users))
    }

    private suspend fun handleCreateUser(exchange: HttpExchange, adminId: String) {
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        val request = json.decodeFromString<CreateUserRequest>(body)
        val user = userService.createUser(request, UUID.fromString(adminId))
        sendJson(exchange, 201, json.encodeToString(user))
    }

    private suspend fun handleUpdateUser(exchange: HttpExchange, userId: String) {
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        val request = json.decodeFromString<UpdateUserRequest>(body)
        val user = userService.updateUser(UUID.fromString(userId), request)
        sendJson(exchange, 200, json.encodeToString(user))
    }

    private suspend fun handleDeactivateUser(exchange: HttpExchange, userId: String) {
        val user = userService.deactivateUser(UUID.fromString(userId))
        sendJson(exchange, 200, json.encodeToString(user))
    }

    private suspend fun handleListProjects(exchange: HttpExchange, userId: String) {
        val projects = userProjectRepo.findByUser(UUID.fromString(userId))
        sendJson(exchange, 200, json.encodeToString(projects))
    }

    private suspend fun handleAssignProject(exchange: HttpExchange, userId: String, adminId: String) {
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        val request = json.decodeFromString<AssignProjectRequest>(body)
        validateProjectKey(request.projectKey)
        val uid = UUID.fromString(userId)
        if (userProjectRepo.exists(uid, request.projectKey)) {
            throw UserManagementException.DuplicateProjectException(request.projectKey)
        }
        val project = userProjectRepo.assign(uid, request.projectKey, UUID.fromString(adminId))
        sendJson(exchange, 201, json.encodeToString(project))
    }

    private suspend fun handleRevokeProject(exchange: HttpExchange, userId: String, projectKey: String) {
        userProjectRepo.revoke(UUID.fromString(userId), projectKey)
        sendJson(exchange, 200, """{"message":"Project assignment revoked"}""")
    }

    private suspend fun handleGetRoles(exchange: HttpExchange) {
        val matrix = permissionService.getPermissionMatrix()
        sendJson(exchange, 200, json.encodeToString(matrix))
    }

    private suspend fun handleUpdatePermissions(exchange: HttpExchange, roleName: String) {
        val role = UserRole.fromString(roleName)
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        val request = json.decodeFromString<PermissionUpdateRequest>(body)
        val updated = permissionService.updatePermissions(role, request.permissions)
        sendJson(exchange, 200, json.encodeToString(updated))
    }

    private fun validateProjectKey(key: String) {
        require(key.matches(Regex("[A-Z][A-Z0-9_]+"))) { "Invalid project key format: $key" }
    }

    private fun parseQuery(exchange: HttpExchange): Map<String, String> {
        return exchange.requestURI.query?.split("&")?.associate {
            val (k, v) = it.split("=", limit = 2)
            k to v
        } ?: emptyMap()
    }

    private fun sendJson(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendError(exchange: HttpExchange, e: UserManagementException) {
        val status = when (e) {
            is UserManagementException.DuplicateEmailException -> 409
            is UserManagementException.UserNotFoundException -> 404
            is UserManagementException.PermissionDeniedException -> 403
            is UserManagementException.LastAdminException -> 400
            is UserManagementException.TokenValidationException -> 400
            is UserManagementException.InvalidRoleException -> 400
            is UserManagementException.DuplicateApprovalException -> 409
            is UserManagementException.DocumentNotFoundException -> 404
            is UserManagementException.DuplicateProjectException -> 409
        }
        val errorBody = json.encodeToString(
            kotlinx.serialization.json.buildJsonObject {
                put("error", kotlinx.serialization.json.JsonPrimitive(e.message ?: "Unknown error"))
                put("code", kotlinx.serialization.json.JsonPrimitive(e.errorCode))
            }
        )
        sendJson(exchange, status, errorBody)
    }
}
