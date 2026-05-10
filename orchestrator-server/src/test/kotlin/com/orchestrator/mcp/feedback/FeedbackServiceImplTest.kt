package com.orchestrator.mcp.feedback

import com.orchestrator.mcp.feedback.model.*
import com.orchestrator.mcp.feedback.repository.FeedbackRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.Clock

class FeedbackServiceImplTest : FunSpec({

    val repository = mockk<FeedbackRepository>(relaxed = true)
    val config = FeedbackConfig(maxContentLength = 5000)
    val service = FeedbackServiceImpl(repository, config)

    val sampleFeedback = Feedback(
        id = 0,
        issueKey = "PROJ-1",
        userId = "user@test.com",
        type = FeedbackType.INACCURATE,
        content = "This information is outdated"
    )

    test("submit saves feedback and returns with id") {
        val saved = sampleFeedback.copy(id = 1)
        coEvery { repository.save(any()) } returns saved

        val result = service.submit(sampleFeedback)

        result.id shouldBe 1
        result.status shouldBe FeedbackStatus.PENDING
        coVerify { repository.save(sampleFeedback) }
    }

    test("submit rejects content exceeding max length") {
        val longFeedback = sampleFeedback.copy(content = "A".repeat(6000))

        val exception = runCatching { service.submit(longFeedback) }.exceptionOrNull()
        exception.shouldNotBeNull()
    }

    test("approve updates status to APPROVED") {
        val pending = sampleFeedback.copy(id = 5, status = FeedbackStatus.PENDING)
        coEvery { repository.findById(5) } returns pending

        val result = service.approve(5, "admin@test.com")

        result.shouldNotBeNull()
        result.status shouldBe FeedbackStatus.APPROVED
        result.reviewerId shouldBe "admin@test.com"
        result.resolvedAt.shouldNotBeNull()
    }

    test("approve returns null for non-existent feedback") {
        coEvery { repository.findById(999) } returns null

        val result = service.approve(999, "admin@test.com")
        result.shouldBeNull()
    }

    test("approve returns null for already resolved feedback") {
        val approved = sampleFeedback.copy(id = 5, status = FeedbackStatus.APPROVED)
        coEvery { repository.findById(5) } returns approved

        val result = service.approve(5, "admin@test.com")
        result.shouldBeNull()
    }

    test("reject updates status to REJECTED with reason") {
        val pending = sampleFeedback.copy(id = 7, status = FeedbackStatus.PENDING)
        coEvery { repository.findById(7) } returns pending

        val result = service.reject(7, "admin@test.com", "Not a valid issue")

        result.shouldNotBeNull()
        result.status shouldBe FeedbackStatus.REJECTED
        result.rejectionReason shouldBe "Not a valid issue"
        result.reviewerId shouldBe "admin@test.com"
    }

    test("getByIssueKey delegates to repository") {
        val feedbacks = listOf(sampleFeedback.copy(id = 1), sampleFeedback.copy(id = 2))
        coEvery { repository.findByIssueKey("PROJ-1") } returns feedbacks

        val result = service.getByIssueKey("PROJ-1")
        result.size shouldBe 2
    }

    test("getByStatus delegates to repository") {
        coEvery { repository.findByStatus(FeedbackStatus.PENDING, 50) } returns listOf(sampleFeedback)

        val result = service.getByStatus(FeedbackStatus.PENDING)
        result.size shouldBe 1
    }

    test("getStats calculates resolution rate correctly") {
        coEvery { repository.count() } returns 10
        coEvery { repository.countByStatus(FeedbackStatus.PENDING) } returns 3
        coEvery { repository.countByStatus(FeedbackStatus.APPROVED) } returns 5
        coEvery { repository.countByStatus(FeedbackStatus.REJECTED) } returns 2

        val stats = service.getStats()

        stats.totalFeedback shouldBe 10
        stats.pending shouldBe 3
        stats.approved shouldBe 5
        stats.rejected shouldBe 2
        stats.resolutionRate shouldBe 0.7
    }

    test("getStats handles zero total") {
        coEvery { repository.count() } returns 0
        coEvery { repository.countByStatus(any()) } returns 0

        val stats = service.getStats()
        stats.resolutionRate shouldBe 0.0
    }
})
