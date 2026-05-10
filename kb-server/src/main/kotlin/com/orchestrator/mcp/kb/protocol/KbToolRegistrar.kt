package com.orchestrator.mcp.kb.protocol

import io.modelcontextprotocol.kotlin.sdk.server.Server
import org.slf4j.LoggerFactory

/**
 * Registers all KB tool handlers with the MCP Server instance.
 * Each handler provides its own name, description, and input schema.
 */
object KbToolRegistrar {

    private val logger = LoggerFactory.getLogger(KbToolRegistrar::class.java)

    fun registerAll(server: Server, handlers: List<KbToolHandler>) {
        handlers.forEach { handler ->
            registerTool(server, handler)
        }
        logger.info("Registered ${handlers.size} KB tools")
    }

    private fun registerTool(server: Server, handler: KbToolHandler) {
        server.addTool(
            name = handler.toolName,
            description = handler.description,
            inputSchema = handler.inputSchema
        ) { request ->
            handler.handle(request.arguments)
        }
        logger.debug("Registered tool: ${handler.toolName}")
    }
}
