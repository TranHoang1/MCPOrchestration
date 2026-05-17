package com.orchestrator.mcp.auth.di

import com.orchestrator.mcp.auth.AuthConfig
import com.orchestrator.mcp.auth.AuthLoginHandler
import com.orchestrator.mcp.auth.AuthMiddleware
import com.orchestrator.mcp.auth.AdminSeeder
import com.orchestrator.mcp.auth.AuthRouteHandler
import com.orchestrator.mcp.auth.BridgeTokenRepository
import com.orchestrator.mcp.auth.BridgeTokenRepositoryImpl
import com.orchestrator.mcp.auth.JwtAuthService
import com.orchestrator.mcp.auth.JwtAuthServiceImpl
import com.orchestrator.mcp.auth.PasswordHashService
import com.orchestrator.mcp.auth.PasswordHashServiceImpl
import com.orchestrator.mcp.auth.sso.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import org.koin.dsl.module

/**
 * Koin DI module for authentication components (MTO-95, MTO-101).
 */
val authModule = module {
    single { AuthConfig() }
    single<PasswordHashService> { PasswordHashServiceImpl() }
    single<JwtAuthService> { JwtAuthServiceImpl(get()) }
    single<BridgeTokenRepository> { BridgeTokenRepositoryImpl(get()) }
    single { AuthMiddleware(get(), get(), get(), get()) }
    single { AuthLoginHandler(get(), get(), get(), get(), get(), get()) }
    single { AuthRouteHandler(get(), get()) }
    single { AdminSeeder(get()) }

    // SSO components (MTO-101)
    single { SsoPkceManager() }
    single { OidcDiscoveryClient(HttpClient(CIO)) }
    single { SsoTokenExchange(HttpClient(CIO), get()) }
    single<SsoConfigRepository> { SsoConfigRepositoryImpl(get()) }
    single<SsoService> { SsoServiceImpl(get(), get(), get(), get(), get(), get(), get()) }
    single { SsoRoutes(get(), get()) }
}
