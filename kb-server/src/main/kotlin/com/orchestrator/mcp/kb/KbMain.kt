package com.orchestrator.mcp.kb

import com.orchestrator.mcp.kb.config.KbConfigLoader
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.orchestrator.mcp.kb.KbMain")

/**
 * Entry point for KB Server.
 * Supports --config=<path> and --transport=<stdio|http> arguments.
 */
fun main(args: Array<String>) {
    // For stdio transport: save real stdout BEFORE redirecting
    // kotlin-logging prints init message to stdout — must redirect early
    val realStdout = System.out

    val transportArg = args.firstOrNull { it.startsWith("--transport=") }?.removePrefix("--transport=")
    if (transportArg == "stdio") {
        System.setOut(System.err)
    }

    val configPath = args
        .firstOrNull { it.startsWith("--config=") }
        ?.removePrefix("--config=")

    val transport = transportArg

    val config = KbConfigLoader.load(configPath)
    val effectiveTransport = transport ?: config.kb.server.transport

    logger.info("KB Server v1.0.0 starting (transport=$effectiveTransport)")

    val app = KbApplication()
    app.start(config, effectiveTransport, realStdout)
}
