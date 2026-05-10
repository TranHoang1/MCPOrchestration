package com.orchestrator.mcp.segmentation.model

/**
 * Sealed exception hierarchy for segmentation errors.
 */
sealed class SegmentationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    class InvalidInputException(message: String) :
        SegmentationException(message)

    class LlmTimeoutException(timeoutMs: Long) :
        SegmentationException("LLM timeout after ${timeoutMs}ms")

    class InvalidLlmResponseException(raw: String, cause: Throwable? = null) :
        SegmentationException("Invalid LLM response: ${raw.take(100)}", cause)

    class ProviderUnavailableException(provider: String, cause: Throwable? = null) :
        SegmentationException("Provider unavailable: $provider", cause)
}
