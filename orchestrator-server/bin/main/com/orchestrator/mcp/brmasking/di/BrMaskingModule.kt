package com.orchestrator.mcp.brmasking.di

import com.orchestrator.mcp.brmasking.BrMaskingService
import com.orchestrator.mcp.brmasking.BrMaskingServiceImpl
import com.orchestrator.mcp.brmasking.crypto.BrEncryptionService
import com.orchestrator.mcp.brmasking.model.BrMaskingConfig
import com.orchestrator.mcp.brmasking.prompt.BrIdentificationAiService
import com.orchestrator.mcp.segmentation.provider.ChatModelFactory
import dev.langchain4j.service.AiServices
import org.koin.dsl.module

/**
 * Koin DI module for BR Masking components.
 */
val brMaskingModule = module {

    single { BrMaskingConfig() }

    single {
        val config = get<BrMaskingConfig>()
        val key = config.encryptionKey.ifBlank {
            System.getenv("BR_ENCRYPTION_KEY") ?: ""
        }
        BrEncryptionService(key)
    }

    single<BrIdentificationAiService> {
        val config = get<BrMaskingConfig>()
        val factory = get<ChatModelFactory>()
        val segConfig = com.orchestrator.mcp.segmentation.config.SegmentationConfig(
            provider = config.provider,
            modelName = config.modelName,
            temperature = config.temperature,
            timeoutSeconds = config.timeoutSeconds
        )
        val chatModel = factory.create(segConfig)
        AiServices.builder(BrIdentificationAiService::class.java)
            .chatLanguageModel(chatModel)
            .build()
    }

    single<BrMaskingService> {
        BrMaskingServiceImpl(
            config = get(),
            aiService = get(),
            encryptionService = get()
        )
    }
}
