package com.orchestrator.mcp.sync.pipeline.pbt

import com.orchestrator.mcp.sync.pipeline.dimension.builtin.deterministicId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

// STC: PBT-002 — IndexEntry ID Determinism
class IndexEntryIdPropertyTest : FunSpec({

    test("deterministicId produces same UUID for same key") {
        checkAll(1000, Arb.string(1..200)) { key ->
            deterministicId(key) shouldBe deterministicId(key)
        }
    }

    test("different keys produce different UUIDs") {
        checkAll(500, Arb.string(1..100)) { key ->
            val key2 = key + ":suffix"
            deterministicId(key) shouldNotBe deterministicId(key2)
        }
    }

    test("deterministicId output is valid UUID format") {
        val uuidRegex = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        )
        checkAll(500, Arb.string(1..100)) { key ->
            deterministicId(key).matches(uuidRegex) shouldBe true
        }
    }
})
