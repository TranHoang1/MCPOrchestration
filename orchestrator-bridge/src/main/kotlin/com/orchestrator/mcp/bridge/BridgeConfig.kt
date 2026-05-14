package com.orchestrator.mcp.bridge

import org.slf4j.LoggerFactory

/**
 * Configuration for the MCP Client Bridge.
 * Loaded from CLI args and environment variables.
 */
data class BridgeConfig(
    val orchestratorUrls: List<String>,
    val orchestratorUrl: String = orchestratorUrls.first(),
    val reconnectEnabled: Boolean = true,
    val maxReconnectDelayMs: Long = 15_000,
    val baseReconnectDelayMs: Long = 1_000,
    val requestTimeoutMs: Long = 30_000,
    val connectionTimeoutMs: Long = 5_000,
    val maxRetryBeforeRotate: Int = 3,
    val enableLocalStreamWrite: Boolean = true,
    val pingIntervalMs: Long = 30_000,
    val pingTimeoutMs: Long = 5_000,
    val token: String? = null
) {
    companion object {
        private val logger = LoggerFactory.getLogger(BridgeConfig::class.java)
        private const val MAX_URLS = 10

        /**
         * Load configuration from CLI args and environment.
         * Priority: CLI args > env vars > defaults.
         */
        fun load(args: Array<String>): BridgeConfig {
            val urls = parseUrls(args)
            val timeout = parseTimeout(args)
            val noReconnect = args.contains("--no-reconnect")
            val pingInterval = parsePingInterval(args)
            val pingTimeout = parsePingTimeout(args)
            val token = parseToken(args)

            if (urls.size > 1) {
                logger.info("Bridge config: ${urls.size} URLs (failover), auth=${if (token != null) "JWT" else "NONE"}")
            } else {
                logger.info("Bridge config: url=${urls.first()}, auth=${if (token != null) "JWT" else "NONE"}")
            }
            return BridgeConfig(
                orchestratorUrls = urls,
                reconnectEnabled = !noReconnect,
                requestTimeoutMs = timeout,
                enableLocalStreamWrite = !args.contains("--no-local-write"),
                pingIntervalMs = pingInterval,
                pingTimeoutMs = pingTimeout,
                token = token
            )
        }

        private fun parseToken(args: Array<String>): String? {
            val idx = args.indexOf("--token")
            if (idx >= 0 && idx + 1 < args.size) return args[idx + 1]
            return System.getenv("MCP_BRIDGE_TOKEN")
        }

        private fun parseUrls(args: Array<String>): List<String> {
            val idx = args.indexOf("--url")
            val raw = when {
                idx >= 0 && idx + 1 < args.size -> args[idx + 1]
                System.getenv("ORCHESTRATOR_URLS") != null -> System.getenv("ORCHESTRATOR_URLS")
                System.getenv("ORCHESTRATOR_URL") != null -> System.getenv("ORCHESTRATOR_URL")
                else -> "http://localhost:8080"
            }
            val urls = raw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filter { it.startsWith("http://") || it.startsWith("https://") }

            if (urls.isEmpty()) {
                logger.error("No valid URLs configured")
                System.exit(1)
            }
            if (urls.size > MAX_URLS) {
                logger.warn("URL list truncated to $MAX_URLS entries")
                return urls.take(MAX_URLS)
            }
            return urls
        }

        private fun parseTimeout(args: Array<String>): Long {
            val idx = args.indexOf("--timeout")
            if (idx >= 0 && idx + 1 < args.size) {
                return args[idx + 1].toLongOrNull() ?: 30_000
            }
            return System.getenv("BRIDGE_TIMEOUT")?.toLongOrNull()
                ?: 30_000
        }

        private fun parsePingInterval(args: Array<String>): Long {
            val idx = args.indexOf("--ping-interval")
            if (idx >= 0 && idx + 1 < args.size) {
                val v = args[idx + 1].toLongOrNull() ?: return 30_000
                if (v == 0L || v >= 5000) return v
            }
            val env = System.getenv("BRIDGE_PING_INTERVAL")?.toLongOrNull()
            if (env != null && (env == 0L || env >= 5000)) return env
            return 30_000
        }

        private fun parsePingTimeout(args: Array<String>): Long {
            val idx = args.indexOf("--ping-timeout")
            if (idx >= 0 && idx + 1 < args.size) {
                val v = args[idx + 1].toLongOrNull() ?: return 5_000
                if (v >= 1000) return v
            }
            val env = System.getenv("BRIDGE_PING_TIMEOUT")?.toLongOrNull()
            if (env != null && env >= 1000) return env
            return 5_000
        }
    }
}
