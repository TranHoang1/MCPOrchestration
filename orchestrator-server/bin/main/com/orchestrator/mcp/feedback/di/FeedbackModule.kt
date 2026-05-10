package com.orchestrator.mcp.feedback.di

import com.orchestrator.mcp.feedback.FeedbackService
import com.orchestrator.mcp.feedback.FeedbackServiceImpl
import com.orchestrator.mcp.feedback.model.FeedbackConfig
import com.orchestrator.mcp.feedback.repository.FeedbackRepository
import com.orchestrator.mcp.feedback.repository.FeedbackRepositoryImpl
import org.koin.dsl.module

/**
 * Koin DI module for Feedback & Correction (MTO-37).
 */
val feedbackModule = module {
    single { FeedbackConfig() }

    single<FeedbackRepository> {
        FeedbackRepositoryImpl(get())
    }

    single<FeedbackService> {
        FeedbackServiceImpl(get(), get())
    }
}
