package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.execution.model.ExecutionContentItem
import com.orchestrator.mcp.core.model.OutputSaveFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*

/**
 * Implementation of OutputFileProxyHandler.
 * Detects file content in upstream responses and saves to output_path.
 */
class OutputFileProxyHandlerImpl(
    private val registry: FileProxyRegistry,
    private val config: FileProxyConfig
) : OutputFileProxyHandler {

    private val logger = LoggerFactory.getLogger(OutputFileProxyHandlerImpl::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun processOutputProxy(
        upstreamResponse: ExecuteToolResponse,
        outputPath: String
    ): ExecuteToolResponse {
        FilePathValidator.validateOutputPath(outputPath)

        val textContent = upstreamResponse.content.firstOrNull { it.type == "text" }?.text
            ?: throw OutputSaveFailedException("No text content in upstream response")

        return try {
            val responseJson = json.parseToJsonElement(textContent).jsonObject
            saveFromResponse(responseJson, outputPath)
        } catch (e: OutputSaveFailedException) {
            throw e
        } catch (e: Exception) {
            throw OutputSaveFailedException(e.message ?: "Unknown error")
        }
    }

    override fun containsFileContent(response: ExecuteToolResponse): Boolean {
        val text = response.content.firstOrNull { it.type == "text" }?.text ?: return false
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            hasArtifacts(obj) || hasBase64Content(obj)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun saveFromResponse(
        responseJson: JsonObject,
        outputPath: String
    ): ExecuteToolResponse {
        // Strategy 1: artifacts[].path — copy file
        val artifacts = responseJson["artifacts"]?.jsonArray
        if (artifacts != null && artifacts.isNotEmpty()) {
            return saveFromArtifacts(artifacts, outputPath)
        }

        // Strategy 2: base64 content field — decode and write
        val base64Field = findBase64Field(responseJson)
        if (base64Field != null) {
            return saveFromBase64(base64Field, outputPath)
        }

        // Strategy 3: file_path field — copy file
        val filePath = responseJson["file_path"]?.jsonPrimitive?.content
        if (filePath != null) {
            return copyFile(filePath, outputPath)
        }

        throw OutputSaveFailedException("No file content found in upstream response")
    }

    private suspend fun saveFromArtifacts(
        artifacts: JsonArray,
        outputPath: String
    ): ExecuteToolResponse {
        val firstArtifact = artifacts[0].jsonObject
        val sourcePath = firstArtifact["path"]?.jsonPrimitive?.content
            ?: throw OutputSaveFailedException("Artifact missing 'path' field")

        return copyFile(sourcePath, outputPath)
    }

    private suspend fun saveFromBase64(
        base64Content: String,
        outputPath: String
    ): ExecuteToolResponse {
        val bytes = Base64.getDecoder().decode(base64Content)
        val bytesWritten = withContext(Dispatchers.IO) {
            Files.write(Path.of(outputPath), bytes)
            bytes.size.toLong()
        }

        logger.info("[FileProxy] OUTPUT proxy: saved {} bytes to {}", bytesWritten, outputPath)
        return buildSaveResponse(outputPath, bytesWritten, "BASE64")
    }

    private suspend fun copyFile(sourcePath: String, outputPath: String): ExecuteToolResponse {
        val bytesWritten = withContext(Dispatchers.IO) {
            val source = Path.of(sourcePath)
            val target = Path.of(outputPath)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            Files.size(target)
        }

        logger.info("[FileProxy] OUTPUT proxy: copied {} bytes to {}", bytesWritten, outputPath)
        return buildSaveResponse(outputPath, bytesWritten, "FILE_PATH")
    }

    private fun buildSaveResponse(
        outputPath: String,
        bytesWritten: Long,
        sourceType: String
    ): ExecuteToolResponse {
        val responseText = buildJsonObject {
            put("saved_to", JsonPrimitive(outputPath))
            put("bytes_written", JsonPrimitive(bytesWritten))
            put("source_type", JsonPrimitive(sourceType))
        }.toString()

        return ExecuteToolResponse(
            content = listOf(ExecutionContentItem(type = "text", text = responseText))
        )
    }

    private fun hasArtifacts(obj: JsonObject): Boolean = obj.containsKey("artifacts")

    private fun hasBase64Content(obj: JsonObject): Boolean {
        return obj.keys.any { it.contains("base64", ignoreCase = true) }
    }

    private fun findBase64Field(obj: JsonObject): String? {
        for ((key, value) in obj) {
            if (key.contains("base64", ignoreCase = true)) {
                return value.jsonPrimitive.content
            }
        }
        return null
    }
}
