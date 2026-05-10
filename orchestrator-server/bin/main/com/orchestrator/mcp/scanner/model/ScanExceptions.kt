package com.orchestrator.mcp.scanner.model

/**
 * Sealed hierarchy of scanner-specific exceptions.
 */
sealed class ScanException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ScanAlreadyRunningException(projectKey: String) :
    ScanException("Scan already running for project '$projectKey'")

class InvalidProjectKeyException(projectKey: String) :
    ScanException("Invalid project key: '$projectKey'. Must match [A-Z][A-Z0-9_]+")

class ScanFailedException(projectKey: String, cause: Throwable? = null) :
    ScanException("Scan failed for project '$projectKey': ${cause?.message}", cause)
