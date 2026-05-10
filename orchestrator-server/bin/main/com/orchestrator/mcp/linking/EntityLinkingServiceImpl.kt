package com.orchestrator.mcp.linking

import com.orchestrator.mcp.client.embedding.EmbeddingService
import com.orchestrator.mcp.client.vectordb.VectorDbClient
import com.orchestrator.mcp.linking.model.EntityLink
import com.orchestrator.mcp.linking.model.LinkingConfig
import com.orchestrator.mcp.linking.model.LinkingResult
import com.orchestrator.mcp.linking.repository.EntityLinkRepository
import org.slf4j.LoggerFactory

/**
 * Implementation of semantic entity linking.
 * Uses embeddings + vector DB for similarity search, stores links in PostgreSQL.
 */
class EntityLinkingServiceImpl(
    private val embeddingService: EmbeddingService,
    private val vectorDbClient: VectorDbClient,
    private val linkRepository: EntityLinkRepository,
    private val config: LinkingConfig
) : EntityLinkingService {

    private val logger = LoggerFactory.getLogger(EntityLinkingServiceImpl::class.java)

    override suspend fun findSimilar(issueKey: String, topK: Int): List<EntityLink> {
        val existing = linkRepository.findByIssueKey(issueKey)
        if (existing.isNotEmpty()) return existing.take(topK)
        return emptyList()
    }

    override suspend fun linkEntry(issueKey: String, content: String): LinkingResult {
        val embedding = embeddingService.generateEmbedding(content)
        val threshold = config.similarityThreshold.toFloat()
        val results = vectorDbClient.search(config.collectionName, embedding, config.defaultTopK + 1, threshold)

        val links = results
            .filter { it.id != issueKey && it.score >= config.similarityThreshold.toFloat() }
            .take(config.maxLinksPerEntry)
            .map { EntityLink(issueKey, it.id, it.score.toDouble()) }

        val created = linkRepository.saveAll(links)
        logger.info("Linked entry {}: {} links created (threshold={})", issueKey, created, config.similarityThreshold)

        return LinkingResult(issueKey, created, links.size, links)
    }

    override suspend fun batchLink(entries: List<Pair<String, String>>): List<LinkingResult> =
        entries.map { (key, content) -> linkEntry(key, content) }

    override suspend fun getLinks(issueKey: String): List<EntityLink> =
        linkRepository.findByIssueKey(issueKey)
}
