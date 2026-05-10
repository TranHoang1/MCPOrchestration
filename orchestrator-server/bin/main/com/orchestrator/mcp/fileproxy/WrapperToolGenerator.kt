package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.fileproxy.model.DetectionResult
import com.orchestrator.mcp.fileproxy.model.ProxyDirection
import com.orchestrator.mcp.core.model.ToolEntry
import com.orchestrator.mcp.registry.ToolRegistry
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Generates wrapper tool definitions that replace original upstream tools.
 * Wrappers substitute file content params with file_path/file_id.
 */
class WrapperToolGenerator(
    private val toolRegistry: ToolRegistry
) {
    private val logger = LoggerFactory.getLogger(WrapperToolGenerator::class.java)

    // Maps wrapper tool name → original tool entry
    private val wrapperMap = ConcurrentHashMap<String, ToolEntry>()
    // Maps wrapper tool name → detection results
    private val wrapperDetections = ConcurrentHashMap<String, List<DetectionResult>>()

    /**
     * Generate a wrapper tool and register it, hiding the original.
     */
    fun generateWrapper(
        originalTool: ToolEntry,
        detectionResults: List<DetectionResult>,
        transportMode: String
    ): ToolEntry? {
        if (detectionResults.isEmpty()) return null

        val inputResults = detectionResults.filter { it.direction == ProxyDirection.INPUT }
        val outputResults = detectionResults.filter { it.direction == ProxyDirection.OUTPUT }

        val newSchema = buildWrapperSchema(
            originalTool.inputSchema, inputResults, outputResults, transportMode
        )
        val newDescription = buildWrapperDescription(
            originalTool.description, inputResults, outputResults, transportMode
        )

        val wrapper = ToolEntry(
            name = originalTool.name,
            description = newDescription,
            inputSchema = newSchema,
            serverName = originalTool.serverName,
            serverStatus = originalTool.serverStatus
        )

        wrapperMap[originalTool.name] = originalTool
        wrapperDetections[originalTool.name] = detectionResults
        toolRegistry.registerTool(wrapper)

        logger.info("[FileProxy] Wrapper created: tool={}, direction={}",
            originalTool.name,
            detectionResults.map { it.direction }.distinct()
        )
        return wrapper
    }

    fun hasWrapper(toolName: String): Boolean = wrapperMap.containsKey(toolName)

    fun getOriginalTool(wrapperName: String): ToolEntry? = wrapperMap[wrapperName]

    fun getDetections(toolName: String): List<DetectionResult> {
        return wrapperDetections[toolName] ?: emptyList()
    }

    fun removeWrapper(toolName: String) {
        val original = wrapperMap.remove(toolName) ?: return
        wrapperDetections.remove(toolName)
        toolRegistry.registerTool(original)
    }

    fun removeServerWrappers(serverName: String) {
        wrapperMap.entries.filter { it.value.serverName == serverName }
            .forEach { removeWrapper(it.key) }
    }

    private fun buildWrapperSchema(
        originalSchema: JsonObject?,
        inputResults: List<DetectionResult>,
        outputResults: List<DetectionResult>,
        transportMode: String
    ): JsonObject {
        val original = originalSchema ?: return buildJsonObject { }
        val properties = original["properties"]?.jsonObject ?: JsonObject(emptyMap())
        val required = original["required"]?.jsonArray?.map {
            it.jsonPrimitive.content
        }?.toMutableList() ?: mutableListOf()

        val newProperties = buildJsonObject {
            // Replace file params with file_path or file_id
            for ((key, value) in properties) {
                val isFileParam = inputResults.any { it.paramName == key }
                if (isFileParam) {
                    putFileParam(this, key, transportMode)
                    // Update required list
                    if (key in required) {
                        required.remove(key)
                        required.add(getReplacementParamName(transportMode))
                    }
                } else {
                    put(key, value)
                }
            }
            // Add output_path if output proxy detected
            if (outputResults.isNotEmpty()) {
                putOutputPathParam(this)
            }
        }

        return buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", newProperties)
            if (required.isNotEmpty()) {
                put("required", JsonArray(required.distinct().map { JsonPrimitive(it) }))
            }
        }
    }

    private fun buildWrapperDescription(
        original: String,
        inputResults: List<DetectionResult>,
        outputResults: List<DetectionResult>,
        transportMode: String
    ): String {
        val parts = mutableListOf(original)
        if (inputResults.isNotEmpty()) {
            val paramRef = if (transportMode == "stdio") "file_path" else "file_id"
            parts.add("Accepts $paramRef instead of base64 content — file is read and encoded automatically.")
        }
        if (outputResults.isNotEmpty()) {
            parts.add("Optionally specify output_path to save file output to a specific location.")
        }
        return parts.joinToString(" ")
    }

    private fun getReplacementParamName(transportMode: String): String {
        return if (transportMode == "stdio") "file_path" else "file_id"
    }

    companion object {
        private fun putFileParam(builder: JsonObjectBuilder, originalParam: String, transportMode: String) {
            if (transportMode == "stdio") {
                builder.put("file_path", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive(
                        "Absolute path to the input file. File will be read and base64-encoded automatically."
                    ))
                })
            } else {
                builder.put("file_id", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive(
                        "UUID returned from upload_file tool. File content will be resolved automatically."
                    ))
                })
            }
        }

        private fun putOutputPathParam(builder: JsonObjectBuilder) {
            builder.put("output_path", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive(
                    "Optional. Absolute path where output file should be saved. If not provided, response is returned as-is."
                ))
            })
        }
    }
}
