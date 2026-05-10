package com.orchestrator.mcp.segmentation.di

import com.orchestrator.mcp.segmentation.ContentSegmentationService
import com.orchestrator.mcp.segmentation.ContentSegmentationServiceImpl
import com.orchestrator.mcp.segmentation.config.SegmentationConfig
import com.orchestrator.mcp.segmentation.prompt.SegmentationAiService
import com.orchestrator.mcp.segmentation.prompt.SegmentationPromptBuilder
import com.orchestrator.mcp.segmentation.provider.ChatModelFactory
import dev.langchain4j.service.AiServices
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin DI module for Content Segmentation components.
 */
val segmentationModule = module {

    single { SegmentationConfig() }

    single { ChatModelFactory() }

    single { SegmentationPromptBuilder() }

    single<SegmentationAiService>(named("primary")) {
        val config = get<SegmentationConfig>()
        val factory = get<ChatModelFactory>()
        val chatModel = factory.create(config)
        AiServices.builder(SegmentationAiService::class.java)
            .chatLanguageModel(chatModel)
            .build()
    }

    single<SegmentationAiService?>(named("local")) {
        val config = get<SegmentationConfig>()
        if (config.brLocalOnly && config.provider != "ollama") {
            val factory = get<ChatModelFactory>()
            val localModel = factory.createLocalModel(config)
            AiServices.builder(SegmentationAiService::class.java)
                .chatLanguageModel(localModel)
                .build()
        } else null
    }

    single<ContentSegmentationService> {
        val config = get<SegmentationConfig>()
        ContentSegmentationServiceImpl(
            config = config,
            aiService = get(named("primary")),
            localAiService = getOrNull(named("local"))
        )
    }
}
