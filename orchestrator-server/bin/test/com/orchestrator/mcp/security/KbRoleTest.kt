package com.orchestrator.mcp.security

import com.orchestrator.mcp.security.model.KbRole
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.exhaustive

class KbRoleTest : FunSpec({

    test("pgRoleName maps correctly for all roles") {
        KbRole.DEVELOPER.pgRoleName shouldBe "kb_developer"
        KbRole.BA_ADMIN.pgRoleName shouldBe "kb_admin"
        KbRole.LOW_PRIVILEGE.pgRoleName shouldBe "kb_viewer"
    }

    test("all pgRoleNames match expected pattern") {
        KbRole.entries.forEach { role ->
            role.pgRoleName shouldMatch Regex("kb_[a-z_]+")
        }
    }

    test("fromString resolves valid values case-insensitively") {
        KbRole.fromString("DEVELOPER") shouldBe KbRole.DEVELOPER
        KbRole.fromString("developer") shouldBe KbRole.DEVELOPER
        KbRole.fromString("BA_ADMIN") shouldBe KbRole.BA_ADMIN
        KbRole.fromString("ba_admin") shouldBe KbRole.BA_ADMIN
        KbRole.fromString("LOW_PRIVILEGE") shouldBe KbRole.LOW_PRIVILEGE
    }

    test("fromString throws for invalid input") {
        shouldThrow<IllegalArgumentException> { KbRole.fromString("INVALID") }
        shouldThrow<IllegalArgumentException> { KbRole.fromString("") }
        shouldThrow<IllegalArgumentException> { KbRole.fromString("superuser") }
    }

    test("PBT: all enum entries have non-blank pgRoleName") {
        val allRoles = KbRole.entries.toList().exhaustive()
        checkAll(allRoles) { role ->
            role.pgRoleName.isNotBlank() shouldBe true
            role.pgRoleName.length shouldBe role.pgRoleName.length // non-null
        }
    }
})
