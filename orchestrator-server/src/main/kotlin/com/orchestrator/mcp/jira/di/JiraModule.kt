package com.orchestrator.mcp.jira.di

import com.orchestrator.mcp.jira.*
import com.orchestrator.mcp.jira.config.JiraClientConfig
import com.orchestrator.mcp.jira.ratelimit.RateLimiter
import com.orchestrator.mcp.jira.ratelimit.TokenBucketRateLimiter
import com.orchestrator.mcp.jira.retry.ExponentialBackoffRetryHandler
import com.orchestrator.mcp.jira.retry.RetryHandler
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin DI module for Jira REST Client components.
 * Register via `includes(jiraModule)` in AppModule.
 */
val jiraModule = module {

    single<JiraClientConfig> { JiraClientConfig.fromEnvironment() }

    single<RateLimiter> {
        val config = get<JiraClientConfig>()
        TokenBucketRateLimiter(ratePerSecond = config.rateLimit, burstCapacity = config.rateLimit)
    }

    single<RetryHandler> {
        val config = get<JiraClientConfig>()
        ExponentialBackoffRetryHandler(
            maxRetries = config.maxRetries,
            initialDelayMs = config.initialDelayMs,
            maxDelayMs = config.maxDelayMs
        )
    }

    single<HttpClient>(named("jiraHttpClient")) {
        val config = get<JiraClientConfig>()
        createJiraHttpClient(config)
    }

    single { JiraResponseHandler(get(), Json { ignoreUnknownKeys = true; encodeDefaults = true }) }

    single<JiraRestClient> {
        JiraRestClientImpl(
            httpClient = get(named("jiraHttpClient")),
            config = get(),
            rateLimiter = get(),
            retryHandler = get(),
            responseHandler = get()
        )
    }
}

/**
 * Create a dedicated Ktor HttpClient for Jira API communication.
 */
internal fun createJiraHttpClient(config: JiraClientConfig): HttpClient {
    return HttpClient(CIO) {
        engine {
            maxConnectionsCount = 100
            endpoint {
                connectTimeout = config.connectTimeoutMs
                socketTimeout = config.socketTimeoutMs
                keepAliveTime = 45_000
                connectAttempts = 1
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = config.timeoutMs
            connectTimeoutMillis = config.connectTimeoutMs
            socketTimeoutMillis = config.socketTimeoutMs
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
            sanitizeHeader { header -> header == "Authorization" }
        }
    }
}
