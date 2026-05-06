package com.orchestrator.mcp.bridge

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.orchestrator.mcp.bridge")

/**
 * Entry point for the MCP Client Bridge.
 * Connects to a remote MCP Orchestrator via HTTP Streamable
 * and exposes tools locally via stdio MCP server.
 */
fun main(args: Array<String>) = runBlocking {
    logger.info("MCP Client Bridge v1.0.0 starting...")

    val config = BridgeConfig.load(args)
    logger.info("Connecting to orchestrator at: ${config.orchestratorUrl}")

    val bridge = BridgeServer(config)

    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking { bridge.stop() }
    })

    bridge.start()
}
