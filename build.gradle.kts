plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "com.orchestrator.mcp"
version = "1.0.0"

application {
    mainClass.set("com.orchestrator.mcp.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // MCP SDK (official Kotlin MCP implementation)
    implementation(libs.mcp.sdk.server)
    implementation(libs.kotlinx.io.core)

    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Ktor Client (for OpenAI, Qdrant, HTTP upstream)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)

    // KotlinX
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // DI
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    // Logging
    implementation(libs.logback.classic)

    // YAML parsing
    implementation(libs.kaml)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.koin.test)
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

ktor {
    fatJar {
        archiveFileName.set("mcp-orchestrator-all.jar")
    }
}
