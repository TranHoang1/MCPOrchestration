package com.orchestrator.mcp.network

import com.orchestrator.mcp.network.model.NetworkGraph

/**
 * Service for building feature network graphs from semantic links.
 */
interface NetworkService {

    /** Get N-hop neighborhood graph centered on an issue key. */
    suspend fun getNetwork(centerIssueKey: String, hops: Int = 2): NetworkGraph

    /** Get full network graph, optionally filtered by project. */
    suspend fun getFullNetwork(projectKey: String? = null): NetworkGraph
}
