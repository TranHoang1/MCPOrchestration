package com.orchestrator.mcp.dashboard

import com.orchestrator.mcp.dashboard.model.SyncStartRequest
import com.orchestrator.mcp.dashboard.model.SyncStopRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.getKoin

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Ktor route definitions for the sync dashboard.
 * Provides REST endpoints and SSE for live updates.
 */
fun Application.configureDashboardRoutes() {
    val dashboardService = getKoin().get<SyncDashboardService>()
    val eventBus = getKoin().get<SyncEventBus>()

    routing {
        route("/sync") {
            get("/status") {
                handleGetAllStatuses(call, dashboardService)
            }
            get("/status/{projectKey}") {
                handleGetProjectStatus(call, dashboardService)
            }
            post("/start") {
                handleStartSync(call, dashboardService)
            }
            post("/stop") {
                handleStopSync(call, dashboardService)
            }
            sse("/live") {
                eventBus.events.collect { event ->
                    val data = json.encodeToString(event)
                    send(io.ktor.sse.ServerSentEvent(data = data, event = event.type))
                }
            }
        }
    }
}

private suspend fun handleGetAllStatuses(
    call: ApplicationCall,
    service: SyncDashboardService
) {
    val keys = call.request.queryParameters["projects"]
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    if (keys.isEmpty()) {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Query param 'projects' required"))
        return
    }
    val response = service.getAllStatuses(keys)
    call.respond(response)
}

private suspend fun handleGetProjectStatus(
    call: ApplicationCall,
    service: SyncDashboardService
) {
    val projectKey = call.parameters["projectKey"]
    if (projectKey.isNullOrBlank()) {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "projectKey required"))
        return
    }
    val status = service.getProjectStatus(projectKey)
    if (status != null) {
        call.respond(status)
    } else {
        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Project not found"))
    }
}

private suspend fun handleStartSync(
    call: ApplicationCall,
    service: SyncDashboardService
) {
    val request = call.receive<SyncStartRequest>()
    val response = service.startSync(request.projectKey, request.fullSync)
    val statusCode = if (response.success) HttpStatusCode.OK else HttpStatusCode.InternalServerError
    call.respond(statusCode, response)
}

private suspend fun handleStopSync(
    call: ApplicationCall,
    service: SyncDashboardService
) {
    val request = call.receive<SyncStopRequest>()
    val response = service.stopSync(request.projectKey)
    call.respond(response)
}
