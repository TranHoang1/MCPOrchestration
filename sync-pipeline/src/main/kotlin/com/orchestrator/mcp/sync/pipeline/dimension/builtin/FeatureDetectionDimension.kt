package com.orchestrator.mcp.sync.pipeline.dimension.builtin

import com.orchestrator.mcp.sync.pipeline.ai.AiAnalysisService
import com.orchestrator.mcp.sync.pipeline.ai.TicketSummary
import com.orchestrator.mcp.sync.pipeline.dimension.IndexDimension
import com.orchestrator.mcp.sync.pipeline.model.*
import com.orchestrator.mcp.sync.pipeline.storage.IndexWriter
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * AI-powered feature detection dimension.
 * Groups tickets by epic (baseline) and enriches with AI analysis.
 * Only runs during post-processing phase (not per-ticket).
 */
class FeatureDetectionDimension(
    private val aiService: AiAnalysisService,
    private val indexWriter: IndexWriter
) : IndexDimension {

    private val logger = LoggerFactory.getLogger(FeatureDetectionDimension::class.java)

    override val dimensionId = "feature_grouping"
    override val displayName = "Feature Auto-Detection"
    override fun supportsVector() = true

    /** No per-ticket extraction — features are detected in post-processing. */
    override suspend fun extract(
        ticket: CrawledTicket,
        config: DimensionConfig
    ): List<IndexEntry> = emptyList()

    /** Detect features across all tickets in the project. */
    override suspend fun postProcess(
        projectKey: String,
        config: DimensionConfig
    ): List<IndexEntry> {
        val summaries = loadTicketSummaries(projectKey)
        if (summaries.isEmpty()) return emptyList()

        val features = aiService.detectFeatures(summaries)
        logger.info("Detected {} features for project {}", features.size, projectKey)

        return features.map { feature ->
            IndexEntry(
                id = deterministicId("feature:${feature.featureId}"),
                dimensionId = dimensionId,
                projectKey = projectKey,
                ticketKey = null,
                entryKey = "feature:${feature.featureId}",
                sourceRef = SourceRef(
                    type = "ai_derived",
                    path = "derived:feature/${feature.featureId}",
                    syncedAt = Clock.System.now(),
                    derivedFrom = feature.ticketKeys.map { "jira:$projectKey/$it" }
                ),
                data = buildFeatureData(feature),
                vectorText = "Feature: ${feature.featureName}. " +
                    "Tickets: ${feature.ticketKeys.joinToString(", ")}"
            )
        }
    }

    private suspend fun loadTicketSummaries(projectKey: String): List<TicketSummary> {
        return indexWriter.getTicketSummaries(projectKey).map { row ->
            TicketSummary(
                key = row.key,
                summary = row.summary,
                issueType = row.issueType,
                epicKey = row.epicKey,
                labels = row.labels,
                components = row.components
            )
        }
    }

    private fun buildFeatureData(
        feature: com.orchestrator.mcp.sync.pipeline.ai.FeatureGroup
    ): Map<String, String?> = mapOf(
        "feature_id" to feature.featureId,
        "feature_name" to feature.featureName,
        "detection_method" to feature.detectionMethod,
        "confidence" to feature.confidence.toString(),
        "ticket_keys" to feature.ticketKeys.joinToString(","),
        "epic_key" to feature.epicKey,
        "description" to feature.description
    )
}
