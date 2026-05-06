package com.orchestrator.mcp.config

import org.slf4j.LoggerFactory
import java.io.File

/**
 * Scans the working directory for external configuration
 * files (application.yml, application.json, config.json).
 *
 * Config loading order (lowest to highest priority):
 * 1. Classpath application.yml (bundled in JAR)
 * 2. External application.yml (working directory)
 * 3. External JSON (config.json or application.json)
 * 4. Environment variables (override ${VAR} refs)
 */
object ExternalConfigScanner {

    private val logger = LoggerFactory.getLogger(
        ExternalConfigScanner::class.java
    )

    private val YAML_FILES = listOf(
        "application.yml",
        "application.yaml"
    )

    private val JSON_FILES = listOf(
        "config.json",
        "application.json"
    )

    /**
     * Scan a directory for external YAML config file.
     * Returns file content or null if not found.
     */
    fun findExternalYaml(
        directory: File = File(".")
    ): String? {
        for (name in YAML_FILES) {
            val file = File(directory, name)
            if (file.exists() && file.isFile) {
                logger.info(
                    "Found external YAML config: ${file.absolutePath}"
                )
                return file.readText()
            }
        }
        return null
    }

    /**
     * Scan a directory for external JSON config file.
     * Returns file content or null if not found.
     */
    fun findExternalJson(
        directory: File = File(".")
    ): String? {
        for (name in JSON_FILES) {
            val file = File(directory, name)
            if (file.exists() && file.isFile) {
                logger.info(
                    "Found external JSON config: ${file.absolutePath}"
                )
                return file.readText()
            }
        }
        return null
    }

    /**
     * List all config files found in the directory.
     * Useful for logging/diagnostics.
     */
    fun listConfigFiles(
        directory: File = File(".")
    ): List<String> {
        val found = mutableListOf<String>()
        (YAML_FILES + JSON_FILES).forEach { name ->
            val file = File(directory, name)
            if (file.exists() && file.isFile) {
                found.add(file.name)
            }
        }
        return found
    }
}
