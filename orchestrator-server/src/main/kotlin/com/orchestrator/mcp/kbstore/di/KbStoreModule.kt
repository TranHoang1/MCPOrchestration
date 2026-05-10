package com.orchestrator.mcp.kbstore.di

import com.orchestrator.mcp.kbstore.config.KbStoreConfig
import com.orchestrator.mcp.kbstore.encryption.EncryptionService
import com.orchestrator.mcp.kbstore.encryption.EncryptionServiceImpl
import com.orchestrator.mcp.kbstore.repository.KbEntryRepository
import com.orchestrator.mcp.kbstore.repository.KbEntryRepositoryImpl
import com.orchestrator.mcp.kbstore.repository.PiiMappingRepository
import com.orchestrator.mcp.kbstore.repository.PiiMappingRepositoryImpl
import org.koin.dsl.module

/**
 * Koin DI module for KB Store components.
 * Registers: EncryptionService, KbEntryRepository, PiiMappingRepository.
 *
 * Prerequisites: HikariDataSource and KbStoreConfig must be available in Koin.
 */
val kbStoreModule = module {
    single<EncryptionService> {
        EncryptionServiceImpl(get<KbStoreConfig>().encryptionKey)
    }
    single<KbEntryRepository> {
        KbEntryRepositoryImpl(get(), get())
    }
    single<PiiMappingRepository> {
        PiiMappingRepositoryImpl(get(), get())
    }
}
