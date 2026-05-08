package com.orchestrator.mcp.crawler

import com.orchestrator.mcp.client.embedding.EmbeddingService
import com.orchestrator.mcp.client.vectordb.VectorDbClient
import com.orchestrator.mcp.client.vectordb.model.VectorPoint
import com.orchestrator.mcp.crawler.model.TicketContent
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Ingests ticket content into the vector knowledge base.
 * Generates embeddings and upserts into vector DB.
 */
interface KBIngestor {
    suspend fun ingest(content: TicketContent): Boolean
}

class KBIngestorImpl(
    private val embeddingService: EmbeddingService,
    private val vectorDbClient: VectorDbClient,
    private val collectionName: String
) : KBIngestor {

    private val logger = LoggerFactory.getLogger(KBIngestorImpl::class.java)

    override suspend fun ingest(content: TicketContent): Boolean {
        return try {
            val text = buildIngestText(content)
            if (text.isBlank()) return false

            val embedding = embeddingService.generateEmbedding(text)
            val pointId = deterministicId(content.issueKey)
            val payload = buildPayload(content)

            vectorDbClient.upsert(collectionName, listOf(
                VectorPoint(id = pointId, vector = embedding, payload = payload)
            ))
            logger.debug("Ingested {} into KB", content.issueKey)
            true
        } catch (e: Exception) {
            logger.warn("KB ingestion failed for {}: {}", content.issueKey, e.message)
            false
        }
    }

    private fun buildIngestText(content: TicketContent): String {
        val sb = StringBuilder()
        sb.appendLine("[${content.issueKey}] ${content.summary}")
        if (content.description.isNotBlank()) {
            sb.appendLine(content.description.take(MAX_CONTENT_LENGTH))
        }
        content.comments.take(5).forEach { comment ->
            sb.appendLine("Comment by ${comment.author}: ${comment.body.take(500)}")
        }
        return sb.toString().take(MAX_INGEST_LENGTH)
    }

    private fun buildPayload(content: TicketContent) = mapOf(
        "issue_key" to content.issueKey,
        "project_key" to content.projectKey,
        "title" to content.summary,
        "type" to "jira_ticket",
        "parent_key" to (content.parentKey ?: "")
    )

    private fun deterministicId(issueKey: String): String =
        UUID.nameUUIDFromBytes("jira:$issueKey".toByteArray()).toString()

    companion object {
        private const val MAX_CONTENT_LENGTH = 8000
        private const val MAX_INGEST_LENGTH = 10000
    }
}
