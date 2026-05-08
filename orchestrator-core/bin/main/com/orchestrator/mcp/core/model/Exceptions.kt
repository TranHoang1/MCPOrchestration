package com.orchestrator.mcp.core.model

/**
 * Sealed exception hierarchy for MCP Orchestrator.
 * Each exception maps to a specific error code in the MCP response.
 */
sealed class McpOrchestratorException(
    val errorCode: String,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

// --- Client input validation errors ---

class InvalidParamsException(message: String) :
    McpOrchestratorException(ErrorCodes.INVALID_PARAMS, message)

class ToolNotFoundException(toolName: String) :
    McpOrchestratorException(
        ErrorCodes.TOOL_NOT_FOUND,
        "Tool '$toolName' is not registered. Use find_tools to discover available tools."
    )

class ServerUnavailableException(toolName: String, serverName: String, status: Any) :
    McpOrchestratorException(
        ErrorCodes.SERVER_UNAVAILABLE,
        "Server hosting '$toolName' is currently unavailable. Status: $status."
    )

class ExecutionTimeoutException(toolName: String, timeoutSeconds: Int) :
    McpOrchestratorException(
        ErrorCodes.EXECUTION_TIMEOUT,
        "Tool execution timed out after ${timeoutSeconds}s."
    )

class UpstreamErrorException(message: String, val upstreamServer: String = "") :
    McpOrchestratorException(ErrorCodes.UPSTREAM_ERROR, "Upstream error: $message")

class VectorDbUnavailableException(cause: Throwable? = null) :
    McpOrchestratorException(
        ErrorCodes.VECTOR_DB_UNAVAILABLE,
        "Vector DB is unavailable, using keyword fallback",
        cause
    )

class EmbeddingServiceException(cause: Throwable? = null) :
    McpOrchestratorException(
        ErrorCodes.EMBEDDING_SERVICE_ERROR,
        "Embedding service unavailable",
        cause
    )

class ConfigException(message: String, cause: Throwable? = null) :
    McpOrchestratorException(ErrorCodes.CONFIG_INVALID, message, cause)

class ToolDisabledException(toolName: String) :
    McpOrchestratorException(
        ErrorCodes.TOOL_DISABLED,
        "Tool '$toolName' is currently disabled. Use toggle_tool to re-enable."
    )

class ConfigWriteException(message: String, cause: Throwable? = null) :
    McpOrchestratorException(ErrorCodes.CONFIG_WRITE_FAILED, message, cause)

class GenericMcpException(errorCode: String, message: String) :
    McpOrchestratorException(errorCode, message)

// --- File Proxy Exceptions ---

class FileNotFoundException(filePath: String) :
    McpOrchestratorException(ErrorCodes.FILE_NOT_FOUND, "File not found: $filePath")

class FileTooLargeException(maxMb: Int, actualMb: String) :
    McpOrchestratorException(
        ErrorCodes.FILE_TOO_LARGE,
        "File exceeds maximum size (${maxMb}MB). Actual: ${actualMb}MB"
    )

class FileNotReadableException(filePath: String) :
    McpOrchestratorException(
        ErrorCodes.FILE_NOT_READABLE,
        "Cannot read file: $filePath — permission denied"
    )

class InvalidFilePathException(reason: String) :
    McpOrchestratorException(ErrorCodes.INVALID_PATH, "Invalid file path: $reason")

class InvalidFileIdException(fileId: String) :
    McpOrchestratorException(
        ErrorCodes.INVALID_FILE_ID,
        "Invalid file_id format — expected UUID: $fileId"
    )

class FileIdNotFoundException(fileId: String) :
    McpOrchestratorException(
        ErrorCodes.FILE_ID_NOT_FOUND,
        "File not found — file_id may have expired: $fileId"
    )

class FileExpiredException(fileId: String) :
    McpOrchestratorException(ErrorCodes.FILE_EXPIRED, "File expired — please re-upload")

class FileMissingOnDiskException(fileId: String) :
    McpOrchestratorException(
        ErrorCodes.FILE_MISSING_ON_DISK,
        "File not found on disk — please re-upload"
    )

class OutputSaveFailedException(reason: String) :
    McpOrchestratorException(
        ErrorCodes.OUTPUT_SAVE_FAILED,
        "Failed to save output file: $reason"
    )

class PathValidationException(message: String) :
    McpOrchestratorException(ErrorCodes.INVALID_PATH, message)

class FileWriteException(message: String) :
    McpOrchestratorException(ErrorCodes.OUTPUT_SAVE_FAILED, message)

// --- HTTP Streamable Transport Exceptions ---

class SessionNotFoundException(sessionId: String) :
    McpOrchestratorException(
        ErrorCodes.SESSION_NOT_FOUND,
        "Session not found: $sessionId"
    )

class SessionExpiredException(sessionId: String) :
    McpOrchestratorException(
        ErrorCodes.SESSION_EXPIRED,
        "Session expired: $sessionId"
    )

class StreamResumeException(eventId: String) :
    McpOrchestratorException(
        ErrorCodes.EVENT_NOT_FOUND,
        "Event not found for resumption: $eventId"
    )

class ServerOverloadedException(maxSessions: Int) :
    McpOrchestratorException(
        ErrorCodes.SERVER_OVERLOADED,
        "Max sessions reached ($maxSessions). Retry later."
    )
