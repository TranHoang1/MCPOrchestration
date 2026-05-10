package com.orchestrator.mcp.kbstore

import com.orchestrator.mcp.kbstore.model.BrSensitivityLevel
import com.orchestrator.mcp.kbstore.model.MappingType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KbStoreModelTest : FunSpec({

    test("BrSensitivityLevel.fromLevel returns correct enum") {
        BrSensitivityLevel.fromLevel(1) shouldBe BrSensitivityLevel.CONFIDENTIAL
        BrSensitivityLevel.fromLevel(2) shouldBe BrSensitivityLevel.INTERNAL
        BrSensitivityLevel.fromLevel(3) shouldBe BrSensitivityLevel.RESTRICTED
    }

    test("BrSensitivityLevel.fromLevel throws for invalid level") {
        shouldThrow<IllegalArgumentException> {
            BrSensitivityLevel.fromLevel(0)
        }
        shouldThrow<IllegalArgumentException> {
            BrSensitivityLevel.fromLevel(4)
        }
    }

    test("BrSensitivityLevel.level returns correct int") {
        BrSensitivityLevel.CONFIDENTIAL.level shouldBe 1
        BrSensitivityLevel.INTERNAL.level shouldBe 2
        BrSensitivityLevel.RESTRICTED.level shouldBe 3
    }

    test("MappingType has all 5 expected values") {
        MappingType.entries.size shouldBe 5
        MappingType.entries.map { it.name } shouldBe listOf(
            "NAME", "ID_CARD", "PHONE", "BANK_ACCOUNT", "EMAIL"
        )
    }
})
