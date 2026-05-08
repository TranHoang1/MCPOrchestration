plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("com.orchestrator.mcp.Main")
}

dependencies {
    // Project dependencies
    implementation(project(":orchestrator-core"))
    implementation(project(":orchestrator-client"))

    // MCP SDK
    implementation(libs.mcp.sdk.server)
    implementation(libs.kotlinx.io.core)

    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Ktor Client (for HTTP upstream connections)
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

    // Database
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    // Document Processing (MTO-19: Attachment text extraction)
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

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
    testImplementation("org.testcontainers:postgresql:1.21.1")
    testImplementation(libs.koin.test)
}

tasks.shadowJar {
    archiveBaseName.set("mcp-orchestrator")
    archiveClassifier.set("all")
    archiveVersion.set("")
    // Final output: mcp-orchestrator-all.jar
    mergeServiceFiles()
}

// Keep Ktor fatJar as alternative build option
ktor {
    fatJar {
        archiveFileName.set("mcp-orchestrator-all.jar")
    }
}
