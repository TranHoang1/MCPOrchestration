package com.orchestrator.mcp.auth.sso

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveMinLength

/**
 * Unit tests for SsoPkceManager — PKCE code verifier/challenge generation and state management.
 */
class SsoPkceManagerTest : DescribeSpec({

    describe("createAuthRequest") {
        it("generates unique state for each request") {
            val manager = SsoPkceManager()
            val req1 = manager.createAuthRequest()
            val req2 = manager.createAuthRequest()
            req1.state shouldNotBe req2.state
        }

        it("generates code verifier with sufficient length") {
            val manager = SsoPkceManager()
            val req = manager.createAuthRequest()
            req.codeVerifier shouldHaveMinLength 43
        }

        it("generates non-empty code challenge") {
            val manager = SsoPkceManager()
            val req = manager.createAuthRequest()
            req.codeChallenge shouldHaveMinLength 10
        }

        it("code challenge differs from code verifier") {
            val manager = SsoPkceManager()
            val req = manager.createAuthRequest()
            req.codeChallenge shouldNotBe req.codeVerifier
        }
    }

    describe("consumeState") {
        it("returns code verifier for valid state") {
            val manager = SsoPkceManager()
            val req = manager.createAuthRequest()
            val verifier = manager.consumeState(req.state)
            verifier.shouldNotBeNull()
            verifier shouldBe req.codeVerifier
        }

        it("returns null for unknown state") {
            val manager = SsoPkceManager()
            val verifier = manager.consumeState("unknown-state")
            verifier.shouldBeNull()
        }

        it("returns null on second consume (single-use)") {
            val manager = SsoPkceManager()
            val req = manager.createAuthRequest()
            manager.consumeState(req.state).shouldNotBeNull()
            manager.consumeState(req.state).shouldBeNull()
        }
    }
})
