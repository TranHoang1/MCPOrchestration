package com.orchestrator.mcp.kb.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Loads KB server configuration from YAML file.
 * Pipeline: read → resolve env vars → deserialize → validate.
 */
object KbConfigLoader {

    private val logger = LoggerFactory.getLogger(KbConfigLoader::class.java)

    private val yaml = Yaml(
        configuration = YamlConfiguration(strictMode = false)
    )

    fun load(configPath: String?): KbConfig {
        val file = resolveConfigFile(configPath)
        if (file == null) {
            logger.info("No config file found, using defaults")
            return KbConfigValidator.validate(KbConfig())
        }

        val config = try {
            logger.info("Loading config from: {}", file.absolutePath)
            val rawContent = file.readText()
            val resolvedContent = KbEnvVarResolver.resolve(rawContent)
            yaml.decodeFromString(KbConfig.serializer(), resolvedContent)
        } catch (e: Exception) {
            logger.error("Failed to load config: {}, using defaults", e.message)
            KbConfig()
        }
        return KbConfigValidator.validate(config)
    }

    private fun resolveConfigFile(configPath: String?): File? {
        if (configPath != null) {
            val file = File(configPath)
            if (file.exists()) return file
            logger.warn("Specified config not found: $configPath")
        }

        val defaultLocations = listOf(
            "application.yml",
            "config/application.yml",
            "kb-server/src/main/resources/application.yml"
        )

        return defaultLocations
            .map { File(it) }
            .firstOrNull { it.exists() }
    }
}
