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
    val enableLocalStreamWrite: Boolean = true,
    val pingIntervalMs: Long = 30_000,
    val pingTimeoutMs: Long = 5_000
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
            val pingInterval = parsePingInterval(args)
            val pingTimeout = parsePingTimeout(args)

            logger.info("Bridge config: url=$url, timeout=${timeout}ms, reconnect=${!noReconnect}, pingInterval=${pingInterval}ms")
            return BridgeConfig(
                orchestratorUrl = url,
                reconnectEnabled = !noReconnect,
                requestTimeoutMs = timeout,
                enableLocalStreamWrite = !args.contains("--no-local-write"),
                pingIntervalMs = pingInterval,
                pingTimeoutMs = pingTimeout
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
