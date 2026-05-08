package com.orchestrator.mcp.crawler.di

import com.orchestrator.mcp.core.config.OrchestratorConfig
import com.orchestrator.mcp.crawler.*
import com.orchestrator.mcp.crawler.config.CrawlerConfig
import org.koin.dsl.module

/**
 * Koin DI module for TicketCrawler components.
 */
val crawlerModule = module {

    single<CrawlerConfig> { CrawlerConfig() }

    single { AdfParser() }

    single { ContentHasher() }

    single<ContentFetcher> {
        ContentFetcherImpl(get(), get(), get<CrawlerConfig>().maxComments)
    }

    single { GraphBuilder(get()) }

    single { AttachmentQueuer(get()) }

    single<KBIngestor> {
        val config = get<OrchestratorConfig>()
        KBIngestorImpl(get(), get(), config.orchestrator.vectorDb.collectionName)
    }

    single<TicketCrawler> {
        TicketCrawlerImpl(get(), get(), get(), get(), get(), get(), get())
    }
}
