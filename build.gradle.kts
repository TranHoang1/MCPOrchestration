plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.shadow) apply false
    base // Provides 'clean' task for root project
}

allprojects {
    group = "com.orchestrator.mcp"
    version = "1.0.0"

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
