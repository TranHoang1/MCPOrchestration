package com.orchestrator.mcp

import com.orchestrator.mcp.core.config.OrchestratorConfig
import com.orchestrator.mcp.di.appModule
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Unified entry point for the MCP Orchestrator.
 * Detects operational mode and handles transport-specific initialization.
 */
object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val configPath = parseConfigArg(args)
        
        // Temporary Koin instance just to read the config
        val tempKoin = startKoin {
            modules(appModule(configPath))
        }.koin
        
        val config = tempKoin.get<OrchestratorConfig>()
        val transport = config.orchestrator.server.transport.lowercase()
        
        // Stop the temp koin so the real app initialization can start fresh
        org.koin.core.context.stopKoin()

        if (transport == "stdio") {
            // Run with Stdio redirection fix
            StdioMain.main(args)
        } else {
            // Run as Standalone SSE server
            realMain(args)
        }
    }
}
