package com.orchestrator.mcp.sync.pipeline.unit

import com.orchestrator.mcp.sync.pipeline.crawl.ContentHasher
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

// STC: UT-001 — ContentHasher known outputs
class ContentHasherTest : FunSpec({

    val hasher = ContentHasher()

    test("hash of 'Hello World' matches known SHA-256") {
        val expected = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e"
        hasher.hash("Hello World") shouldBe expected
    }

    test("hash of empty string returns valid SHA-256") {
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        hasher.hash("") shouldBe expected
    }

    test("hash is case-sensitive") {
        hasher.hash("hello") shouldNotBe hasher.hash("Hello")
    }

    test("hashParts joins with pipe separator") {
        val combined = hasher.hashParts("a", "b", "c")
        combined shouldBe hasher.hash("a|b|c")
    }

    test("hashParts filters null values") {
        val result = hasher.hashParts("a", null, "c")
        result shouldBe hasher.hash("a|c")
    }

    test("hashParts with all nulls returns hash of empty string") {
        hasher.hashParts(null, null) shouldBe hasher.hash("")
    }
})
