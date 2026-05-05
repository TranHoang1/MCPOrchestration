package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.fileproxy.model.DetectionResult
import com.orchestrator.mcp.fileproxy.model.ProxyDirection
import com.orchestrator.mcp.model.InvalidFileIdException
import com.orchestrator.mcp.registry.ToolRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Main orchestration implementation for file proxy.
 * Coordinates detection, wrapper generation, and proxy handling.
 */
class FileProxyServiceImpl(
    private val detector: FileProxyDetector,
    private val wrapperGenerator: WrapperToolGenerator,
    private val inputHandler: InputFileProxyHandler,
    private val outputHandler: OutputFileProxyHandler,
    private val registry: FileProxyRegistry,
    private val config: FileProxyConfig,
    private val toolRegistry: ToolRegistry,
    private val uploadHandler: FileUploadHandler,
    private val executionDispatcher: ToolExecutionDispatcher
) : FileProxyService {

    private val logger = LoggerFactory.getLogger(FileProxyServiceImpl::class.java)
    private lateinit var sessionId: UUID

    override suspend fun initialize(sessionId: UUID) {
        this.sessionId = sessionId
        if (!config.enabled) {
            logger.info("[FileProxy] Feature disabled — skipping initialization")
            return
        }
        logger.info("[FileProxy] Initializing file proxy for session={}", sessionId)
        detectAndWrapAllTools()
    }

    override suspend fun handleProxyCall(
        toolName: String,
        serverName: String,
        arguments: JsonObject,
        transportMode: String
    ): ExecuteToolResponse {
        val detections = wrapperGenerator.getDetections(toolName)
        val resolvedServerName = serverName.ifEmpty {
            detections.firstOrNull()?.serverName ?: ""
        }
        val inputDetections = detections.filter { it.direction == ProxyDirection.INPUT }
        val outputDetections = detections.filter { it.direction == ProxyDirection.OUTPUT }

        // Handle input proxy
        var response: ExecuteToolResponse? = null
        if (inputDetections.isNotEmpty() && config.inputProxyEnabled) {
            response = handleInputProxy(toolName, resolvedServerName, arguments, inputDetections, transportMode)
        }

        // If no input proxy, execute normally
        if (response == null) {
            val cleanArgs = removeProxyParams(arguments)
            response = executionDispatcher.execute(toolName, cleanArgs)
        }

        // Handle output proxy
        val outputPath = arguments["output_path"]?.jsonPrimitive?.content
        if (outputPath != null && outputDetections.isNotEmpty() && config.outputProxyEnabled) {
            response = outputHandler.processOutputProxy(response, outputPath)
        }

        return response
    }

    override fun isProxyTool(toolName: String): Boolean {
        return wrapperGenerator.hasWrapper(toolName)
    }

    override suspend fun redetectServer(serverName: String) {
        detector.invalidateServer(serverName)
        wrapperGenerator.removeServerWrappers(serverName)
        detectAndWrapServerTools(serverName)
    }

    private suspend fun detectAndWrapAllTools() {
        val allTools = toolRegistry.getAllTools()
        val serverGroups = allTools.groupBy { it.serverName }

        for ((serverName, tools) in serverGroups) {
            detectAndWrapTools(serverName, tools.map { it.name })
        }
    }

    private suspend fun detectAndWrapServerTools(serverName: String) {
        val tools = toolRegistry.getToolsByServer(serverName)
        detectAndWrapTools(serverName, tools.map { it.name })
    }

    private fun detectAndWrapTools(serverName: String, toolNames: List<String>) {
        for (toolName in toolNames) {
            val tool = toolRegistry.lookupTool(toolName) ?: continue
            val schema = tool.inputSchema ?: continue

            val inputResults = detector.detectInputFileParams(toolName, serverName, schema)
            val hasOutput = detector.detectOutputFileResponse(toolName, serverName, null)

            val allResults = inputResults.toMutableList()
            if (hasOutput) {
                allResults.add(
                    DetectionResult(
                        toolName, serverName, "response",
                        ProxyDirection.OUTPUT,
                        com.orchestrator.mcp.fileproxy.model.DetectionMethod.SCHEMA_TYPE,
                        0.8f
                    )
                )
            }

            if (allResults.isNotEmpty()) {
                val transport = "stdio" // Default; actual transport resolved at call time
                wrapperGenerator.generateWrapper(tool, allResults, transport)
            }
        }
    }

    private suspend fun handleInputProxy(
        toolName: String,
        serverName: String,
        arguments: JsonObject,
        detections: List<DetectionResult>,
        transportMode: String
    ): ExecuteToolResponse {
        val detection = detections.first()

        return if (transportMode == "stdio") {
            handleStdioInput(toolName, serverName, arguments, detection)
        } else {
            handleHttpInput(toolName, serverName, arguments, detection)
        }
    }

    private suspend fun handleStdioInput(
        toolName: String,
        serverName: String,
        arguments: JsonObject,
        detection: DetectionResult
    ): ExecuteToolResponse {
        val filePath = arguments["file_path"]?.jsonPrimitive?.content
            ?: throw com.orchestrator.mcp.model.InvalidParamsException("file_path is required")

        val otherArgs = arguments.filterKeys { it != "file_path" && it != "output_path" }
            .mapValues { it.value.jsonPrimitive.content }

        // Large-text params (confidence 0.75) are read as raw text, not base64
        val encodeBase64 = detection.confidence > 0.8f

        return inputHandler.processInputProxy(
            toolName, serverName, filePath, detection.paramName, otherArgs, encodeBase64
        )
    }

    private suspend fun handleHttpInput(
        toolName: String,
        serverName: String,
        arguments: JsonObject,
        detection: DetectionResult
    ): ExecuteToolResponse {
        val fileIdStr = arguments["file_id"]?.jsonPrimitive?.content
            ?: throw com.orchestrator.mcp.model.InvalidParamsException("file_id is required")

        val fileId = try {
            UUID.fromString(fileIdStr)
        } catch (_: Exception) {
            throw InvalidFileIdException(fileIdStr)
        }

        val base64Content = uploadHandler.resolveFileId(fileId)
        val otherArgs = arguments.filterKeys { it != "file_id" && it != "output_path" }

        val upstreamArgs = kotlinx.serialization.json.buildJsonObject {
            put(detection.paramName, kotlinx.serialization.json.JsonPrimitive(base64Content))
            otherArgs.forEach { (k, v) -> put(k, v) }
        }

        return executionDispatcher.execute(toolName, upstreamArgs)
    }

    private fun removeProxyParams(arguments: JsonObject): JsonObject {
        return JsonObject(arguments.filterKeys { it != "file_path" && it != "file_id" && it != "output_path" })
    }
}
