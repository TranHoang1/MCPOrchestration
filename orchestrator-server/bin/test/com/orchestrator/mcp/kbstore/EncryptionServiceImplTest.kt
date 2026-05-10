package com.orchestrator.mcp.kbstore

import com.orchestrator.mcp.kbstore.encryption.EncryptionServiceImpl
import com.orchestrator.mcp.kbstore.model.KbStoreException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.util.Base64

class EncryptionServiceImplTest : FunSpec({

    // Valid 32-byte key (Base64-encoded)
    val validKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    test("UT-01: encrypt/decrypt roundtrip returns original text") {
        val service = EncryptionServiceImpl(validKey)
        val plaintext = "Lãi suất cho vay tiêu dùng: 12.5%/năm"

        val encrypted = service.encrypt(plaintext)
        val decrypted = service.decrypt(encrypted)

        decrypted shouldBe plaintext
    }

    test("UT-02: encrypted output differs from plaintext") {
        val service = EncryptionServiceImpl(validKey)
        val plaintext = "Secret business rule"

        val encrypted = service.encrypt(plaintext)

        String(encrypted, Charsets.UTF_8) shouldNotBe plaintext
    }

    test("UT-03: same plaintext produces different ciphertext (random IV)") {
        val service = EncryptionServiceImpl(validKey)
        val plaintext = "Same input"

        val encrypted1 = service.encrypt(plaintext)
        val encrypted2 = service.encrypt(plaintext)

        // Different IVs → different ciphertext
        encrypted1.contentEquals(encrypted2) shouldBe false
    }

    test("UT-04: encrypted output starts with 12-byte IV") {
        val service = EncryptionServiceImpl(validKey)
        val encrypted = service.encrypt("test")

        // IV (12 bytes) + ciphertext (at least 4 bytes + 16 byte tag)
        (encrypted.size >= 12 + 4 + 16) shouldBe true
    }

    test("UT-05: rejects empty encryption key") {
        val ex = shouldThrow<KbStoreException.ConfigException> {
            EncryptionServiceImpl("")
        }
        ex.message shouldContain "empty"
    }

    test("UT-06: rejects key with wrong size (16 bytes)") {
        val shortKey = Base64.getEncoder().encodeToString(ByteArray(16))
        val ex = shouldThrow<KbStoreException.ConfigException> {
            EncryptionServiceImpl(shortKey)
        }
        ex.message shouldContain "32 bytes"
    }

    test("UT-07: decrypt with wrong key throws EncryptionException") {
        val service1 = EncryptionServiceImpl(validKey)
        val otherKey = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 50).toByte() })
        val service2 = EncryptionServiceImpl(otherKey)

        val encrypted = service1.encrypt("secret")

        shouldThrow<KbStoreException.EncryptionException> {
            service2.decrypt(encrypted)
        }
    }

    test("UT-08: decrypt too-short ciphertext throws EncryptionException") {
        val service = EncryptionServiceImpl(validKey)

        shouldThrow<KbStoreException.EncryptionException> {
            service.decrypt(ByteArray(5))
        }
    }

    test("UT-09: handles Unicode text (Vietnamese diacritics)") {
        val service = EncryptionServiceImpl(validKey)
        val text = "Điều kiện duyệt vay: thu nhập >= 10 triệu VNĐ"

        val decrypted = service.decrypt(service.encrypt(text))

        decrypted shouldBe text
    }

    test("UT-10: handles empty string") {
        val service = EncryptionServiceImpl(validKey)

        val decrypted = service.decrypt(service.encrypt(""))

        decrypted shouldBe ""
    }
})
