plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.shadow) apply false
    base // Provides 'clean' task for root project
}

allprojects {
    group = "com.orchestrator.mcp"
    version = "1.4.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        // Isolate test classes to prevent Koin/Kotest state pollution
        forkEvery = 100
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Server Bundle — packages JARs + configs + scripts into zip
// Usage: ./gradlew packageServerBundle
// Output: build/distributions/mcp-orchestration-server-{version}.zip
// Source: src/dist/ (NOT TempRelease — that's for manual testing)
// ─────────────────────────────────────────────────────────────
tasks.register<Zip>("packageServerBundle") {
    group = "distribution"
    description = "Package server bundle with JARs, configs, and start scripts"

    dependsOn(":orchestrator-server:shadowJar", ":kb-server:shadowJar")

    archiveBaseName.set("mcp-orchestration-server")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    // Shadow JARs
    from(project(":orchestrator-server").tasks.named("shadowJar"))
    from(project(":kb-server").tasks.named("shadowJar"))

    // Config templates (source of truth: src/dist/config/)
    from("src/dist/config") {
        include(
            ".env.example",
            "application.yml",
            "kb-server.yml",
            "mcp-servers.json"
        )
    }

    // Start scripts (source of truth: src/dist/scripts/)
    from("src/dist/scripts") {
        include("*.cmd", "*.sh", "*.ps1")
    }

    // Documentation (source of truth: src/dist/docs/)
    from("src/dist/docs") {
        include("README-quickstart.md")
    }

    // Ensure .sh files are executable in the zip
    filesMatching("*.sh") {
        mode = 0b111101101 // 755
    }
}
