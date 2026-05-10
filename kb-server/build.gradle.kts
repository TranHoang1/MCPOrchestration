plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("com.orchestrator.mcp.kb.KbMainKt")
}

dependencies {
    // Shared project modules
    implementation(project(":orchestrator-core"))
    implementation(project(":orchestrator-client"))

    // MCP SDK
    implementation(libs.mcp.sdk.server)
    implementation(libs.kotlinx.io.core)

    // Ktor Client (for LLM API calls, Jira API)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // KotlinX
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // DI
    implementation(libs.koin.core)

    // Database
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    // Logging
    implementation(libs.logback.classic)

    // YAML
    implementation(libs.kaml)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
}

tasks.shadowJar {
    archiveBaseName.set("kb-server")
    archiveClassifier.set("all")
    archiveVersion.set("")
    mergeServiceFiles()
}
