package com.orchestrator.mcp.security

import com.orchestrator.mcp.security.config.RlsConfig
import com.orchestrator.mcp.security.model.KbRole
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class RoleContextServiceImplTest : FunSpec({

    val defaultConfig = RlsConfig()
    val service = RoleContextServiceImpl(defaultConfig)

    test("resolves mapped roles correctly") {
        service.resolveRole("ROLE_DEVELOPER") shouldBe KbRole.DEVELOPER
        service.resolveRole("ROLE_BA") shouldBe KbRole.BA_ADMIN
        service.resolveRole("ROLE_ADMIN") shouldBe KbRole.BA_ADMIN
        service.resolveRole("ROLE_USER") shouldBe KbRole.LOW_PRIVILEGE
    }

    test("returns default role for unmapped identity") {
        service.resolveRole("UNKNOWN_ROLE") shouldBe KbRole.LOW_PRIVILEGE
        service.resolveRole("") shouldBe KbRole.LOW_PRIVILEGE
        service.resolveRole("random_string") shouldBe KbRole.LOW_PRIVILEGE
    }

    test("getDefaultRole returns configured default") {
        service.getDefaultRole() shouldBe KbRole.LOW_PRIVILEGE
    }

    test("custom config with different default role") {
        val customConfig = RlsConfig(defaultRole = KbRole.DEVELOPER)
        val customService = RoleContextServiceImpl(customConfig)
        customService.getDefaultRole() shouldBe KbRole.DEVELOPER
        customService.resolveRole("UNMAPPED") shouldBe KbRole.DEVELOPER
    }

    test("custom config with additional mappings") {
        val customConfig = RlsConfig(
            roleMappings = mapOf("CUSTOM_ROLE" to KbRole.BA_ADMIN)
        )
        val customService = RoleContextServiceImpl(customConfig)
        customService.resolveRole("CUSTOM_ROLE") shouldBe KbRole.BA_ADMIN
    }

    test("PBT: resolveRole never throws for any string input") {
        checkAll(Arb.string(0..100)) { input ->
            val result = service.resolveRole(input)
            // Must always return a valid KbRole
            KbRole.entries.contains(result) shouldBe true
        }
    }
})
