package com.orchestrator.mcp.sync.pipeline.pbt

import com.orchestrator.mcp.sync.pipeline.crawl.ContentHasher
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

// STC: PBT-001 — ContentHasher Determinism
class ContentHasherPropertyTest : FunSpec({

    val hasher = ContentHasher()

    test("hash is deterministic: hash(s) == hash(s) for all strings") {
        checkAll(1000, Arb.string(0..500)) { s ->
            hasher.hash(s) shouldBe hasher.hash(s)
        }
    }

    test("different strings produce different hashes with high probability") {
        checkAll(500, Arb.string(1..100)) { s1 ->
            val s2 = s1 + "x"
            hasher.hash(s1) shouldNotBe hasher.hash(s2)
        }
    }

    test("hash output is always 64 hex characters (SHA-256)") {
        checkAll(500, Arb.string(0..1000)) { s ->
            val hash = hasher.hash(s)
            hash.length shouldBe 64
            hash.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
        }
    }

    test("hashParts is deterministic for same inputs") {
        checkAll(200, Arb.string(0..100)) { s ->
            hasher.hashParts(s, "part2") shouldBe hasher.hashParts(s, "part2")
        }
    }
})
