plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("com.orchestrator.mcp.bridge.BridgeApplicationKt")
}

dependencies {
    // Project dependencies
    implementation(project(":orchestrator-core"))

    // Ktor Client (for HTTP Streamable connection to Orchestrator)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // MCP SDK (for stdio server)
    implementation(libs.mcp.sdk.server)
    implementation(libs.kotlinx.io.core)

    // SQLite (for local code intelligence index)
    implementation(libs.sqlite.jdbc)

    // ONNX Runtime (optional — for local embedding fallback)
    compileOnly("com.microsoft.onnxruntime:onnxruntime:1.18.0")

    // KotlinX
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Logging
    implementation(libs.logback.classic)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}

tasks.shadowJar {
    archiveBaseName.set("mcp-bridge")
    archiveClassifier.set("all")
    archiveVersion.set("")
    // Final output: mcp-bridge-all.jar
    mergeServiceFiles()
}
