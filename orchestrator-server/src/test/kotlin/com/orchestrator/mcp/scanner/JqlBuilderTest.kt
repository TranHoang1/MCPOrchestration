package com.orchestrator.mcp.scanner

import com.orchestrator.mcp.scanner.model.ScanType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.datetime.Instant

class JqlBuilderTest : DescribeSpec({

    val builder = JqlBuilder(syncBufferMinutes = 1)

    describe("build") {
        it("builds full scan JQL with ORDER BY updated DESC") {
            val jql = builder.build("MTO", ScanType.FULL, null)
            jql shouldBe """project = "MTO" ORDER BY updated DESC"""
        }

        it("builds resumed scan JQL same as full scan") {
            val jql = builder.build("MTO", ScanType.RESUMED, null)
            jql shouldBe """project = "MTO" ORDER BY updated DESC"""
        }

        it("builds incremental JQL with updated filter and 1-min buffer") {
            val lastSync = Instant.parse("2026-05-01T10:30:00Z")
            val jql = builder.build("MTO", ScanType.INCREMENTAL, lastSync)

            jql shouldContain """project = "MTO""""
            jql shouldContain "AND updated >"
            // Buffer: 10:30 - 1min = 10:29
            jql shouldContain "10:29"
            jql shouldContain "ORDER BY updated DESC"
        }

        it("handles midnight buffer correctly") {
            val lastSync = Instant.parse("2026-05-01T00:00:00Z")
            val jql = builder.build("PROJ", ScanType.INCREMENTAL, lastSync)

            // 00:00 - 1min = 23:59 of previous day
            jql shouldContain "2026-04-30"
            jql shouldContain "23:59"
        }

        it("does not include updated filter for FULL scan") {
            val jql = builder.build("TEST", ScanType.FULL, Instant.parse("2026-01-01T00:00:00Z"))
            jql shouldNotContain "updated >"
        }
    }
})
