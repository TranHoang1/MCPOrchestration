package com.orchestrator.mcp.credentials.di

import com.orchestrator.mcp.credentials.*
import org.koin.dsl.module

/**
 * Koin DI module for credential management components (MTO-96 + MTO-97 + MTO-98).
 */
val credentialModule = module {
    // MTO-96: Schema CRUD (admin)
    single<CredentialSchemaRepository> { CredentialSchemaRepositoryImpl(get()) }
    single<CredentialSchemaService> { CredentialSchemaServiceImpl(get()) }
    single { CredentialSchemaRoutes(get(), get()) }

    // MTO-97: User Credential CRUD (profile)
    single<UserCredentialRepository> { UserCredentialRepositoryImpl(get()) }
    single<UserCredentialService> { UserCredentialServiceImpl(get(), get(), get()) }
    single { UserCredentialRoutes(get(), get()) }

    // MTO-98: Credential Resolver (placeholder resolution)
    single<CredentialResolver> { CredentialResolverImpl(get(), get()) }
}
