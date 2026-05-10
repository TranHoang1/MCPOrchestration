package com.orchestrator.mcp.queue.di

import com.orchestrator.mcp.queue.*
import com.orchestrator.mcp.queue.config.QueueConfig
import com.orchestrator.mcp.queue.repository.TaskStateRepository
import com.orchestrator.mcp.queue.repository.TaskStateRepositoryImpl
import org.koin.dsl.module

/**
 * Koin DI module for the dual-priority queue system.
 * Registers all queue components as singletons.
 */
val queueModule = module {
    // Configuration
    single { QueueConfig() }

    // Repository
    single<TaskStateRepository> { TaskStateRepositoryImpl(get()) }

    // Queue infrastructure
    single { DualPriorityQueue(get()) }

    // Task handlers registry (empty by default, populated by feature modules)
    single<Map<String, TaskHandler>> { emptyMap() }

    // Services
    single<QueueService> { QueueServiceImpl(get(), get(), get()) }
    single { QueueWorker(get(), get(), get(), get()) }
    single { QueueWatchdog(get(), get()) }
    single { CrashRecoveryService(get(), get()) }
}
