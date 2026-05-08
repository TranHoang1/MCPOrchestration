package com.orchestrator.mcp.core.util

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class RetryUtilsTest : DescribeSpec({

    describe("RetryUtils.calculateBackoff") {

        it("should return base delay at attempt 0") {
            RetryUtils.calculateBackoff(0, 1000) shouldBe 1000
        }

        it("should double delay at each attempt") {
            RetryUtils.calculateBackoff(1, 1000) shouldBe 2000
            RetryUtils.calculateBackoff(2, 1000) shouldBe 4000
            RetryUtils.calculateBackoff(3, 1000) shouldBe 8000
        }

        it("should cap at maxDelay") {
            RetryUtils.calculateBackoff(10, 1000, 15000) shouldBe 15000
        }

        it("should use default max of 60s") {
            RetryUtils.calculateBackoff(20, 1000) shouldBe 60_000
        }
    }
})
