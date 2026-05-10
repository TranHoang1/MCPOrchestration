package com.orchestrator.mcp.usermanagement.model

/**
 * Sealed exception hierarchy for User Management module.
 * Each exception carries an error code for consistent API responses.
 */
sealed class UserManagementException(
    message: String,
    val errorCode: String
) : RuntimeException(message) {

    class DuplicateEmailException(email: String) :
        UserManagementException("User with email $email already exists", "DUPLICATE_EMAIL")

    class UserNotFoundException(id: String) :
        UserManagementException("User with id $id not found", "USER_NOT_FOUND")

    class InvalidRoleException(role: String) :
        UserManagementException("Invalid role: $role", "INVALID_ROLE")

    class PermissionDeniedException(reason: String) :
        UserManagementException(reason, "PERMISSION_DENIED")

    class LastAdminException :
        UserManagementException("Cannot deactivate last system_owner", "LAST_ADMIN")

    class TokenValidationException(email: String) :
        UserManagementException("Jira token validation failed for $email", "TOKEN_INVALID")

    class DuplicateApprovalException :
        UserManagementException("Already approved this document version", "DUPLICATE_APPROVAL")

    class DocumentNotFoundException(ticket: String, type: String) :
        UserManagementException("No $type attachment found on $ticket", "DOC_NOT_FOUND")

    class DuplicateProjectException(projectKey: String) :
        UserManagementException("User already assigned to project $projectKey", "DUPLICATE_PROJECT")
}
