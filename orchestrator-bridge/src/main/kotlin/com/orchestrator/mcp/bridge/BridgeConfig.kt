package com.orchestrator.mcp.bridge

import org.slf4j.LoggerFactory

/**
 * Configuration for the MCP Client Bridge.
 * Loaded from CLI args and environment variables.
 */
data class BridgeConfig(
    val orchestratorUrl: String,
    val reconnectEnabled: Boolean = true,
    val maxReconnectDelayMs: Long = 15_000,
    val baseReconnectDelayMs: Long = 1_000,
    val requestTimeoutMs: Long = 30_000,
    val enableLocalStreamWrite: Boolean = true
) {
    companion object {
        private val logger = LoggerFactory.getLogger(BridgeConfig::class.java)

        /**
         * Load configuration from CLI args and environment.
         * Priority: CLI args > env vars > defaults.
         */
        fun load(args: Array<String>): BridgeConfig {
            val url = parseUrl(args)
            val timeout = parseTimeout(args)
            val noReconnect = args.contains("--no-reconnect")

            logger.info("Bridge config: url=$url, timeout=${timeout}ms, reconnect=${!noReconnect}")
            return BridgeConfig(
                orchestratorUrl = url,
                reconnectEnabled = !noReconnect,
                requestTimeoutMs = timeout,
                enableLocalStreamWrite = !args.contains("--no-local-write")
            )
        }

        private fun parseUrl(args: Array<String>): String {
            val idx = args.indexOf("--url")
            if (idx >= 0 && idx + 1 < args.size) return args[idx + 1]
            return System.getenv("ORCHESTRATOR_URL")
                ?: "http://localhost:8080"
        }

        private fun parseTimeout(args: Array<String>): Long {
            val idx = args.indexOf("--timeout")
            if (idx >= 0 && idx + 1 < args.size) {
                return args[idx + 1].toLongOrNull() ?: 30_000
            }
            return System.getenv("BRIDGE_TIMEOUT")?.toLongOrNull()
                ?: 30_000
        }
    }
}
