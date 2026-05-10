package com.orchestrator.mcp.segmentation

import com.orchestrator.mcp.segmentation.prompt.SegmentationPromptBuilder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class SegmentationPromptBuilderTest : FunSpec({

    val builder = SegmentationPromptBuilder()

    // UT-18: Prompt Builder Includes Few-Shot Examples
    test("UT-18: includes few-shot examples when enabled") {
        val message = builder.buildUserMessage("test text", includeFewShot = true)
        message shouldContain "Examples:"
        message shouldContain "LEVEL_1"
        message shouldContain "publicContent"
        message shouldContain "technicalContent"
        message shouldContain "businessRules"
    }

    test("excludes few-shot examples when disabled") {
        val message = builder.buildUserMessage("test text", includeFewShot = false)
        message shouldNotContain "Examples:"
        message shouldContain "test text"
    }

    test("includes user text in message") {
        val message = builder.buildUserMessage("my input text", includeFewShot = false)
        message shouldContain "my input text"
        message shouldContain "Classify the following masked text:"
    }

    test("few-shot examples contain at least 3 examples") {
        val message = builder.buildUserMessage("x", includeFewShot = true)
        val inputCount = "Input:".toRegex().findAll(message).count()
        inputCount shouldBeGreaterThanOrEqual 3
    }

    // UT-20: Token Estimation Within Budget
    test("UT-20: prompt with max input stays within token budget") {
        val maxInput = "x".repeat(SegmentationPromptBuilder.MAX_INPUT_LENGTH)
        val message = builder.buildUserMessage(maxInput, includeFewShot = true)
        // Rough token estimate: chars / 4
        val estimatedTokens = message.length / 4
        // System prompt (~800) + user prompt + response budget (~500)
        // Total should be < 4000 tokens for user prompt alone
        estimatedTokens shouldBeLessThan 4000
    }

    test("MAX_INPUT_LENGTH is 10000") {
        SegmentationPromptBuilder.MAX_INPUT_LENGTH shouldBe 10_000
    }
})
