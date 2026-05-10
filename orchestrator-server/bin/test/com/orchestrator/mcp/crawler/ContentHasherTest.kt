package com.orchestrator.mcp.crawler

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength

class ContentHasherTest : DescribeSpec({

    val hasher = ContentHasher()

    describe("computeHash") {
        it("produces consistent SHA-256 hash") {
            val hash1 = hasher.computeHash("hello world")
            val hash2 = hasher.computeHash("hello world")
            hash1 shouldBe hash2
        }

        it("produces 64-char hex string") {
            val hash = hasher.computeHash("test content")
            hash shouldHaveLength 64
        }

        it("produces different hashes for different content") {
            val hash1 = hasher.computeHash("content A")
            val hash2 = hasher.computeHash("content B")
            hash1 shouldNotBe hash2
        }

        it("handles empty string") {
            val hash = hasher.computeHash("")
            hash shouldHaveLength 64
        }
    }

    describe("hasChanged") {
        it("returns true when existing hash is null") {
            hasher.hasChanged("abc123", null) shouldBe true
        }

        it("returns true when hashes differ") {
            hasher.hasChanged("new_hash", "old_hash") shouldBe true
        }

        it("returns false when hashes match") {
            hasher.hasChanged("same_hash", "same_hash") shouldBe false
        }
    }
})
