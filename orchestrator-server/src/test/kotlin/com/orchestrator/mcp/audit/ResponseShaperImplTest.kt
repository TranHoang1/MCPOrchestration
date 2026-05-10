package com.orchestrator.mcp.audit

import com.orchestrator.mcp.security.model.KbRole
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ResponseShaperImplTest : FunSpec({

    val shaper = ResponseShaperImpl()

    test("BA_ADMIN sees full business_rules") {
        val result = shaper.shape(KbRole.BA_ADMIN, "business_rules", "Approval > 50k needs VP sign-off")
        result shouldBe "Approval > 50k needs VP sign-off"
    }

    test("DEVELOPER sees masked business_rules") {
        val result = shaper.shape(KbRole.DEVELOPER, "business_rules", "Approval > 50k needs VP sign-off")
        result shouldBe "[BR_MASKED]"
    }

    test("LOW_PRIVILEGE sees null for business_rules") {
        val result = shaper.shape(KbRole.LOW_PRIVILEGE, "business_rules", "Approval > 50k")
        result.shouldBeNull()
    }

    test("BA_ADMIN sees full pii_original") {
        val result = shaper.shape(KbRole.BA_ADMIN, "pii_original", "john.doe@company.com")
        result shouldBe "john.doe@company.com"
    }

    test("DEVELOPER sees masked pii_original") {
        val result = shaper.shape(KbRole.DEVELOPER, "pii_original", "john.doe@company.com")
        result shouldBe "[PII_MASKED]"
    }

    test("LOW_PRIVILEGE sees null for pii_original") {
        val result = shaper.shape(KbRole.LOW_PRIVILEGE, "pii_original", "john.doe@company.com")
        result.shouldBeNull()
    }

    test("BA_ADMIN sees audit_logs") {
        val result = shaper.shape(KbRole.BA_ADMIN, "audit_logs", "some audit data")
        result shouldBe "some audit data"
    }

    test("DEVELOPER cannot see audit_logs") {
        val result = shaper.shape(KbRole.DEVELOPER, "audit_logs", "some audit data")
        result.shouldBeNull()
    }

    test("LOW_PRIVILEGE sees truncated content") {
        val longContent = "A".repeat(500)
        val result = shaper.shape(KbRole.LOW_PRIVILEGE, "content", longContent)
        result?.length shouldBe 200
    }

    test("DEVELOPER sees full content") {
        val longContent = "A".repeat(500)
        val result = shaper.shape(KbRole.DEVELOPER, "content", longContent)
        result?.length shouldBe 500
    }

    test("shapeMap filters null values for non-admin roles") {
        val data = mapOf(
            "business_rules" to "secret",
            "content" to "public info",
            "pii_original" to "email@test.com"
        )
        val result = shaper.shapeMap(KbRole.DEVELOPER, data)

        result["business_rules"] shouldBe "[BR_MASKED]"
        result["content"] shouldBe "public info"
        result["pii_original"] shouldBe "[PII_MASKED]"
    }
})
