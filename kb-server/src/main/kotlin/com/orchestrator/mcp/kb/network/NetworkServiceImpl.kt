package com.orchestrator.mcp.kb.network

import com.orchestrator.mcp.kb.network.model.*
import com.orchestrator.mcp.kb.network.repository.EntityLinkRepository
import org.slf4j.LoggerFactory

/**
 * Builds feature network graphs from entity links.
 * Performs BFS traversal to build N-hop neighborhoods.
 */
class NetworkServiceImpl(
    private val linkRepository: EntityLinkRepository,
    private val config: NetworkConfig
) : NetworkService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun getNetwork(centerIssueKey: String, hops: Int): NetworkGraph {
        val visited = mutableSetOf<String>()
        val edges = mutableListOf<GraphEdge>()
        val queue = ArrayDeque<Pair<String, Int>>()

        queue.add(centerIssueKey to 0)
        visited.add(centerIssueKey)

        while (queue.isNotEmpty() && visited.size < config.maxNodes) {
            val (current, depth) = queue.removeFirst()
            if (depth >= hops) continue
            traverseNeighbors(current, visited, edges, queue, depth)
        }

        val nodes = visited.map { GraphNode(id = it, label = it) }
        val uniqueEdges = edges.distinctBy { setOf(it.source, it.target) }

        logger.info("Network for {}: {} nodes, {} edges ({} hops)",
            centerIssueKey, nodes.size, uniqueEdges.size, hops)

        return NetworkGraph(
            nodes = nodes,
            edges = uniqueEdges,
            metadata = GraphMetadata(nodes.size, uniqueEdges.size, centerIssueKey)
        )
    }

    override suspend fun getFullNetwork(projectKey: String?): NetworkGraph {
        val allLinks = if (projectKey != null) {
            linkRepository.findByIssueKey(projectKey)
        } else {
            emptyList()
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

    private suspend fun traverseNeighbors(
        current: String,
        visited: MutableSet<String>,
        edges: MutableList<GraphEdge>,
        queue: ArrayDeque<Pair<String, Int>>,
        depth: Int
    ) {
        val links = linkRepository.findByIssueKey(current)
        for (link in links) {
            if (link.similarityScore < config.minEdgeWeight) continue
            val neighbor = resolveNeighbor(link, current)
            edges.add(GraphEdge(link.sourceIssueKey, link.targetIssueKey, link.similarityScore))
            if (neighbor !in visited) {
                visited.add(neighbor)
                queue.add(neighbor to depth + 1)
            }
        }
    }

    private fun resolveNeighbor(link: EntityLink, current: String): String {
        return if (link.sourceIssueKey == current) link.targetIssueKey else link.sourceIssueKey
    }
}
