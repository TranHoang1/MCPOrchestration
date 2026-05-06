package com.orchestrator.mcp.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.orchestrator.mcp.core.config.ConfigurationManager
import com.orchestrator.mcp.core.config.OrchestratorConfig
import com.orchestrator.mcp.core.config.OrchestratorSettings
import com.orchestrator.mcp.core.config.UpstreamServerConfig
import com.orchestrator.mcp.core.model.ConfigException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

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

    override fun updateConfig(newConfig: OrchestratorConfig) {
        currentConfig = newConfig
        onChangeCallback?.invoke(newConfig)
    }

    override fun saveConfig(newConfig: OrchestratorConfig) {
        updateConfig(newConfig)
        
        // Try to save back to the source file (JSON preferred for upstream servers)
        val path = configPath ?: "mcp-servers.json"
        val file = File(path).let {
            if (it.isAbsolute) it
            else File(workingDirectory, path)
        }
        
        try {
            val jsonFormat = Json { prettyPrint = true }
            val serversJson = jsonFormat.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(UpstreamServerConfig.serializer()),
                newConfig.orchestrator.upstreamServers
            )
            
            file.writeText(serversJson)
            logger.info("Configuration saved to ${file.absolutePath}")
        } catch (e: Exception) {
            logger.error("Failed to save configuration to ${file.absolutePath}: ${e.message}")
            throw ConfigException("Failed to save configuration", e)
        }
    }

    private fun loadConfig(): OrchestratorConfig {
        return try {
            val yamlConfig = loadYamlConfig()
            
            // Try settings from --config file (if provided) first, then config.json
            val cliSettings = configPath?.let { path ->
                val file = File(path).let { if (it.isAbsolute) it else File(workingDirectory, path) }
                if (file.exists()) JsonConfigLoader.parseOrchestratorSettings(file.readText()) else null
            }
            val jsonSettings = if (cliSettings == null) loadJsonSettings() else null
            val mergedSettings = cliSettings ?: jsonSettings

            val jsonServers = loadJsonServers()
            val cliServers = loadCliConfigServers()

            // Start with YAML, then overlay JSON settings (embedding, vectorDb, etc.)
            val baseConfig = if (mergedSettings != null) {
                logger.info("Merging Orchestrator settings from JSON source")
                yamlConfig.copy(
                    orchestrator = yamlConfig.orchestrator.copy(
                        embedding = mergedSettings.embedding,
                        vectorDb = mergedSettings.vectorDb,
                        discovery = mergedSettings.discovery,
                        execution = mergedSettings.execution,
                        server = mergedSettings.server
                    )
                )
            } else {
                yamlConfig
            }

            val finalConfig = mergeAllServers(baseConfig, jsonServers, cliServers)
            
            // Apply environment variable overrides (highest priority)
            val finalSettingsWithEnv = applyEnvOverrides(finalConfig.orchestrator)
            finalConfig.copy(orchestrator = finalSettingsWithEnv)
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException(
                "Failed to parse configuration: ${e.message}",
                e
            )
        }
    }

    private fun loadYamlConfig(): OrchestratorConfig {
        val rawContent = configContent
            ?: configPath?.let { File(it).readText() }
            ?: loadYamlWithExternalFallback()

        val resolved = ConfigurationManagerImpl.resolveEnvVars(rawContent)
        return yaml.decodeFromString(
            OrchestratorConfig.serializer(), resolved
        )
    }

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

    private fun loadJsonSettings(): OrchestratorSettings? {
        val jsonContent = ExternalConfigScanner
            .findExternalJson(workingDirectory)
            ?: return null

        return JsonConfigLoader.parseOrchestratorSettings(jsonContent)
    }

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

    private fun mergeAllServers(
        yamlConfig: OrchestratorConfig,
        jsonServers: List<UpstreamServerConfig>,
        cliServers: List<UpstreamServerConfig>
    ): OrchestratorConfig {
        val afterJson = mergeJsonServers(yamlConfig, jsonServers)
        if (cliServers.isEmpty()) return afterJson
        return mergeJsonServers(afterJson, cliServers)
    }

    private fun applyEnvOverrides(settings: OrchestratorSettings): OrchestratorSettings {
        var embedding = settings.embedding
        var vectorDb = settings.vectorDb
        var server = settings.server

        System.getenv("EMBEDDING_PROVIDER")?.let { embedding = embedding.copy(provider = it) }
        System.getenv("EMBEDDING_MODEL")?.let { embedding = embedding.copy(model = it) }
        System.getenv("EMBEDDING_API_KEY")?.let { embedding = embedding.copy(apiKey = it) }
        System.getenv("EMBEDDING_DIMENSIONS")?.toIntOrNull()?.let { embedding = embedding.copy(dimensions = it) }
        System.getenv("EMBEDDING_BASE_URL")?.let { embedding = embedding.copy(baseUrl = it) }

        System.getenv("VECTOR_DB_PROVIDER")?.let { vectorDb = vectorDb.copy(provider = it) }
        System.getenv("VECTOR_DB_CONNECTION_STRING")?.let { vectorDb = vectorDb.copy(connectionString = it) }
        System.getenv("VECTOR_DB_HOST")?.let { vectorDb = vectorDb.copy(host = it) }
        System.getenv("VECTOR_DB_PORT")?.toIntOrNull()?.let { vectorDb = vectorDb.copy(port = it) }
        System.getenv("VECTOR_DB_USER")?.let { vectorDb = vectorDb.copy(user = it) }
        System.getenv("VECTOR_DB_PASSWORD")?.let { vectorDb = vectorDb.copy(password = it) }

        System.getenv("SERVER_PORT")?.toIntOrNull()?.let { server = server.copy(port = it) }
        System.getenv("SERVER_PROTOCOL")?.let { server = server.copy(protocol = it) }

        return settings.copy(
            embedding = embedding,
            vectorDb = vectorDb,
            server = server
        )
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
