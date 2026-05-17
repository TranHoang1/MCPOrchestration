package com.orchestrator.mcp.auth.sso

import com.orchestrator.mcp.auth.sso.model.OidcMetadata
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches and caches OIDC Discovery metadata from .well-known/openid-configuration.
 * Cache entries expire after 1 hour to pick up IdP config changes.
 */
class OidcDiscoveryClient(private val httpClient: HttpClient) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cache = ConcurrentHashMap<String, CachedMetadata>()
    private val cacheTtlMs = 3600_000L // 1 hour

    /** Fetch OIDC metadata for the given issuer URL. Uses cache if fresh. */
    suspend fun discover(issuerUrl: String): OidcMetadata {
        val cached = cache[issuerUrl]
        if (cached != null && !cached.isExpired()) return cached.metadata
        return fetchAndCache(issuerUrl)
    }

    /** Force refresh metadata for a specific issuer (e.g., on config save). */
    suspend fun refresh(issuerUrl: String): OidcMetadata {
        cache.remove(issuerUrl)
        return fetchAndCache(issuerUrl)
    }

    /** Clear all cached metadata. */
    fun clearCache() = cache.clear()

    private suspend fun fetchAndCache(issuerUrl: String): OidcMetadata {
        val discoveryUrl = buildDiscoveryUrl(issuerUrl)
        logger.debug("Fetching OIDC discovery: {}", discoveryUrl)
        val metadata = fetchMetadata(discoveryUrl)
        cache[issuerUrl] = CachedMetadata(metadata, System.currentTimeMillis())
        logger.info("OIDC discovery cached for issuer: {}", issuerUrl)
        return metadata
    }

    private suspend fun fetchMetadata(discoveryUrl: String): OidcMetadata {
        val response = httpClient.get(discoveryUrl) {
            accept(ContentType.Application.Json)
        }
        if (response.status != HttpStatusCode.OK) {
            throw SsoException.DiscoveryFailedException(discoveryUrl, response.status.value)
        }
        return json.decodeFromString<OidcMetadata>(response.bodyAsText())
    }

    private fun buildDiscoveryUrl(issuerUrl: String): String {
        val base = issuerUrl.trimEnd('/')
        return "$base/.well-known/openid-configuration"
    }

    private data class CachedMetadata(val metadata: OidcMetadata, val fetchedAt: Long) {
        fun isExpired(): Boolean =
            System.currentTimeMillis() - fetchedAt > 3600_000L
    }
}
