package com.orchestrator.mcp.segmentation

import com.orchestrator.mcp.segmentation.model.SegmentationResult

/**
 * Public API for content segmentation using LLM-based classification.
 * Segments masked text into Public Metadata, Technical Content, and Business Rules.
 */
interface ContentSegmentationService {

    /**
     * Segments masked text into classified content categories.
     *
     * @param maskedText PII-masked text (max 10,000 chars)
     * @return SegmentationResult with classified content and metadata
     * @throws SegmentationException on unrecoverable errors
     */
    suspend fun segment(maskedText: String): SegmentationResult
}
