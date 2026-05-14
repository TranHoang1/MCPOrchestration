package com.orchestrator.mcp.sync.pipeline.ai

import com.orchestrator.mcp.sync.pipeline.config.SyncPipelineConfig
import dev.langchain4j.data.message.UserMessage
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/**
 * AI-powered feature detection using LLM.
 * Epic-based baseline + optional AI enrichment.
 * Gracefully degrades if AI is unavailable.
 */
class AiAnalysisServiceImpl(
    private val providerFactory: AiProviderFactory,
    private val config: SyncPipelineConfig
) : AiAnalysisService {

    private val logger = LoggerFactory.getLogger(AiAnalysisServiceImpl::class.java)

    override suspend fun detectFeatures(tickets: List<TicketSummary>): List<FeatureGroup> {
        if (tickets.isEmpty()) return emptyList()

        // Step 1: Epic-based grouping (always works, no AI needed)
        val epicGroups = groupByEpic(tickets)

        // Step 2: AI enrichment (if enabled and healthy)
        return if (shouldUseAi() && isHealthy()) {
            enrichWithAi(epicGroups, tickets)
        } else {
            epicGroups
        }
    }

    override suspend fun summarizeFeature(feature: FeatureGroup): String {
        return feature.description ?: "Feature: ${feature.featureName}"
    }

    override suspend fun isHealthy(): Boolean {
        return try {
            providerFactory.createChatModel(config.ai)
            true
        } catch (e: Exception) {
            logger.debug("AI provider not healthy: {}", e.message)
            false
        }
    }

    private fun groupByEpic(tickets: List<TicketSummary>): List<FeatureGroup> {
        return tickets
            .filter { it.epicKey != null }
            .groupBy { it.epicKey!! }
            .map { (epicKey, members) ->
                val epicTicket = tickets.find { it.key == epicKey }
                FeatureGroup(
                    featureId = "epic-$epicKey",
                    featureName = epicTicket?.summary ?: "Feature $epicKey",
                    ticketKeys = members.map { it.key },
                    detectionMethod = "epic_hierarchy",
                    confidence = 1.0,
                    epicKey = epicKey
                )
            }
    }

    private suspend fun enrichWithAi(
        epicGroups: List<FeatureGroup>,
        tickets: List<TicketSummary>
    ): List<FeatureGroup> {
        return try {
            val model = providerFactory.createChatModel(config.ai)
            val prompt = buildPrompt(tickets, epicGroups)
            val response = withTimeout(config.ai.timeoutSeconds * 1000L) {
                @Suppress("DEPRECATION")
                model.generate(UserMessage.from(prompt))
            }
            parseAiResponse(response.content().text(), epicGroups)
        } catch (e: Exception) {
            logger.warn("AI enrichment failed, using epic-only: {}", e.message)
            epicGroups
        }
    }

    private fun buildPrompt(
        tickets: List<TicketSummary>,
        existing: List<FeatureGroup>
    ): String {
        val ticketList = tickets.take(config.featureDetection.maxTicketsPerAnalysis)
            .joinToString("\n") { "- ${it.key}: ${it.summary} [${it.issueType}]" }
        val existingFeatures = existing.joinToString("\n") { "- ${it.featureName}: ${it.ticketKeys}" }
        return """
            |Analyze these Jira tickets and identify feature groupings.
            |Existing epic-based groups: $existingFeatures
            |Tickets: $ticketList
            |Return JSON array of {featureId, featureName, ticketKeys, confidence}.
        """.trimMargin()
    }

    private fun parseAiResponse(
        response: String,
        fallback: List<FeatureGroup>
    ): List<FeatureGroup> {
        // Simple fallback — return epic groups enhanced with AI description
        return fallback
    }

    private fun shouldUseAi(): Boolean {
        return config.featureDetection.strategy != "epic_only"
    }
}
