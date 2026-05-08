package com.orchestrator.mcp.graph

import com.orchestrator.mcp.graph.model.ViewMode
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.java.KoinJavaComponent.getKoin

/**
 * Ktor route definitions for the 3D graph visualization API.
 */
fun Application.configureGraphRoutes() {
    val graphService = getKoin().get<GraphService>()

    routing {
        route("/sync/graph") {
            get("/{projectKey}") {
                handleProjectGraph(call, graphService)
            }
            get("/{projectKey}/{issueKey}") {
                handleSubgraph(call, graphService)
            }
        }
    }
}

private suspend fun handleProjectGraph(
    call: ApplicationCall,
    service: GraphService
) {
    val projectKey = call.parameters["projectKey"]
    if (projectKey.isNullOrBlank()) {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "projectKey required"))
        return
    }

    val view = ViewMode.fromString(call.request.queryParameters["view"])
    val response = service.getProjectGraph(projectKey, view)
    call.respond(response)
}

private suspend fun handleSubgraph(
    call: ApplicationCall,
    service: GraphService
) {
    val projectKey = call.parameters["projectKey"]
    val issueKey = call.parameters["issueKey"]
    if (projectKey.isNullOrBlank() || issueKey.isNullOrBlank()) {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "projectKey and issueKey required"))
        return
    }

    val view = ViewMode.fromString(call.request.queryParameters["view"])
    val depth = call.request.queryParameters["depth"]?.toIntOrNull()?.coerceIn(1, 5) ?: 2
    val response = service.getSubgraph(projectKey, issueKey, depth, view)
    call.respond(response)
}
