package com.orchestrator.mcp.sync.pipeline.unit

import com.orchestrator.mcp.sync.pipeline.SyncTestFixtures
import com.orchestrator.mcp.sync.pipeline.dimension.builtin.AttachmentDimension
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

// STC: UT-008 — AttachmentDimension extraction
class AttachmentDimensionTest : FunSpec({

    val dimension = AttachmentDimension()
    val config = SyncTestFixtures.dimensionConfig(id = "attachments")

    test("2 attachments produce 2 entries") {
        val ticket = SyncTestFixtures.crawledTicket(attachments = 2)
        val entries = dimension.extract(ticket, config)
        entries.size shouldBe 2
    }

    test("zero attachments produce empty list") {
        val ticket = SyncTestFixtures.crawledTicket(attachments = 0)
        val entries = dimension.extract(ticket, config)
        entries.size shouldBe 0
    }

    test("download_url is NOT exposed in data map") {
        val ticket = SyncTestFixtures.crawledTicket(attachments = 1)
        val entry = dimension.extract(ticket, config).first()
        (entry.data.containsKey("download_url")) shouldBe false
        (entry.data.containsKey("downloadUrl")) shouldBe false
    }

    test("vectorText is null for attachments") {
        val ticket = SyncTestFixtures.crawledTicket(attachments = 1)
        val entry = dimension.extract(ticket, config).first()
        entry.vectorText shouldBe null
    }

    test("data map contains filename and mime_type") {
        val ticket = SyncTestFixtures.crawledTicket(attachments = 1)
        val entry = dimension.extract(ticket, config).first()
        entry.data["filename"] shouldBe "file1.png"
        entry.data["mime_type"] shouldBe "image/png"
    }

    test("sourceRef path includes attachment ID") {
        val ticket = SyncTestFixtures.crawledTicket(
            key = "P-1", projectKey = "P", attachments = 1
        )
        val entry = dimension.extract(ticket, config).first()
        entry.sourceRef.path shouldBe "jira:P/P-1/attachment/att-1"
        entry.sourceRef.type shouldBe "jira_attachment"
    }

    test("entryKey format is ticketKey:attachmentId") {
        val ticket = SyncTestFixtures.crawledTicket(key = "T-3", attachments = 1)
        val entry = dimension.extract(ticket, config).first()
        entry.entryKey shouldBe "T-3:att-1"
    }
})
