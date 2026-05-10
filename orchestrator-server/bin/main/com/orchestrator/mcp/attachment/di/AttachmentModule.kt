package com.orchestrator.mcp.attachment.di

import com.orchestrator.mcp.attachment.*
import com.orchestrator.mcp.attachment.config.AttachmentProcessorConfig
import com.orchestrator.mcp.attachment.extractors.*
import com.orchestrator.mcp.core.config.OrchestratorConfig
import kotlinx.coroutines.sync.Semaphore
import org.koin.dsl.module

/**
 * Koin DI module for AttachmentProcessor components.
 */
val attachmentModule = module {

    single<AttachmentProcessorConfig> { AttachmentProcessorConfig() }

    single {
        val extractors: Map<String, ContentExtractor> = mapOf(
            "application/pdf" to PdfTextExtractor(),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to DocxTextExtractor(),
            "text/plain" to PlainTextExtractor(),
            "text/markdown" to PlainTextExtractor()
        )
        TextExtractor(extractors)
    }

    single<AttachmentDownloader> {
        val config = get<AttachmentProcessorConfig>()
        val semaphore = Semaphore(config.maxConcurrentDownloads)
        AttachmentDownloaderImpl(get(), semaphore)
    }

    single<AttachmentProcessor> {
        val config = get<AttachmentProcessorConfig>()
        val orchestratorConfig = get<OrchestratorConfig>()
        AttachmentProcessorImpl(
            queueRepository = get(),
            downloader = get(),
            textExtractor = get(),
            embeddingService = get(),
            vectorDbClient = get(),
            collectionName = orchestratorConfig.orchestrator.vectorDb.collectionName,
            config = config
        )
    }
}
