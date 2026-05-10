package com.orchestrator.mcp.sync

import io.kotest.core.annotation.EnabledCondition
import io.kotest.core.spec.Spec
import kotlin.reflect.KClass

/**
 * Kotest condition that enables tests only when Docker is available.
 * Used for Testcontainers-based integration tests.
 */
class DockerAvailableCondition : EnabledCondition {
    override fun enabled(kclass: KClass<out Spec>): Boolean {
        return try {
            org.testcontainers.DockerClientFactory.instance().isDockerAvailable
        } catch (_: Exception) {
            false
        }
    }
}
