package com.orchestrator.mcp.kb.protocol

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import org.slf4j.LoggerFactory

/**
 * Factory that creates an MCP SDK Server instance
 * with all 11 KB tools registered.
 */
class KbMcpServerFactory(
    private val handlers: List<KbToolHandler>
) {
    private val logger = LoggerFactory.getLogger(KbMcpServerFactory::class.java)

    fun create(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "kb-server",
                version = "1.0.0"
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false)
                )
            )
        )

        KbToolRegistrar.registerAll(server, handlers)
        logger.info("KB MCP Server created with ${handlers.size} tools registered")
        return server
    }
}
