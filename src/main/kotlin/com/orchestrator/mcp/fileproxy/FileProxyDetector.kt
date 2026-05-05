package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.fileproxy.model.DetectionMethod
import com.orchestrator.mcp.fileproxy.model.DetectionResult
import com.orchestrator.mcp.fileproxy.model.ProxyDirection
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Schema-based detection of file content parameters in tool definitions.
 * Uses heuristics: param name patterns, description keywords, schema type.
 */
class FileProxyDetector {

    private val logger = LoggerFactory.getLogger(FileProxyDetector::class.java)
    private val detectionCache = ConcurrentHashMap<String, List<DetectionResult>>()

    fun detectInputFileParams(
        toolName: String,
        serverName: String,
        inputSchema: JsonObject
    ): List<DetectionResult> {
        val cacheKey = "$serverName::$toolName::INPUT"
        detectionCache[cacheKey]?.let { return it }

        val results = scanPropertiesForInput(toolName, serverName, inputSchema)
        detectionCache[cacheKey] = results

        results.forEach { r ->
            logger.info(
                "[FileProxy] Detected: tool={}, param={}, method={}, confidence={}",
                r.toolName, r.paramName, r.method, r.confidence
            )
        }
        return results
    }

    fun detectOutputFileResponse(
        toolName: String,
        serverName: String,
        outputSchema: JsonObject?
    ): Boolean {
        if (outputSchema == null) return false
        val cacheKey = "$serverName::$toolName::OUTPUT"
        if (detectionCache.containsKey(cacheKey)) {
            return detectionCache[cacheKey]!!.isNotEmpty()
        }

        val hasOutput = checkOutputSchema(outputSchema)
        if (hasOutput) {
            val result = DetectionResult(
                toolName, serverName, "response",
                ProxyDirection.OUTPUT, DetectionMethod.SCHEMA_TYPE, 0.8f
            )
            detectionCache[cacheKey] = listOf(result)
        } else {
            detectionCache[cacheKey] = emptyList()
        }
        return hasOutput
    }

    fun detectOutputFromResponse(toolName: String, response: JsonObject): Boolean {
        return hasArtifactsPath(response) || hasBase64Field(response)
    }

    fun getDetectionResults(serverName: String): List<DetectionResult> {
        return detectionCache.entries
            .filter { it.key.startsWith("$serverName::") }
            .flatMap { it.value }
    }

    fun invalidateServer(serverName: String) {
        detectionCache.keys.removeIf { it.startsWith("$serverName::") }
    }

    private fun scanPropertiesForInput(
        toolName: String,
        serverName: String,
        schema: JsonObject
    ): List<DetectionResult> {
        val properties = schema["properties"]?.jsonObject ?: return emptyList()
        val results = mutableListOf<DetectionResult>()

        for ((paramName, paramSchema) in properties) {
            val obj = paramSchema.jsonObject
            val detection = detectFileParam(toolName, serverName, paramName, obj)
            if (detection != null) results.add(detection)
        }
        return results
    }

    private fun detectFileParam(
        toolName: String,
        serverName: String,
        paramName: String,
        paramSchema: JsonObject
    ): DetectionResult? {
        // Check by schema type (contentEncoding: base64) — highest confidence
        val encoding = paramSchema["contentEncoding"]?.jsonPrimitive?.content
        if (encoding == "base64") {
            return DetectionResult(
                toolName, serverName, paramName,
                ProxyDirection.INPUT, DetectionMethod.SCHEMA_TYPE, 0.95f
            )
        }

        // Check by name pattern
        if (isFileParamByName(paramName)) {
            return DetectionResult(
                toolName, serverName, paramName,
                ProxyDirection.INPUT, DetectionMethod.NAME_PATTERN, 0.9f
            )
        }

        // Check by description keywords
        val desc = paramSchema["description"]?.jsonPrimitive?.content ?: ""
        if (isFileParamByDescription(desc)) {
            return DetectionResult(
                toolName, serverName, paramName,
                ProxyDirection.INPUT, DetectionMethod.DESCRIPTION_KEYWORD, 0.8f
            )
        }

        return null
    }

    private fun checkOutputSchema(schema: JsonObject): Boolean {
        val props = schema["properties"]?.jsonObject ?: return false
        return props.keys.any { it in OUTPUT_INDICATORS }
    }

    private fun hasArtifactsPath(response: JsonObject): Boolean {
        return response.containsKey("artifacts") || response.containsKey("file_path")
    }

    private fun hasBase64Field(response: JsonObject): Boolean {
        return response.keys.any { key ->
            key.contains("base64", ignoreCase = true) ||
                key.contains("content", ignoreCase = true)
        }
    }

    companion object {
        private val FILE_PARAM_NAMES = setOf(
            "content", "file_content", "data", "file_data",
            "base64", "base64_content", "file_base64",
            "image", "image_data", "pdf_content", "document"
        )

        private val FILE_DESCRIPTION_KEYWORDS = listOf(
            "base64", "file content", "binary content",
            "encoded content", "file data", "raw bytes"
        )

        private val OUTPUT_INDICATORS = setOf(
            "artifacts", "output_file", "file_path",
            "generated_file", "result_file"
        )

        private fun isFileParamByName(name: String): Boolean {
            return name.lowercase() in FILE_PARAM_NAMES
        }

        private fun isFileParamByDescription(desc: String): Boolean {
            val lower = desc.lowercase()
            return FILE_DESCRIPTION_KEYWORDS.any { lower.contains(it) }
        }
    }
}
