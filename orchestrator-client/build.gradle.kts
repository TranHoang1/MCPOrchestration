plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    // Project dependencies
    implementation(project(":orchestrator-core"))

    // Ktor Client (for OpenAI, Qdrant, HTTP upstream)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)

    // KotlinX
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.io.core)

    // DI
    implementation(libs.koin.core)

    // Logging
    implementation(libs.logback.classic)

    // Database
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    // MCP SDK (for upstream connections)
    implementation(libs.mcp.sdk.server)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.koin.test)
}
