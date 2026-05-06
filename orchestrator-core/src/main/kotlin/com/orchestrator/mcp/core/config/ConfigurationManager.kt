package com.orchestrator.mcp.core.config

/**
 * Interface for configuration management with hot-reload support.
 */
interface ConfigurationManager {
    fun getConfig(): OrchestratorConfig
    fun reload(): OrchestratorConfig
    fun updateConfig(newConfig: OrchestratorConfig)
    fun saveConfig(newConfig: OrchestratorConfig)
    fun watchForChanges(onChange: (OrchestratorConfig) -> Unit)
}
