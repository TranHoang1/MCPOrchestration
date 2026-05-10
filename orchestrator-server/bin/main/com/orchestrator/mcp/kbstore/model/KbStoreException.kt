package com.orchestrator.mcp.kbstore.model

/**
 * Sealed exception hierarchy for KB Store operations.
 */
sealed class KbStoreException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    class ConfigException(message: String) :
        KbStoreException(message)

    class EncryptionException(message: String, cause: Throwable? = null) :
        KbStoreException(message, cause)

    class RepositoryException(message: String, cause: Throwable? = null) :
        KbStoreException(message, cause)

    class ValidationException(message: String) :
        KbStoreException(message)
}
