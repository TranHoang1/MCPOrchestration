package com.orchestrator.mcp.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.orchestrator.mcp.model.ConfigException
import org.slf4j.LoggerFactory

/**
 * Interface for configuration management with hot-reload support.
 */
interface ConfigurationManager {
    fun getConfig(): OrchestratorConfig
    fun reload(): OrchestratorConfig
    fun watchForChanges(onChange: (OrchestratorConfig) -> Unit)
}

/**
 * YAML-based configuration manager with environment variable substitution.
 */
class ConfigurationManagerImpl(
    private val configContent: String? = null,
    private val configPath: String? = null
) : ConfigurationManager {

    private val logger = LoggerFactory.getLogger(ConfigurationManagerImpl::class.java)
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false
        )
    )

    @Volatile
    private var currentConfig: OrchestratorConfig? = null
    private var onChangeCallback: ((OrchestratorConfig) -> Unit)? = null

    override fun getConfig(): OrchestratorConfig {
        return currentConfig ?: loadConfig().also { currentConfig = it }
    }

    override fun reload(): OrchestratorConfig {
        return try {
            val newConfig = loadConfig()
            val oldConfig = currentConfig
            currentConfig = newConfig
            if (oldConfig != null) {
                logger.info("Configuration reloaded successfully")
                onChangeCallback?.invoke(newConfig)
            }
            newConfig
        } catch (e: Exception) {
            logger.error("Failed to reload configuration: ${e.message}. Keeping previous config.")
            currentConfig ?: throw ConfigException("No valid configuration available", e)
        }
    }

    override fun watchForChanges(onChange: (OrchestratorConfig) -> Unit) {
        this.onChangeCallback = onChange
    }

    private fun loadConfig(): OrchestratorConfig {
        return try {
            val rawContent = configContent
                ?: configPath?.let { java.io.File(it).readText() }
                ?: loadFromClasspath()

            val resolved = resolveEnvVars(rawContent)
            yaml.decodeFromString(OrchestratorConfig.serializer(), resolved)
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException("Failed to parse configuration: ${e.message}", e)
        }
    }

    private fun loadFromClasspath(): String {
        return this::class.java.classLoader
            .getResourceAsStream("application.yml")
            ?.bufferedReader()
            ?.readText()
            ?: throw ConfigException("application.yml not found in classpath")
    }

    companion object {
        /**
         * Resolve ${ENV_VAR} references in config content.
         */
        fun resolveEnvVars(content: String): String {
            val pattern = Regex("""\$\{(\w+)}""")
            return pattern.replace(content) { match ->
                val envName = match.groupValues[1]
                System.getenv(envName) ?: ""
            }
        }
    }
}
