package com.orchestrator.mcp.kb.queue.handler

import com.orchestrator.mcp.kb.masking.PiiMaskingEngine
import com.orchestrator.mcp.kb.queue.TaskHandler
import com.orchestrator.mcp.kb.queue.model.QueueTask
import com.orchestrator.mcp.kb.store.model.BrSensitivityLevel
import com.orchestrator.mcp.kb.store.model.KbEntry
import com.orchestrator.mcp.kb.store.model.MappingType
import com.orchestrator.mcp.kb.store.model.PiiMapping
import com.orchestrator.mcp.kb.store.repository.KbEntryRepository
import com.orchestrator.mcp.kb.store.repository.PiiMappingRepository
import com.orchestrator.mcp.kb.store.vector.KbVectorClient
import com.orchestrator.mcp.kb.store.vector.KbVectorEntry
import com.orchestrator.mcp.client.embedding.EmbeddingService
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.security.MessageDigest

/**
 * Handles "ingest" tasks from the queue.
 * Pipeline: PII mask → store entry → generate embedding → index vector.
 */
class IngestTaskHandler(
    private val piiMaskingEngine: PiiMaskingEngine,
    private val entryRepository: KbEntryRepository,
    private val piiMappingRepository: PiiMappingRepository,
    private val embeddingService: EmbeddingService,
    private val vectorClient: KbVectorClient
) : TaskHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun taskType(): String = "ingest"

    override suspend fun handle(task: QueueTask) {
        val payload = task.payload
        val title = payload["title"]?.jsonPrimitive?.content ?: "Untitled"
        val content = payload["content"]?.jsonPrimitive?.content ?: ""
        val issueKey = payload["issue_key"]?.jsonPrimitive?.content ?: extractIssueKey(title)
        val tags = payload["tags"]?.jsonPrimitive?.content

        // Step 1: PII masking
        val maskResult = piiMaskingEngine.mask(content)

        // Step 2: Store PII mappings
        storePiiMappings(issueKey, maskResult.mappings.map { detection ->
            PiiMapping(
                issueKey = issueKey,
                placeholder = detection.token,
                originalValue = detection.originalValue,
                mappingType = MappingType.valueOf(detection.piiType.label)
            )
        })

        // Step 3: Store KB entry with masked content
        val contentHash = computeHash(maskResult.maskedContent)
        val entry = KbEntry(
            issueKey = issueKey,
            projectKey = issueKey.substringBefore("-").ifEmpty { "UNKNOWN" },
            publicContent = buildPublicContent(title, maskResult.maskedContent, tags),
            technicalContent = null,
            businessRules = null,
            maskedFull = maskResult.maskedContent,
            brSensitivityLevel = BrSensitivityLevel.INTERNAL,
            contentHash = contentHash
        )
        entryRepository.upsert(entry)

        // Step 4: Generate embedding and index
        indexInVectorDb(entry, maskResult.maskedContent)

        logger.info("Ingest task completed for {}", issueKey)
    }

    private suspend fun storePiiMappings(issueKey: String, mappings: List<PiiMapping>) {
        if (mappings.isEmpty()) return
        piiMappingRepository.replaceForIssueKey(issueKey, mappings)
        logger.debug("Stored {} PII mappings for {}", mappings.size, issueKey)
    }

    private suspend fun indexInVectorDb(entry: KbEntry, content: String) {
        try {
            val textToEmbed = "${entry.issueKey} ${entry.publicContent ?: content}"
            val embedding = embeddingService.generateEmbedding(textToEmbed)
            val vectorEntry = KbVectorEntry(
                issueKey = entry.issueKey,
                projectKey = entry.projectKey,
                contentHash = entry.contentHash,
                embedding = embedding,
                searchText = textToEmbed.take(500)
            )
            vectorClient.upsert(vectorEntry)
        } catch (e: Exception) {
            logger.warn("Vector indexing failed for {}: {}", entry.issueKey, e.message)
        }
    }

    private fun buildPublicContent(title: String, content: String, tags: String?): String =
        buildString {
            append("# $title\n\n")
            tags?.let { append("Tags: $it\n\n") }
            append(content)
        }

    private fun extractIssueKey(title: String): String {
        val pattern = Regex("[A-Z]+-\\d+")
        return pattern.find(title)?.value ?: "KB-${System.currentTimeMillis()}"
    }

    private fun computeHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
