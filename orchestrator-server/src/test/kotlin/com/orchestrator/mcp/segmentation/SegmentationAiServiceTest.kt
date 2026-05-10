package com.orchestrator.mcp.segmentation

import com.orchestrator.mcp.segmentation.prompt.SegmentationAiService
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SegmentationAiServiceTest : FunSpec({

    // UT-17: AiService Interface Annotations
    test("UT-17: classify method has @SystemMessage annotation") {
        val method = SegmentationAiService::class.java.getMethod("classify", String::class.java)
        val systemMessage = method.getAnnotation(SystemMessage::class.java)
        systemMessage shouldNotBe null
    }

    test("UT-17: classify method has @UserMessage annotation") {
        val method = SegmentationAiService::class.java.getMethod("classify", String::class.java)
        val userMessage = method.getAnnotation(UserMessage::class.java)
        userMessage shouldNotBe null
        userMessage.value.first() shouldBe "Classify the following masked text:\n\n{{maskedText}}"
    }

    test("UT-17: classify parameter has @V annotation") {
        val method = SegmentationAiService::class.java.getMethod("classify", String::class.java)
        val paramAnnotations = method.parameterAnnotations[0]
        val vAnnotation = paramAnnotations.filterIsInstance<V>().firstOrNull()
        vAnnotation shouldNotBe null
        vAnnotation!!.value shouldBe "maskedText"
    }

    test("SystemMessage contains classification rules") {
        val method = SegmentationAiService::class.java.getMethod("classify", String::class.java)
        val systemMessage = method.getAnnotation(SystemMessage::class.java)
        val content = systemMessage.value.first()
        (content.contains("publicContent") || content.contains("Financial")) shouldBe true
    }
})
