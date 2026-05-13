package com.orchestrator.mcp.credentials.model

/**
 * Sealed exception hierarchy for Credential module.
 * Each exception carries an error code and HTTP status.
 */
sealed class CredentialException(
    message: String,
    val errorCode: String,
    val httpStatus: Int = 400
) : RuntimeException(message) {

    class SchemaNotFoundException(serverName: String) :
        CredentialException(
            "No credential schema defined for server '$serverName'",
            "SCHEMA_NOT_FOUND", 404
        )

    class DuplicateFieldKeyException(serverName: String, fieldKey: String) :
        CredentialException(
            "Field key '$fieldKey' already exists for server '$serverName'",
            "DUPLICATE_FIELD_KEY", 409
        )

    class InvalidFieldKeyException(fieldKey: String) :
        CredentialException(
            "Field key '$fieldKey' must match ^[a-z][a-z0-9_]{0,49}$",
            "INVALID_FIELD_KEY", 400
        )

    class FieldHasUserDataException(
        serverName: String,
        fieldKey: String,
        val affectedUsers: Int
    ) : CredentialException(
        "Field '$fieldKey' on '$serverName' has data for $affectedUsers user(s). " +
            "Add ?confirm=true to force delete.",
        "FIELD_HAS_USER_DATA", 409
    )

    class MissingCredentialException(serverName: String, fieldKey: String) :
        CredentialException(
            "Missing required credential '$fieldKey' for server '$serverName'",
            "MISSING_CREDENTIAL", 400
        )

    class DecryptionException(serverName: String) :
        CredentialException(
            "Failed to decrypt credentials for server '$serverName'",
            "DECRYPTION_FAILED", 500
        )

    class PayloadTooLargeException(sizeBytes: Int, maxBytes: Int) :
        CredentialException(
            "Payload size ${sizeBytes}B exceeds maximum ${maxBytes}B",
            "PAYLOAD_TOO_LARGE", 413
        )

    class InvalidUrlFormatException(fieldKey: String) :
        CredentialException(
            "Field '$fieldKey' must be a valid URL",
            "INVALID_URL_FORMAT", 400
        )

    class InvalidEmailFormatException(fieldKey: String) :
        CredentialException(
            "Field '$fieldKey' must be a valid email address",
            "INVALID_EMAIL_FORMAT", 400
        )
}

/** Thrown when field_type value is not in the allowed enum. */
class InvalidFieldTypeException(value: String) :
    RuntimeException("Invalid field_type '$value'. Allowed: url, email, secret, text, number")
