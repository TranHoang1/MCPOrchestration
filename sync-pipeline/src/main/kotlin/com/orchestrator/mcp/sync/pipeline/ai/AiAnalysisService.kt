package com.orchestrator.mcp.sync.pipeline.ai

/**
 * AI service for intelligent analysis during sync.
 * Used by FeatureDetectionDimension and future AI-powered dimensions.
 */
interface AiAnalysisService {

    /** Analyze tickets and detect feature groupings. */
    suspend fun detectFeatures(tickets: List<TicketSummary>): List<FeatureGroup>

    /** Generate enriched description for a feature. */
    suspend fun summarizeFeature(feature: FeatureGroup): String

    /** Check if AI provider is available and responsive. */
    suspend fun isHealthy(): Boolean
}
