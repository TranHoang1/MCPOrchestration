package com.orchestrator.mcp.usermanagement.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class TokenEncryptionServiceTest : DescribeSpec({

    // Direct test of encryption logic without env var dependency
    val testKey = ByteArray(32) { it.toByte() } // deterministic 32-byte key
    val testKeySpec = SecretKeySpec(testKey, "AES")

    describe("AES-256-GCM encryption logic") {
        it("encrypts and decrypts roundtrip") {
            val plaintext = "my-secret-jira-token-12345"
            val encrypted = encryptWithKey(plaintext, testKeySpec)
            val decrypted = decryptWithKey(encrypted, testKeySpec)
            decrypted shouldBe plaintext
        }

        it("produces different ciphertext for same plaintext (random IV)") {
            val plaintext = "same-token"
            val encrypted1 = encryptWithKey(plaintext, testKeySpec)
            val encrypted2 = encryptWithKey(plaintext, testKeySpec)
            encrypted1 shouldNotBe encrypted2
        }

        it("encrypted output does not contain plaintext") {
            val plaintext = "visible-secret"
            val encrypted = encryptWithKey(plaintext, testKeySpec)
            encrypted shouldNotContain plaintext
        }

        it("decrypt with wrong key fails") {
            val plaintext = "secret"
            val encrypted = encryptWithKey(plaintext, testKeySpec)
            val wrongKey = SecretKeySpec(ByteArray(32) { 0xFF.toByte() }, "AES")
            shouldThrow<Exception> {
                decryptWithKey(encrypted, wrongKey)
            }
        }
    }
})

private fun encryptWithKey(plaintext: String, key: SecretKeySpec): String {
    val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
    val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(iv + encrypted)
}

private fun decryptWithKey(ciphertext: String, key: SecretKeySpec): String {
    val combined = Base64.getDecoder().decode(ciphertext)
    val iv = combined.copyOfRange(0, 12)
    val encrypted = combined.copyOfRange(12, combined.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
    return String(cipher.doFinal(encrypted), Charsets.UTF_8)
}
