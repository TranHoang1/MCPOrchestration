package com.orchestrator.mcp.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.orchestrator.mcp.model.ConfigException
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Interface for configuration management with hot-reload support.
 */
interface ConfigurationManager {
    fun getConfig(): OrchestratorConfig
    fun reload(): OrchestratorConfig
    fun watchForChanges(onChange: (OrchestratorConfig) -> Unit)
}

/**
 * Configuration manager with multi-source loading:
 * 1. Classpath YAML (bundled in JAR) — lowest priority
 * 2. External YAML (working directory) — overrides classpath
 * 3. External JSON (config.json / application.json) — merges upstream_servers
 * 4. Environment variables — highest priority (resolves ${VAR} refs)
 */
class ConfigurationManagerImpl(
    private val configContent: String? = null,
    private val configPath: String? = null,
    private val workingDirectory: File = File(".")
) : ConfigurationManager {

    private val logger = LoggerFactory.getLogger(
        ConfigurationManagerImpl::class.java
    )
    private val yaml = Yaml(
        configuration = YamlConfiguration(strictMode = false)
    )

    @Volatile
    private var currentConfig: OrchestratorConfig? = null
    private var onChangeCallback: ((OrchestratorConfig) -> Unit)? = null

    override fun getConfig(): OrchestratorConfig {
        return currentConfig
            ?: loadConfig().also { currentConfig = it }
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
            logger.error(
                "Failed to reload configuration: " +
                    "${e.message}. Keeping previous config."
            )
            currentConfig
                ?: throw ConfigException(
                    "No valid configuration available", e
                )
        }
    }

    override fun watchForChanges(
        onChange: (OrchestratorConfig) -> Unit
    ) {
        this.onChangeCallback = onChange
    }

    private fun loadConfig(): OrchestratorConfig {
        return try {
            val yamlConfig = loadYamlConfig()
            val jsonServers = loadJsonServers()
            val cliServers = loadCliConfigServers()
            mergeAllServers(yamlConfig, jsonServers, cliServers)
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException(
                "Failed to parse configuration: ${e.message}",
                e
            )
        }
    }

    /**
     * Load YAML config with priority:
     * configContent > configPath > external YAML > classpath
     */
    private fun loadYamlConfig(): OrchestratorConfig {
        val rawContent = configContent
            ?: configPath?.let { File(it).readText() }
            ?: loadYamlWithExternalFallback()

        val resolved = resolveEnvVars(rawContent)
        return yaml.decodeFromString(
            OrchestratorConfig.serializer(), resolved
        )
    }

    /**
     * Try external YAML first, fall back to classpath.
     */
    private fun loadYamlWithExternalFallback(): String {
        val external = ExternalConfigScanner
            .findExternalYaml(workingDirectory)
        if (external != null) {
            logger.info(
                "Using external YAML configuration"
            )
            return external
        }
        return loadFromClasspath()
    }

    /**
     * Load upstream servers from external JSON file.
     * Returns empty list if no JSON config found.
     */
    private fun loadJsonServers(): List<UpstreamServerConfig> {
        if (configContent != null) return emptyList()

        val jsonContent = ExternalConfigScanner
            .findExternalJson(workingDirectory)
            ?: return emptyList()

        logger.info(
            "Loading upstream servers from JSON config"
        )
        return JsonConfigLoader
            .parseUpstreamServers(jsonContent)
    }

    /**
     * Load upstream servers from --config CLI argument.
     * Supports mcpServers format (MCP setting format).
     * Returns empty list if no --config provided or file
     * not found.
     */
    private fun loadCliConfigServers(): List<UpstreamServerConfig> {
        val path = configPath ?: return emptyList()
        val file = File(path).let {
            if (it.isAbsolute) it
            else File(workingDirectory, path)
        }
        if (!file.exists()) {
            logger.warn(
                "Config file not found: " +
                    "${file.absolutePath}. " +
                    "Continuing without it."
            )
            return emptyList()
        }
        logger.info(
            "Loading servers from --config: " +
                file.absolutePath
        )
        val content = file.readText()
        return JsonConfigLoader
            .parseMcpServersFormat(content)
    }

    /**
     * Merge JSON upstream servers into YAML config.
     * JSON servers are appended (not replaced) to YAML servers.
     * Duplicate names: JSON wins (higher priority).
     */
    private fun mergeJsonServers(
        yamlConfig: OrchestratorConfig,
        jsonServers: List<UpstreamServerConfig>
    ): OrchestratorConfig {
        if (jsonServers.isEmpty()) return yamlConfig

        val yamlNames = yamlConfig.orchestrator
            .upstreamServers.map { it.name }.toSet()
        val newServers = jsonServers
            .filter { it.name !in yamlNames }
        val overridden = jsonServers
            .filter { it.name in yamlNames }

        val merged = yamlConfig.orchestrator.upstreamServers
            .map { existing ->
                overridden.find { it.name == existing.name }
                    ?: existing
            } + newServers

        logger.info(
            "Merged ${jsonServers.size} JSON servers " +
                "(${newServers.size} new, " +
                "${overridden.size} overridden)"
        )

        return yamlConfig.copy(
            orchestrator = yamlConfig.orchestrator.copy(
                upstreamServers = merged
            )
        )
    }

    /**
     * Merge all server sources: YAML + JSON + CLI.
     * Priority: CLI > JSON > YAML (for same name).
     */
    private fun mergeAllServers(
        yamlConfig: OrchestratorConfig,
        jsonServers: List<UpstreamServerConfig>,
        cliServers: List<UpstreamServerConfig>
    ): OrchestratorConfig {
        val afterJson = mergeJsonServers(yamlConfig, jsonServers)
        if (cliServers.isEmpty()) return afterJson
        return mergeJsonServers(afterJson, cliServers)
    }

    private fun loadFromClasspath(): String {
        return this::class.java.classLoader
            .getResourceAsStream("application.yml")
            ?.bufferedReader()
            ?.readText()
            ?: throw ConfigException(
                "application.yml not found in classpath"
            )
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
