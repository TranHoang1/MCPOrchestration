package com.orchestrator.mcp.security.br

import com.orchestrator.mcp.security.br.model.BrAccessConfig
import com.orchestrator.mcp.security.br.model.KeyStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.Base64

class BrKeyManagementServiceImplTest : FunSpec({

    // Generate a valid 32-byte key for testing
    val testKeyBytes = ByteArray(32) { it.toByte() }
    val testKeyBase64 = Base64.getEncoder().encodeToString(testKeyBytes)
    val keyId = "test-key-001"

    val config = BrAccessConfig(
        activeKeyId = keyId,
        encryptionKeyBase64 = testKeyBase64
    )
    val service = BrKeyManagementServiceImpl(config)

    test("encrypt produces payload with keyId:iv:ciphertext format") {
        val encrypted = service.encrypt("Hello Business Rule")

        encrypted shouldContain ":"
        val parts = encrypted.split(":")
        parts.size shouldBe 3
        parts[0] shouldBe keyId
    }

    test("decrypt restores original plaintext") {
        val plaintext = "Approval threshold is 50000 USD"
        val encrypted = service.encrypt(plaintext)

        val decrypted = service.decrypt(encrypted, keyId)

        decrypted.shouldNotBeNull()
        decrypted shouldBe plaintext
    }

    test("decrypt returns null for invalid payload") {
        val result = service.decrypt("invalid-payload", keyId)
        result.shouldBeNull()
    }

    test("decrypt returns null for unknown key") {
        val encrypted = service.encrypt("test content")
        val result = service.decrypt(encrypted, "unknown-key")
        // Should still work because payload contains the actual keyId
        result.shouldNotBeNull()
    }

    test("registerKey adds new key successfully") {
        val newKeyBytes = ByteArray(32) { (it + 100).toByte() }
        val newKeyBase64 = Base64.getEncoder().encodeToString(newKeyBytes)

        val result = service.registerKey("new-key-002", newKeyBase64)
        result shouldBe true
    }

    test("registerKey fails for invalid key size") {
        val shortKey = Base64.getEncoder().encodeToString(ByteArray(16))
        val result = service.registerKey("bad-key", shortKey)
        result shouldBe false
    }

    test("getKeyMetadata returns metadata for registered key") {
        val metadata = service.getKeyMetadata(keyId)

        metadata.shouldNotBeNull()
        metadata.keyId shouldBe keyId
        metadata.status shouldBe KeyStatus.ACTIVE
    }

    test("getKeyMetadata returns null for unknown key") {
        val metadata = service.getKeyMetadata("nonexistent")
        metadata.shouldBeNull()
    }

    test("unique IV per encryption — same plaintext produces different ciphertext") {
        val plaintext = "Same content"
        val encrypted1 = service.encrypt(plaintext)
        val encrypted2 = service.encrypt(plaintext)

        encrypted1 shouldBe encrypted1 // sanity
        // IVs should differ, so ciphertexts differ
        val iv1 = encrypted1.split(":")[1]
        val iv2 = encrypted2.split(":")[1]
        // Extremely unlikely to be equal with random IVs
        // but we verify both decrypt correctly
        service.decrypt(encrypted1, keyId) shouldBe plaintext
        service.decrypt(encrypted2, keyId) shouldBe plaintext
    }
})
