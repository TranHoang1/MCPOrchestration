package com.orchestrator.mcp.sync.pipeline

import com.orchestrator.mcp.sync.pipeline.model.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.hours

/**
 * Shared test fixtures for sync-pipeline tests.
 */
object SyncTestFixtures {

    val NOW: Instant = Clock.System.now()
    val HOUR_AGO: Instant = NOW.minus(1.hours)

    fun jiraUser(
        accountId: String = "user-1",
        displayName: String = "Test User",
        email: String? = "test@example.com"
    ) = JiraUser(
        accountId = accountId,
        displayName = displayName,
        email = email
    )

    fun crawledComment(
        id: String = "1001",
        author: JiraUser = jiraUser(),
        body: String = "This is a test comment"
    ) = CrawledComment(
        commentId = id,
        author = author,
        body = body,
        createdAt = HOUR_AGO,
        updatedAt = NOW
    )

    fun crawledAttachment(
        id: String = "att-1",
        filename: String = "screenshot.png",
        mimeType: String = "image/png",
        sizeBytes: Long = 1024L
    ) = CrawledAttachment(
        attachmentId = id,
        filename = filename,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        author = jiraUser(),
        createdAt = HOUR_AGO,
        downloadUrl = "https://jira.example.com/att/$id"
    )

    fun crawledLink(
        type: String = "blocks",
        direction: String = "outward",
        targetKey: String = "TEST-2"
    ) = CrawledLink(type = type, direction = direction, targetKey = targetKey)

    fun crawledTicket(
        key: String = "TEST-1",
        projectKey: String = "TEST",
        comments: Int = 3,
        attachments: Int = 1,
        links: Int = 2
    ) = CrawledTicket(
        key = key,
        projectKey = projectKey,
        summary = "Test ticket summary for $key",
        description = "Detailed description for $key",
        issueType = "Story",
        status = "In Progress",
        priority = "High",
        assignee = jiraUser("assignee-1", "Assignee User"),
        reporter = jiraUser("reporter-1", "Reporter User"),
        parentKey = null,
        epicKey = "TEST-100",
        labels = listOf("backend", "api"),
        components = listOf("sync-pipeline"),
        fixVersions = listOf("1.0.0"),
        storyPoints = 5.0,
        sprint = "Sprint 10",
        createdAt = HOUR_AGO,
        updatedAt = NOW,
        resolvedAt = null,
        comments = (1..comments).map { i ->
            crawledComment(
                id = "comment-$i",
                author = jiraUser("user-$i", "User $i")
            )
        },
        links = (1..links).map { i ->
            crawledLink(targetKey = "TEST-${i + 10}")
        },
        attachments = (1..attachments).map { i ->
            crawledAttachment(id = "att-$i", filename = "file$i.png")
        },
        contentHash = "abc123hash"
    )

    fun dimensionConfig(
        id: String = "ticket_metadata",
        enabled: Boolean = true,
        sortOrder: Int = 0
    ) = DimensionConfig(
        id = id,
        displayName = "Test Dimension $id",
        enabled = enabled,
        sourceType = "jira_ticket",
        fields = null,
        indexStrategy = "upsert",
        vectorEnabled = false,
        processorClass = null,
        configJson = null,
        sortOrder = sortOrder
    )
}
