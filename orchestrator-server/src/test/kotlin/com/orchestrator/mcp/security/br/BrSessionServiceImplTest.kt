package com.orchestrator.mcp.security.br

import com.orchestrator.mcp.security.br.model.BrAccessConfig
import com.orchestrator.mcp.security.model.KbRole
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class BrSessionServiceImplTest : FunSpec({

    val config = BrAccessConfig(sessionTimeout = 30.minutes)
    val service = BrSessionServiceImpl(config)

    test("create session returns valid session with correct role") {
        val session = service.create("user@test.com", KbRole.BA_ADMIN)

        session.userId shouldBe "user@test.com"
        session.role shouldBe KbRole.BA_ADMIN
        session.revoked shouldBe false
        session.token.shouldNotBeNull()
    }

    test("validate returns session for valid token") {
        val session = service.create("user@test.com", KbRole.BA_ADMIN)
        val validated = service.validate(session.token)

        validated.shouldNotBeNull()
        validated.userId shouldBe "user@test.com"
    }

    test("validate returns null for unknown token") {
        val result = service.validate("unknown-token")
        result.shouldBeNull()
    }

    test("validate returns null for expired session") {
        val shortConfig = BrAccessConfig(sessionTimeout = 1.milliseconds)
        val shortService = BrSessionServiceImpl(shortConfig)
        val session = shortService.create("user@test.com", KbRole.BA_ADMIN)

        Thread.sleep(10) // Ensure expiry
        val result = shortService.validate(session.token)
        result.shouldBeNull()
    }

    test("revoke marks session as revoked") {
        val session = service.create("user@test.com", KbRole.BA_ADMIN)
        val revoked = service.revoke(session.token)

        revoked shouldBe true
        val validated = service.validate(session.token)
        validated.shouldNotBeNull()
        validated.revoked shouldBe true
    }

    test("revoke returns false for unknown token") {
        val result = service.revoke("unknown-token")
        result shouldBe false
    }
})
