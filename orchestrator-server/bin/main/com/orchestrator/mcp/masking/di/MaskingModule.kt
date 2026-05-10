package com.orchestrator.mcp.masking.di

import com.orchestrator.mcp.masking.PiiMaskingEngine
import com.orchestrator.mcp.masking.PiiMaskingEngineImpl
import com.orchestrator.mcp.masking.config.MaskingConfig
import com.orchestrator.mcp.masking.strategy.BankAccountDetectionStrategy
import com.orchestrator.mcp.masking.strategy.EmailDetectionStrategy
import com.orchestrator.mcp.masking.strategy.IdCardDetectionStrategy
import com.orchestrator.mcp.masking.strategy.NameDetectionStrategy
import com.orchestrator.mcp.masking.strategy.PhoneDetectionStrategy
import com.orchestrator.mcp.masking.strategy.PiiDetectionStrategy
import org.koin.dsl.module

/**
 * Koin DI module for PII Masking Engine.
 * Registers all strategies and the engine implementation.
 */
val maskingModule = module {

    single<MaskingConfig> { MaskingConfig() }

    single<List<PiiDetectionStrategy>> {
        listOf(
            EmailDetectionStrategy(),
            PhoneDetectionStrategy(),
            BankAccountDetectionStrategy(get()),
            IdCardDetectionStrategy(),
            NameDetectionStrategy(get())
        )
    }

    single<PiiMaskingEngine> {
        PiiMaskingEngineImpl(
            strategies = get(),
            config = get()
        )
    }
}
