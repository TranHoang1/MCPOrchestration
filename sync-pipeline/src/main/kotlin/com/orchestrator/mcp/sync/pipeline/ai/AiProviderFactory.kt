package com.orchestrator.mcp.sync.pipeline.ai

import com.orchestrator.mcp.sync.pipeline.config.AiConfig
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Factory for creating provider-specific ChatLanguageModel instances.
 * Supports Ollama, LMStudio, OpenAI, and Azure.
 */
class AiProviderFactory {

    private val logger = LoggerFactory.getLogger(AiProviderFactory::class.java)

    /** Create a ChatLanguageModel based on provider config. */
    fun createChatModel(config: AiConfig): ChatLanguageModel {
        logger.debug("Creating chat model: provider={}, model={}", config.provider, config.model)
        return when (config.provider.lowercase()) {
            "ollama" -> createOllamaModel(config)
            "lmstudio" -> createLmStudioModel(config)
            "openai" -> createOpenAiModel(config)
            else -> throw IllegalArgumentException(
                "Unsupported AI provider: ${config.provider}. " +
                    "Supported: ollama, lmstudio, openai"
            )
        }
    }

    private fun createOllamaModel(config: AiConfig): ChatLanguageModel {
        return OllamaChatModel.builder()
            .baseUrl(config.baseUrl)
            .modelName(config.model)
            .temperature(config.temperature)
            .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
            .build()
    }

    private fun createLmStudioModel(config: AiConfig): ChatLanguageModel {
        return OpenAiChatModel.builder()
            .baseUrl(config.baseUrl)
            .apiKey("lm-studio")
            .modelName(config.model)
            .temperature(config.temperature)
            .maxTokens(config.maxTokens)
            .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
            .build()
    }

    private fun createOpenAiModel(config: AiConfig): ChatLanguageModel {
        return OpenAiChatModel.builder()
            .apiKey(config.apiKey ?: System.getenv("OPENAI_API_KEY") ?: "")
            .modelName(config.model)
            .temperature(config.temperature)
            .maxTokens(config.maxTokens)
            .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
            .build()
    }
}
