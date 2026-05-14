package com.orchestrator.mcp.credentials

import com.orchestrator.mcp.credentials.model.CredentialException
import com.orchestrator.mcp.credentials.model.ResolvedConfig
import org.slf4j.LoggerFactory
import java.security.MessageDigest

/**
 * Implementation of CredentialResolver.
 * Extracts {placeholder} patterns, fetches user credentials, and substitutes values.
 * CRITICAL: Decrypted values are NEVER logged.
 */
class CredentialResolverImpl(
    private val credentialService: UserCredentialService,
    private val schemaRepo: CredentialSchemaRepository
) : CredentialResolver {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val placeholderRegex = Regex("\\{([a-z_]+)\\}")

    override suspend fun resolve(
        userId: String,
        serverName: String,
        command: String,
        args: List<String>,
        env: Map<String, String>
    ): ResolvedConfig {
        if (!hasPlaceholders(command, args, env)) {
            return buildNoPlaceholderResult(serverName, command, args, env)
        }
        val credentials = fetchAndValidateCredentials(userId, serverName, command, args, env)
        return buildResolvedConfig(serverName, command, args, env, credentials)
    }

    override fun hasPlaceholders(command: String, args: List<String>, env: Map<String, String>): Boolean {
        if (placeholderRegex.containsMatchIn(command)) return true
        if (args.any { placeholderRegex.containsMatchIn(it) }) return true
        if (env.values.any { placeholderRegex.containsMatchIn(it) }) return true
        return false
    }

    override suspend fun getDecryptedCredentials(userId: String, serverName: String): Map<String, String>? {
        return credentialService.getDecryptedCredentials(userId, serverName)
    }

    private fun buildNoPlaceholderResult(
        serverName: String,
        command: String,
        args: List<String>,
        env: Map<String, String>
    ): ResolvedConfig {
        val poolKey = computeHash(serverName)
        return ResolvedConfig(command = command, args = args, env = env, poolKey = poolKey)
    }

    private suspend fun fetchAndValidateCredentials(
        userId: String,
        serverName: String,
        command: String,
        args: List<String>,
        env: Map<String, String>
    ): Map<String, String> {
        val credentials = credentialService.getDecryptedCredentials(userId, serverName)
            ?: throw CredentialException.MissingCredentialException(serverName, "*")
        val requiredKeys = extractAllPlaceholders(command, args, env)
        validateAllKeysPresent(serverName, credentials, requiredKeys)
        return credentials
    }

    private fun buildResolvedConfig(
        serverName: String,
        command: String,
        args: List<String>,
        env: Map<String, String>,
        credentials: Map<String, String>
    ): ResolvedConfig {
        val resolvedCommand = replacePlaceholders(command, credentials)
        val resolvedArgs = args.map { replacePlaceholders(it, credentials) }
        val resolvedEnv = env.mapValues { (_, v) -> replacePlaceholders(v, credentials) }
        val poolKey = computePoolKey(serverName, credentials)
        logger.debug("Resolved config for server={}, poolKey={}", serverName, poolKey)
        return ResolvedConfig(resolvedCommand, resolvedArgs, resolvedEnv, poolKey)
    }

    private fun extractAllPlaceholders(
        command: String,
        args: List<String>,
        env: Map<String, String>
    ): Set<String> {
        val keys = mutableSetOf<String>()
        placeholderRegex.findAll(command).forEach { keys.add(it.groupValues[1]) }
        args.forEach { arg -> placeholderRegex.findAll(arg).forEach { keys.add(it.groupValues[1]) } }
        env.values.forEach { v -> placeholderRegex.findAll(v).forEach { keys.add(it.groupValues[1]) } }
        return keys
    }

    private fun validateAllKeysPresent(
        serverName: String,
        credentials: Map<String, String>,
        requiredKeys: Set<String>
    ) {
        for (key in requiredKeys) {
            if (credentials[key].isNullOrBlank()) {
                throw CredentialException.MissingCredentialException(serverName, key)
            }
        }
    }

    private fun replacePlaceholders(template: String, credentials: Map<String, String>): String {
        return placeholderRegex.replace(template) { match ->
            credentials[match.groupValues[1]] ?: match.value
        }
    }

    private fun computePoolKey(serverName: String, credentials: Map<String, String>): String {
        val sortedValues = credentials.toSortedMap().values.joinToString("|")
        return computeHash("$serverName|$sortedValues")
    }

    companion object {
        /** SHA-256 hash utility. */
        fun computeHash(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }

    override suspend fun getFirstAvailableCredentials(serverName: String): Map<String, String>? {
        return try {
            credentialService.getFirstAvailableForServer(serverName)
        } catch (e: Exception) {
            logger.debug("No credentials available for server={}: {}", serverName, e.message)
            null
        }
    }
}
