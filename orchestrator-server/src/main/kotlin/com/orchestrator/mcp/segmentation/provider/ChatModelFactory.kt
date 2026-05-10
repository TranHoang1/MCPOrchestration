package com.orchestrator.mcp.segmentation.provider

import com.orchestrator.mcp.segmentation.config.SegmentationConfig
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.azure.AzureOpenAiChatModel
import java.time.Duration

/**
 * Factory for creating provider-specific ChatLanguageModel instances.
 */
class ChatModelFactory {

    fun create(config: SegmentationConfig): ChatLanguageModel {
        return when (config.provider.lowercase()) {
            "openai" -> createOpenAiModel(config)
            "ollama" -> createOllamaModel(config)
            "azure" -> createAzureModel(config)
            else -> throw IllegalArgumentException(
                "Unsupported provider: ${config.provider}. Supported: openai, ollama, azure"
            )
        }
    }

    fun createLocalModel(config: SegmentationConfig): ChatLanguageModel {
        return createOllamaModel(config)
    }

    private fun createOpenAiModel(config: SegmentationConfig): ChatLanguageModel {
        return OpenAiChatModel.builder()
            .apiKey(config.apiKey ?: System.getenv("OPENAI_API_KEY") ?: "")
            .modelName(config.modelName)
            .temperature(config.temperature)
            .maxTokens(config.maxTokens)
            .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
            .apply { config.baseUrl?.let { baseUrl(it) } }
            .build()
    }

    private fun createOllamaModel(config: SegmentationConfig): ChatLanguageModel {
        return OllamaChatModel.builder()
            .baseUrl(config.ollamaUrl)
            .modelName(config.ollamaModel)
            .temperature(config.temperature)
            .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
            .build()
    }

    private fun createAzureModel(config: SegmentationConfig): ChatLanguageModel {
        return AzureOpenAiChatModel.builder()
            .apiKey(config.apiKey ?: System.getenv("AZURE_OPENAI_KEY") ?: "")
            .deploymentName(config.modelName)
            .temperature(config.temperature)
            .maxTokens(config.maxTokens)
            .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
            .apply { config.baseUrl?.let { endpoint(it) } }
            .build()
    }
}
