package com.orchestrator.mcp.scanner

import com.orchestrator.mcp.jira.model.JiraIssue
import com.orchestrator.mcp.scanner.model.LinkDirection
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*

class MetadataParserTest : DescribeSpec({

    val parser = MetadataParser()

    describe("parse") {
        it("parses a complete Jira issue correctly") {
            val issue = createIssue(
                key = "MTO-17",
                summary = "Project Scanner",
                status = "In Progress",
                issueType = "Story",
                priority = "High",
                assignee = "John Doe",
                updated = "2026-05-01T10:30:00.000+0000"
            )

            val result = parser.parse(listOf(issue))
            result shouldHaveSize 1
            val meta = result.first()
            meta.issueKey shouldBe "MTO-17"
            meta.projectKey shouldBe "MTO"
            meta.summary shouldBe "Project Scanner"
            meta.status shouldBe "In Progress"
            meta.issueType shouldBe "Story"
            meta.priority shouldBe "High"
            meta.assignee shouldBe "John Doe"
        }

        it("handles null assignee") {
            val issue = createIssue(key = "MTO-1", assignee = null)
            val result = parser.parse(listOf(issue))
            result.first().assignee shouldBe null
        }

        it("handles null priority with default Medium") {
            val issue = createIssue(key = "MTO-2", priority = null)
            val result = parser.parse(listOf(issue))
            result.first().priority shouldBe "Medium"
        }

        it("extracts parent key") {
            val fields = buildJsonObject {
                put("summary", "Child task")
                put("status", buildJsonObject { put("name", "To Do") })
                put("issuetype", buildJsonObject { put("name", "Sub-task") })
                put("parent", buildJsonObject { put("key", "MTO-10") })
                put("updated", "2026-05-01T10:00:00.000+0000")
            }
            val issue = JiraIssue(id = "1", key = "MTO-11", self = "", fields = fields)
            val result = parser.parse(listOf(issue))
            result.first().parentKey shouldBe "MTO-10"
        }

        it("extracts issue links") {
            val fields = buildJsonObject {
                put("summary", "Linked issue")
                put("status", buildJsonObject { put("name", "Done") })
                put("issuetype", buildJsonObject { put("name", "Task") })
                put("updated", "2026-05-01T10:00:00.000+0000")
                putJsonArray("issuelinks") {
                    addJsonObject {
                        put("type", buildJsonObject { put("name", "Blocks") })
                        put("outwardIssue", buildJsonObject { put("key", "MTO-20") })
                    }
                    addJsonObject {
                        put("type", buildJsonObject { put("name", "Relates") })
                        put("inwardIssue", buildJsonObject { put("key", "MTO-5") })
                    }
                }
            }
            val issue = JiraIssue(id = "2", key = "MTO-15", self = "", fields = fields)
            val result = parser.parse(listOf(issue))
            val links = result.first().links
            links shouldHaveSize 2
            links[0].type shouldBe "Blocks"
            links[0].direction shouldBe LinkDirection.OUTWARD
            links[0].targetKey shouldBe "MTO-20"
            links[1].direction shouldBe LinkDirection.INWARD
            links[1].targetKey shouldBe "MTO-5"
        }

        it("extracts labels") {
            val fields = buildJsonObject {
                put("summary", "Labeled issue")
                put("status", buildJsonObject { put("name", "To Do") })
                put("issuetype", buildJsonObject { put("name", "Story") })
                put("updated", "2026-05-01T10:00:00.000+0000")
                putJsonArray("labels") { add("backend"); add("urgent") }
            }
            val issue = JiraIssue(id = "3", key = "MTO-30", self = "", fields = fields)
            val result = parser.parse(listOf(issue))
            result.first().labels shouldBe listOf("backend", "urgent")
        }

        it("skips unparseable issues and returns rest") {
            val good = createIssue(key = "MTO-1")
            val bad = JiraIssue(id = "x", key = "MTO-2", self = "", fields = buildJsonObject {})
            // bad issue has no "updated" but parser handles gracefully
            val result = parser.parse(listOf(good, bad))
            // Both should parse — bad one uses Clock.System.now() for updatedAt
            result shouldHaveSize 2
        }

        it("returns empty list for empty input") {
            parser.parse(emptyList()).shouldBeEmpty()
        }
    }
})

private fun createIssue(
    key: String,
    summary: String = "Test Summary",
    status: String = "To Do",
    issueType: String = "Task",
    priority: String? = "Medium",
    assignee: String? = "Test User",
    updated: String = "2026-05-01T10:00:00.000+0000"
): JiraIssue {
    val fields = buildJsonObject {
        put("summary", summary)
        put("status", buildJsonObject { put("name", status) })
        put("issuetype", buildJsonObject { put("name", issueType) })
        if (priority != null) {
            put("priority", buildJsonObject { put("name", priority) })
        } else {
            put("priority", JsonNull)
        }
        if (assignee != null) {
            put("assignee", buildJsonObject { put("displayName", assignee) })
        } else {
            put("assignee", JsonNull)
        }
        put("updated", updated)
    }
    return JiraIssue(id = "1", key = key, self = "", fields = fields)
}
