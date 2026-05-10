package com.orchestrator.mcp.brmasking

import com.orchestrator.mcp.brmasking.crypto.BrEncryptionService
import com.orchestrator.mcp.brmasking.model.*
import com.orchestrator.mcp.brmasking.prompt.BrIdentificationAiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Implementation of BrMaskingService using LLM for BR identification
 * and AES-256-GCM for encryption.
 */
class BrMaskingServiceImpl(
    private val config: BrMaskingConfig,
    private val aiService: BrIdentificationAiService,
    private val encryptionService: BrEncryptionService
) : BrMaskingService {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun maskBusinessRules(brContent: String): BrMaskingResult {
        if (brContent.isBlank()) return emptyResult(brContent)
        val startTime = System.currentTimeMillis()

        val identifiedRules = identifyRules(brContent)
        val (maskedText, placeholders) = buildMaskedOutput(brContent, identifiedRules)

        return BrMaskingResult(
            maskedBr = maskedText,
            brPlaceholders = placeholders,
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }

    override fun unmask(placeholder: BrPlaceholder): String {
        return encryptionService.decrypt(placeholder.encryptedOriginal, placeholder.iv)
    }

    private suspend fun identifyRules(content: String): List<IdentifiedBr> {
        return try {
            val raw = callLlm(content)
            parseIdentifiedRules(raw)
        } catch (e: Exception) {
            logger.warn("BR identification failed: ${e.message}. Using fail-safe.")
            listOf(IdentifiedBr(content, "UNKNOWN", "Unclassified business rule"))
        }
    }

    private suspend fun callLlm(content: String): String = withContext(Dispatchers.IO) {
        withTimeout(config.timeoutSeconds * 1000L) {
            aiService.identify(content)
        }
    }

    private fun parseIdentifiedRules(raw: String): List<IdentifiedBr> {
        val cleaned = extractJsonArray(raw)
        return json.decodeFromString<List<IdentifiedBr>>(cleaned)
    }

    private fun buildMaskedOutput(
        original: String,
        rules: List<IdentifiedBr>
    ): Pair<String, List<BrPlaceholder>> {
        val counters = mutableMapOf<BrCategory, Int>()
        val placeholders = mutableListOf<BrPlaceholder>()
        var masked = original

        for (rule in rules) {
            val category = parseCategory(rule.category)
            val count = counters.getOrDefault(category, 0) + 1
            counters[category] = count

            val placeholderId = "[BR_${category.name}_${count.toString().padStart(2, '0')}]"
            val (encrypted, iv) = encryptionService.encrypt(rule.text)

            placeholders.add(BrPlaceholder(placeholderId, category, encrypted, iv, rule.summary))
            masked = masked.replace(rule.text, placeholderId)
        }
        return masked to placeholders
    }

    private fun parseCategory(raw: String): BrCategory {
        return try { BrCategory.valueOf(raw.uppercase()) } catch (_: Exception) { BrCategory.UNKNOWN }
    }

    private fun extractJsonArray(raw: String): String {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start == -1 || end == -1) return "[]"
        return raw.substring(start, end + 1)
    }

    private fun emptyResult(content: String) = BrMaskingResult(content, emptyList())

    @Serializable
    private data class IdentifiedBr(val text: String, val category: String, val summary: String)
}
