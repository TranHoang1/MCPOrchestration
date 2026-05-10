package com.orchestrator.mcp.kb

import com.orchestrator.mcp.kb.config.KbConfigLoader
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.orchestrator.mcp.kb.KbMain")

/**
 * Entry point for KB Server.
 * Supports --config=<path> and --transport=<stdio|http> arguments.
 */
fun main(args: Array<String>) {
    val configPath = args
        .firstOrNull { it.startsWith("--config=") }
        ?.removePrefix("--config=")

    val transport = args
        .firstOrNull { it.startsWith("--transport=") }
        ?.removePrefix("--transport=")

    val config = KbConfigLoader.load(configPath)
    val effectiveTransport = transport ?: config.kb.server.transport

    logger.info("KB Server v1.0.0 starting (transport=$effectiveTransport)")

    val app = KbApplication()
    app.start(config, effectiveTransport)
}
