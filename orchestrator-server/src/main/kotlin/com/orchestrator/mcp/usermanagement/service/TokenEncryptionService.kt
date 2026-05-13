package com.orchestrator.mcp.usermanagement.service

import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.sql.DataSource

/**
 * AES-256-GCM encryption service for sensitive data.
 * Key resolution: env var → DB system_config → auto-generate and persist.
 */
interface TokenEncryptionService {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

class TokenEncryptionServiceImpl(
    private val encryptionKeyEnv: String,
    private val dataSource: DataSource? = null
) : TokenEncryptionService {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val GCM_TAG_LENGTH = 128
    private val GCM_IV_LENGTH = 12

    private val secretKey: SecretKeySpec by lazy { resolveOrGenerateKey() }

    override fun encrypt(plaintext: String): String {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    override fun decrypt(ciphertext: String): String {
        val combined = Base64.getDecoder().decode(ciphertext)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun resolveOrGenerateKey(): SecretKeySpec {
        val envKey = System.getenv(encryptionKeyEnv)
        if (!envKey.isNullOrBlank()) return decodeKey(envKey, "env")
        val dbKey = loadKeyFromDb()
        if (dbKey != null) return decodeKey(dbKey, "db")
        return generateAndPersistKey()
    }

    private fun decodeKey(base64: String, source: String): SecretKeySpec {
        val bytes = Base64.getDecoder().decode(base64)
        require(bytes.size == 32) { "Key from $source must be 32 bytes" }
        logger.info("Encryption key loaded from {}", source)
        return SecretKeySpec(bytes, "AES")
    }

    private fun generateAndPersistKey(): SecretKeySpec {
        val keyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val keyBase64 = Base64.getEncoder().encodeToString(keyBytes)
        persistKeyToDb(keyBase64)
        logger.warn("Auto-generated encryption key and persisted to DB")
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun loadKeyFromDb(): String? {
        val ds = dataSource ?: return null
        return try {
            ds.connection.use { conn ->
                ensureSystemConfigTable(conn)
                val sql = "SELECT config_value FROM system_config WHERE config_key = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, encryptionKeyEnv)
                    val rs = stmt.executeQuery()
                    if (rs.next()) rs.getString("config_value") else null
                }
            }
        } catch (e: Exception) {
            logger.debug("Cannot load key from DB: {}", e.message)
            null
        }
    }

    private fun persistKeyToDb(keyBase64: String) {
        val ds = dataSource ?: run {
            logger.warn("No DataSource — key exists only in memory this session")
            return
        }
        try {
            ds.connection.use { conn ->
                ensureSystemConfigTable(conn)
                val sql = """
                    INSERT INTO system_config (config_key, config_value)
                    VALUES (?, ?)
                    ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, encryptionKeyEnv)
                    stmt.setString(2, keyBase64)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to persist key to DB: {}", e.message)
        }
    }

    private fun ensureSystemConfigTable(conn: java.sql.Connection) {
        val sql = """
            CREATE TABLE IF NOT EXISTS system_config (
                config_key TEXT PRIMARY KEY,
                config_value TEXT NOT NULL,
                created_at TEXT DEFAULT to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
            )
        """.trimIndent()
        conn.createStatement().use { it.execute(sql) }
    }
}
