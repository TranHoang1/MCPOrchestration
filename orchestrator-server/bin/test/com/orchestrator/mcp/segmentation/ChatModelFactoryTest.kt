package com.orchestrator.mcp.segmentation

import com.orchestrator.mcp.segmentation.config.SegmentationConfig
import com.orchestrator.mcp.segmentation.provider.ChatModelFactory
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class ChatModelFactoryTest : FunSpec({

    val factory = ChatModelFactory()

    // UT-06: ChatModelFactory Creates OpenAI Model
    test("UT-06: creates OpenAI model with correct config") {
        val config = SegmentationConfig(
            provider = "openai",
            apiKey = "sk-test-key",
            modelName = "gpt-4o-mini",
            temperature = 0.1,
            maxTokens = 2000,
            timeoutSeconds = 10
        )
        val model = factory.create(config)
        model.shouldBeInstanceOf<OpenAiChatModel>()
    }

    // UT-07: ChatModelFactory Creates Ollama Model
    test("UT-07: creates Ollama model with correct config") {
        val config = SegmentationConfig(
            provider = "ollama",
            ollamaUrl = "http://localhost:11434",
            ollamaModel = "llama3",
            temperature = 0.1,
            timeoutSeconds = 10
        )
        val model = factory.create(config)
        model.shouldBeInstanceOf<OllamaChatModel>()
    }

    // UT-08: ChatModelFactory Rejects Invalid Provider
    test("UT-08: throws IllegalArgumentException for invalid provider") {
        val config = SegmentationConfig(provider = "invalid")
        val ex = shouldThrow<IllegalArgumentException> {
            factory.create(config)
        }
        ex.message shouldContain "Unsupported provider"
        ex.message shouldContain "invalid"
    }

    // Case-insensitive provider matching
    test("handles case-insensitive provider names") {
        val config = SegmentationConfig(
            provider = "OpenAI",
            apiKey = "sk-test",
            timeoutSeconds = 5
        )
        val model = factory.create(config)
        model.shouldBeInstanceOf<OpenAiChatModel>()
    }

    // createLocalModel always creates Ollama
    test("createLocalModel creates Ollama model") {
        val config = SegmentationConfig(
            provider = "openai",
            ollamaUrl = "http://localhost:11434",
            ollamaModel = "llama3",
            timeoutSeconds = 5
        )
        val model = factory.createLocalModel(config)
        model.shouldBeInstanceOf<OllamaChatModel>()
    }
})
