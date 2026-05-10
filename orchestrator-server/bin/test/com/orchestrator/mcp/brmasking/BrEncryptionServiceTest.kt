package com.orchestrator.mcp.brmasking

import com.orchestrator.mcp.brmasking.crypto.BrEncryptionService
import com.orchestrator.mcp.brmasking.model.BrMaskingException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.util.Base64

class BrEncryptionServiceTest : FunSpec({

    // Valid 32-byte test key (Base64-encoded)
    val testKeyBytes = ByteArray(32) { it.toByte() }
    val testKeyBase64 = Base64.getEncoder().encodeToString(testKeyBytes)
    val service = BrEncryptionService(testKeyBase64)

    // --- PBT-01: Encrypt-Decrypt Roundtrip ---
    test("PBT-01: encrypt-decrypt roundtrip preserves original text") {
        checkAll(1000, Arb.string(1..2000)) { plaintext ->
            val (ciphertext, iv) = service.encrypt(plaintext)
            service.decrypt(ciphertext, iv) shouldBe plaintext
        }
    }

    // --- PBT-03: Unique IV Per Encryption ---
    test("PBT-03: each encryption produces unique IV") {
        checkAll(100, Arb.string(1..500)) { plaintext ->
            val ivs = (1..5).map { service.encrypt(plaintext).second }
            ivs.distinct().size shouldBe ivs.size
        }
    }

    // --- UT-13: Encrypt Returns Non-Empty Ciphertext ---
    test("UT-13: encrypt produces non-empty Base64 ciphertext and IV") {
        val (ciphertext, iv) = service.encrypt("Lãi suất 12.5%/năm")
        ciphertext.shouldNotBeBlank()
        iv.shouldNotBeBlank()
        // Verify valid Base64
        Base64.getDecoder().decode(ciphertext) shouldNotBe null
        Base64.getDecoder().decode(iv) shouldNotBe null
    }

    // --- UT-14: Decrypt Returns Original Text ---
    test("UT-14: decrypt with correct key and IV returns original plaintext") {
        val original = "Ngưỡng rủi ro: NPL > 5%"
        val (ciphertext, iv) = service.encrypt(original)
        service.decrypt(ciphertext, iv) shouldBe original
    }

    // --- UT-15: Decrypt With Wrong Key Throws Exception ---
    test("UT-15: decryption with wrong key throws DecryptionException") {
        val (ciphertext, iv) = service.encrypt("secret BR content")

        val wrongKeyBytes = ByteArray(32) { (it + 100).toByte() }
        val wrongKeyBase64 = Base64.getEncoder().encodeToString(wrongKeyBytes)
        val wrongService = BrEncryptionService(wrongKeyBase64)

        shouldThrow<BrMaskingException.DecryptionException> {
            wrongService.decrypt(ciphertext, iv)
        }
    }

    // --- UT-16: Invalid Key Length Throws Exception ---
    test("UT-16: key shorter than 32 bytes throws exception") {
        val shortKeyBytes = ByteArray(16) { it.toByte() }
        val shortKeyBase64 = Base64.getEncoder().encodeToString(shortKeyBytes)

        shouldThrow<IllegalArgumentException> {
            BrEncryptionService(shortKeyBase64)
        }
    }

    // --- UT-17: Blank Key Throws InvalidKeyException ---
    test("UT-17: empty encryption key throws InvalidKeyException") {
        shouldThrow<BrMaskingException.InvalidKeyException> {
            BrEncryptionService("")
        }
    }

    // Additional: Vietnamese text with special characters
    test("encrypt-decrypt handles Vietnamese text with special characters") {
        val text = "Lãi suất ưu đãi: 6.5%/năm → 15% sau 12 tháng"
        val (ciphertext, iv) = service.encrypt(text)
        service.decrypt(ciphertext, iv) shouldBe text
    }

    // Additional: Corrupted ciphertext throws DecryptionException
    test("corrupted ciphertext throws DecryptionException") {
        val (_, iv) = service.encrypt("test")
        shouldThrow<BrMaskingException.DecryptionException> {
            service.decrypt("corrupted_not_base64_valid!!!", iv)
        }
    }
})
