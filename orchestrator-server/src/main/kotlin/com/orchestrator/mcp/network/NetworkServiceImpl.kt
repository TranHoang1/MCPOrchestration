package com.orchestrator.mcp.network

import com.orchestrator.mcp.linking.repository.EntityLinkRepository
import com.orchestrator.mcp.network.model.*
import org.slf4j.LoggerFactory

/**
 * Builds feature network graphs from entity links.
 * Performs BFS traversal to build N-hop neighborhoods.
 */
class NetworkServiceImpl(
    private val linkRepository: EntityLinkRepository,
    private val config: NetworkConfig
) : NetworkService {

    private val logger = LoggerFactory.getLogger(NetworkServiceImpl::class.java)

    override suspend fun getNetwork(centerIssueKey: String, hops: Int): NetworkGraph {
        val visited = mutableSetOf<String>()
        val edges = mutableListOf<GraphEdge>()
        val queue = ArrayDeque<Pair<String, Int>>()

        queue.add(centerIssueKey to 0)
        visited.add(centerIssueKey)

        while (queue.isNotEmpty() && visited.size < config.maxNodes) {
            val (current, depth) = queue.removeFirst()
            if (depth >= hops) continue

            val links = linkRepository.findByIssueKey(current)
            for (link in links) {
                if (link.similarityScore < config.minEdgeWeight) continue
                val neighbor = if (link.sourceIssueKey == current) link.targetIssueKey else link.sourceIssueKey
                edges.add(GraphEdge(link.sourceIssueKey, link.targetIssueKey, link.similarityScore))
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(neighbor to depth + 1)
                }
            }
        }

        val nodes = visited.map { GraphNode(id = it, label = it) }
        val uniqueEdges = edges.distinctBy { setOf(it.source, it.target) }

        logger.info("Network for {}: {} nodes, {} edges ({} hops)", centerIssueKey, nodes.size, uniqueEdges.size, hops)

        return NetworkGraph(
            nodes = nodes,
            edges = uniqueEdges,
            metadata = GraphMetadata(nodes.size, uniqueEdges.size, centerIssueKey)
        )
    }

    override suspend fun getFullNetwork(projectKey: String?): NetworkGraph {
        // For full network, we'd query all links — simplified here
        val allLinks = if (projectKey != null) {
            linkRepository.findByIssueKey(projectKey)
        } else {
            emptyList() // Would need a findAll method for production
        }

        val nodeIds = mutableSetOf<String>()
        val edges = allLinks.map { link ->
            nodeIds.add(link.sourceIssueKey)
            nodeIds.add(link.targetIssueKey)
            GraphEdge(link.sourceIssueKey, link.targetIssueKey, link.similarityScore)
        }

        val nodes = nodeIds.map { GraphNode(id = it, label = it) }
        return NetworkGraph(nodes, edges, GraphMetadata(nodes.size, edges.size, null))
    }
}
