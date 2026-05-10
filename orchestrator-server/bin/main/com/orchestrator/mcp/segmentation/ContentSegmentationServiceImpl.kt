package com.orchestrator.mcp.segmentation

import com.orchestrator.mcp.segmentation.config.SegmentationConfig
import com.orchestrator.mcp.segmentation.model.*
import com.orchestrator.mcp.segmentation.prompt.SegmentationAiService
import com.orchestrator.mcp.segmentation.prompt.SegmentationPromptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Implementation of ContentSegmentationService using LangChain4j.
 * Orchestrates LLM calls, response parsing, and BR local-only enforcement.
 */
class ContentSegmentationServiceImpl(
    private val config: SegmentationConfig,
    private val aiService: SegmentationAiService,
    private val localAiService: SegmentationAiService? = null
) : ContentSegmentationService {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun segment(maskedText: String): SegmentationResult {
        validateInput(maskedText)
        val text = truncateIfNeeded(maskedText)
        val startTime = System.currentTimeMillis()

        val rawResponse = callLlm(text)
        val result = parseResponse(rawResponse)
        val finalResult = enforceBrLocalOnly(result)

        val provider = if (finalResult.provider.isNotBlank()) finalResult.provider else config.provider
        return finalResult.copy(
            processingTimeMs = System.currentTimeMillis() - startTime,
            provider = provider
        )
    }

    private fun validateInput(text: String) {
        if (text.isBlank()) {
            throw SegmentationException.InvalidInputException("maskedText must not be blank")
        }
    }

    private fun truncateIfNeeded(text: String): String {
        return if (text.length > SegmentationPromptBuilder.MAX_INPUT_LENGTH) {
            logger.warn("Input truncated from ${text.length} to ${SegmentationPromptBuilder.MAX_INPUT_LENGTH} chars")
            text.take(SegmentationPromptBuilder.MAX_INPUT_LENGTH)
        } else text
    }

    private suspend fun callLlm(text: String): String = withContext(Dispatchers.IO) {
        try {
            withTimeout(config.timeoutSeconds * 1000L) {
                aiService.classify(text)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw SegmentationException.LlmTimeoutException(config.timeoutSeconds * 1000L)
        } catch (e: Exception) {
            throw SegmentationException.ProviderUnavailableException(config.provider, e)
        }
    }

    private fun parseResponse(raw: String): SegmentationResult {
        return try {
            val cleaned = extractJsonBlock(raw)
            json.decodeFromString<SegmentationResult>(cleaned)
        } catch (e: Exception) {
            throw SegmentationException.InvalidLlmResponseException(raw, e)
        }
    }

    private fun extractJsonBlock(raw: String): String {
        val jsonStart = raw.indexOf('{')
        val jsonEnd = raw.lastIndexOf('}')
        if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) return raw
        return raw.substring(jsonStart, jsonEnd + 1)
    }

    private suspend fun enforceBrLocalOnly(result: SegmentationResult): SegmentationResult {
        if (!config.brLocalOnly) return result
        if (result.businessRules.isBlank()) return result
        if (config.provider == "ollama") return result

        val local = localAiService ?: return result.copy(degraded = true)
        return try {
            val localRaw = withContext(Dispatchers.IO) { local.classify(result.businessRules) }
            val localResult = parseResponse(localRaw)
            result.copy(
                businessRules = localResult.businessRules,
                brSensitivityLevel = localResult.brSensitivityLevel,
                provider = "${config.provider}+ollama"
            )
        } catch (e: Exception) {
            logger.warn("BR local-only enforcement failed: ${e.message}")
            result.copy(degraded = true)
        }
    }
}
