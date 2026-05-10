package com.orchestrator.mcp.ocr.di

import com.orchestrator.mcp.ocr.OcrService
import com.orchestrator.mcp.ocr.OcrServiceImpl
import com.orchestrator.mcp.ocr.extractor.ImageTextExtractor
import com.orchestrator.mcp.ocr.model.OcrConfig
import org.koin.dsl.module

/**
 * Koin DI module for OCR components.
 */
val ocrModule = module {

    single { OcrConfig() }

    single<OcrService> {
        OcrServiceImpl(
            dispatcher = get(),
            config = get()
        )
    }

    single { ImageTextExtractor(ocrService = get()) }
}
