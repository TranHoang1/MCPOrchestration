# Technical Design Document (TDD)

## MCP Orchestration — MTO-99: [P5] Process Pool Manager — Scalable Process Pool wrapping UpstreamServerManager

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-99 |
| Title | [P5] Process Pool Manager — Scalable Process Pool wrapping UpstreamServerManager |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-17 |
| Status | Draft |
| Related BRD | (Jira ticket description serves as BRD) |
| Related FSD | (Jira ticket description serves as FSD) |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | SA Agent – Solution Architect | Create document |
| Peer Reviewer | TA Agent – Technical Analyst | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-17 | SA Agent | Initial TDD — Process Pool Manager architecture and implementation design |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm the technical design in this TDD |
| | ☐ I agree and confirm the technical design in this TDD |

---

## 1. Introduction

> **Scope Boundary:** This TDD specifies HOW to implement the Process Pool Manager that wraps the existing UpstreamServerManager. It focuses on technology choices, architecture decisions, implementation patterns, and deployment concerns.

### 1.1 Purpose

Design a scalable process pool that manages upstream MCP server processes, pooled by credential hash rather than per-user. The ProcessPoolManager wraps the existing `UpstreamServerManager` to provide connection pooling, auto-scaling, health monitoring, and graceful lifecycle management.

### 1.2 Scope

- New `ProcessPoolManager` interface and implementation in `orchestrator-client` module
- Pool key strategy: `hash(serverName + resolvedCredentials)`
- Auto-scale up/down based on load and idle timeout
- Health check integration with existing `HealthMonitor`
- Admin API endpoints for pool monitoring
- Configuration via YAML (`processPool` section)

### 1.3 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.3.20 |
| Platform | JVM | 21 |
| Framework | Ktor (Netty) | 3.4.0 |
| Concurrency | kotlinx.coroutines | 1.10.2 |
| DI | Koin | 4.1.1 |
| Serialization | kotlinx.serialization | 1.8.1 |
| Config | kaml (YAML) | 0.77.0 |
| Logging | Logback | 1.5.18 |
| Testing | JUnit 5 + Kotest + MockK + Testcontainers | latest |

### 1.4 Design Principles

- **Decorator Pattern**: ProcessPoolManager wraps UpstreamServerManager without modifying it
- **Single Responsibility**: Pool management separate from connection management
- **Coroutine-first**: All async operations use structured concurrency
- **Fail-fast with graceful degradation**: Acquire timeout prevents indefinite waiting
- **Observable**: All pool operations emit metrics for monitoring

### 1.5 Constraints

- Must be backward-compatible with existing `UpstreamServerManager` interface
- Must not break existing single-connection mode (pool disabled = passthrough)
- Max memory overhead per idle process: ~50MB (JVM process)
- Pool state must survive brief network hiccups (don't kill processes on transient errors)
- Must integrate with existing Koin DI module without breaking other modules

### 1.6 References

| Document | Location |
|----------|----------|
| Jira Ticket | MTO-99 (description contains full requirements) |
| UpstreamServerManager | orchestrator-client/src/main/kotlin/.../upstream/UpstreamServerManager.kt |
| HealthMonitor | orchestrator-client/src/main/kotlin/.../upstream/HealthMonitor.kt |
| OrchestratorConfig | orchestrator-core/src/main/kotlin/.../config/OrchestratorConfig.kt |

---

## 2. System Architecture

### 2.1 Architecture Overview

The Process Pool Manager sits between the orchestrator-server's execution layer and the existing UpstreamServerManager. It intercepts connection requests and routes them through a pool keyed by `hash(serverName + credentialHash)`.

![Architecture Diagram](diagrams/architecture.png)

*[Edit in draw.io](diagrams/architecture.drawio)*

### 2.2 Component Diagram

![Component Diagram](diagrams/component.png)

*[Edit in draw.io](diagrams/component.drawio)*

| Component | Responsibility | Technology |
|-----------|---------------|------------|
| ProcessPoolManager | Pool lifecycle, acquire/release, scaling decisions | Kotlin coroutines + Mutex |
| PoolEntry | Single pooled process instance with state tracking | Data class + AtomicReference |
| PoolKey | Immutable key: hash(serverName + credentialHash) | Value class |
| ScalingPolicy | Decide when to scale up/down | Strategy pattern |
| PoolHealthChecker | Periodic health checks on pooled instances | Coroutine job |
| PoolMetrics | Collect and expose pool metrics | AtomicLong counters |
| PoolAdminRoutes | Admin API for pool monitoring | Ktor routes |

### 2.3 Deployment Architecture

No separate deployment — ProcessPoolManager is part of `orchestrator-server` fat JAR. Configuration via `application.yml`.

### 2.4 Communication Patterns

| From | To | Protocol | Pattern | Description |
|------|----|----------|---------|-------------|
| ExecutionDispatcher | ProcessPoolManager | In-process | Sync (suspend) | Acquire connection for tool execution |
| ProcessPoolManager | UpstreamServerManager | In-process | Sync (suspend) | Spawn/connect new process |
| PoolHealthChecker | PoolEntry | In-process | Async (coroutine) | Periodic ping |
| PoolAdminRoutes | ProcessPoolManager | In-process | Sync (suspend) | Query pool state |
| Admin UI | PoolAdminRoutes | HTTP/JSON | Request-Response | Monitor pool status |

---

## 3. API Design

> **Prerequisite:** This section defines the Admin API for pool monitoring and management.

### 3.1 API Overview

| # | Endpoint | Method | Description | Source |
|---|----------|--------|-------------|--------|
| 1 | /admin/pool/status | GET | Get all pool statuses | AC7 |
| 2 | /admin/pool/{serverName} | GET | Get pool status for specific server | AC7 |
| 3 | /admin/pool/{serverName}/scale | POST | Manual scale up/down | AC7 |
| 4 | /admin/pool/{serverName}/drain | POST | Drain pool (graceful shutdown) | AC8 |
| 5 | /admin/pool/metrics | GET | Get pool metrics | AC7 |

---

### 3.2 API: Get All Pool Statuses

| Attribute | Value |
|-----------|-------|
| Method | GET |
| Path | /admin/pool/status |
| Auth | Admin role required |
| Rate Limit | 60/min |

**Response — 200 OK:**

```json
{
  "pools": [
    {
      "poolKey": "server1#a1b2c3d4",
      "serverName": "server1",
      "credentialHash": "a1b2c3d4",
      "instances": {
        "total": 3,
        "active": 1,
        "idle": 1,
        "warming": 1
      },
      "metrics": {
        "avgResponseTimeMs": 245,
        "requestsPerMinute": 12,
        "queueDepth": 0
      },
      "config": {
        "maxInstances": 5,
        "idleTimeoutMs": 300000
      }
    }
  ],
  "global": {
    "totalInstances": 5,
    "maxTotalInstances": 20,
    "utilizationPercent": 25.0
  }
}
```

### 3.3 API: Manual Scale

| Attribute | Value |
|-----------|-------|
| Method | POST |
| Path | /admin/pool/{serverName}/scale |
| Auth | Admin role required |

**Request Body:**

```json
{
  "credentialHash": "a1b2c3d4",
  "action": "scale_up",
  "count": 1
}
```

**Response — 200 OK:**

```json
{
  "success": true,
  "message": "Scaled up 1 instance for server1#a1b2c3d4",
  "currentInstances": 4
}
```

**Error Responses:**

| Status | Code | Message | Description |
|--------|------|---------|-------------|
| 400 | POOL_MAX_REACHED | Max instances reached | Cannot scale beyond maxInstancesPerServer |
| 404 | POOL_NOT_FOUND | Pool not found | No pool exists for this server+credential |
| 503 | POOL_UNAVAILABLE | Pool manager unavailable | Pool manager is shutting down |

---

## 4. Database Design

> No database changes required. Pool state is entirely in-memory (ephemeral). Pool metrics can optionally be persisted to existing `audit_log` table for historical analysis.

### 4.1 In-Memory State

Pool state is managed via `ConcurrentHashMap` and coroutine-safe data structures. No persistent storage needed because:
- Pool instances are processes — they don't survive server restart anyway
- Metrics are real-time only (historical metrics via existing monitoring infrastructure)
- Configuration is in YAML (not DB)

### 4.2 Optional: Metrics Persistence

If historical metrics are needed, emit to existing `audit_log` table:

```sql
-- No new tables needed. Use existing audit_log:
INSERT INTO audit_log (event_type, event_data, created_at)
VALUES ('pool_metrics', '{"serverName":"...","instances":3,"avgResponseMs":245}', NOW());
```

---

## 5. Class / Module Design

### 5.1 Package Structure

```
com.orchestrator.mcp.client.upstream.pool/
├── ProcessPoolManager.kt          # Interface — pool lifecycle
├── ProcessPoolManagerImpl.kt      # Implementation — core pool logic
├── PoolEntry.kt                   # Single pooled instance state
├── PoolKey.kt                     # Value class — pool key (serverName + credHash)
├── PoolConfig.kt                  # @Serializable config data class
├── PoolMetrics.kt                 # Metrics collection
├── PoolHealthChecker.kt           # Health check coroutine
├── ScalingPolicy.kt               # Interface — scaling decisions
├── DefaultScalingPolicy.kt        # Default: response-time based scaling
└── PoolExceptions.kt              # Pool-specific exceptions
```

Admin routes (in orchestrator-server):
```
com.orchestrator.mcp.pool/
├── PoolAdminRoutes.kt             # Ktor routes for /admin/pool/*
└── PoolAdminService.kt            # Service layer for admin operations
```

### 5.2 Key Interfaces

```kotlin
/**
 * Manages a pool of upstream MCP server processes, keyed by credential hash.
 * Wraps UpstreamServerManager to provide connection pooling and auto-scaling.
 */
interface ProcessPoolManager {
    /**
     * Acquire a connection from the pool for the given server and credentials.
     * If no idle instance available, may spawn new (if under max) or wait (with timeout).
     *
     * @param serverName Name of the upstream server
     * @param credentialHash Hash of resolved credentials (same creds = shared process)
     * @return PooledConnection that MUST be released after use
     * @throws PoolAcquireTimeoutException if timeout exceeded
     * @throws PoolExhaustedException if at max capacity and cannot wait
     */
    suspend fun acquire(serverName: String, credentialHash: String): PooledConnection

    /**
     * Release a connection back to the pool.
     * Connection becomes available for other requests.
     */
    suspend fun release(connection: PooledConnection)

    /**
     * Get current pool status for all pools.
     */
    fun getPoolStatus(): List<PoolStatus>

    /**
     * Get pool status for a specific server.
     */
    fun getPoolStatus(serverName: String): List<PoolStatus>

    /**
     * Graceful shutdown: drain all pools, wait for active requests, then kill.
     */
    suspend fun shutdown()

    /**
     * Start the pool manager (warmup instances, start health checker).
     */
    suspend fun start()
}

/**
 * A pooled connection wrapper that tracks usage and must be released.
 */
data class PooledConnection(
    val connection: McpConnection,
    val poolKey: PoolKey,
    val instanceId: String,
    val acquiredAt: kotlinx.datetime.Instant
)

/**
 * Pool status DTO for monitoring.
 */
data class PoolStatus(
    val poolKey: PoolKey,
    val serverName: String,
    val credentialHash: String,
    val totalInstances: Int,
    val activeInstances: Int,
    val idleInstances: Int,
    val warmingInstances: Int,
    val avgResponseTimeMs: Long,
    val requestsPerMinute: Double,
    val queueDepth: Int
)
```

### 5.3 Core Implementation Design

```kotlin
/**
 * Pool key — immutable identifier for a pool.
 * Same serverName + same credentials = same pool.
 */
@JvmInline
value class PoolKey(val value: String) {
    companion object {
        fun of(serverName: String, credentialHash: String): PoolKey =
            PoolKey("$serverName#$credentialHash")
    }
}

/**
 * State of a single pooled instance.
 */
enum class InstanceState {
    WARMING,    // Being created/initialized
    IDLE,       // Ready for use
    ACTIVE,     // Currently handling a request
    DRAINING,   // Finishing current request, will be killed after
    DEAD        // Failed health check, pending removal
}

/**
 * A single pooled process instance.
 */
data class PoolEntry(
    val instanceId: String,
    val poolKey: PoolKey,
    val connection: McpConnection,
    val state: AtomicReference<InstanceState> = AtomicReference(InstanceState.WARMING),
    val createdAt: Instant = Clock.System.now(),
    val lastUsedAt: AtomicReference<Instant> = AtomicReference(Clock.System.now()),
    val requestCount: AtomicLong = AtomicLong(0),
    val totalResponseTimeMs: AtomicLong = AtomicLong(0)
) {
    val avgResponseTimeMs: Long
        get() {
            val count = requestCount.get()
            return if (count == 0L) 0L else totalResponseTimeMs.get() / count
        }
}
```

### 5.4 Acquire/Release Flow (Pseudocode)

```kotlin
// ProcessPoolManagerImpl.acquire()
suspend fun acquire(serverName: String, credentialHash: String): PooledConnection {
    val key = PoolKey.of(serverName, credentialHash)
    
    // 1. Try to get idle instance from pool
    val idle = pools[key]?.findIdleInstance()
    if (idle != null) {
        idle.state.set(InstanceState.ACTIVE)
        idle.lastUsedAt.set(Clock.System.now())
        metrics.recordAcquire(key, fromIdle = true)
        return PooledConnection(idle.connection, key, idle.instanceId, Clock.System.now())
    }
    
    // 2. If under max, spawn new instance
    val pool = pools.getOrPut(key) { PoolBucket(key) }
    if (pool.size < config.maxInstancesPerServer && globalCount.get() < config.maxTotalInstances) {
        val entry = spawnInstance(key, serverName, credentialHash)
        entry.state.set(InstanceState.ACTIVE)
        metrics.recordAcquire(key, fromIdle = false)
        return PooledConnection(entry.connection, key, entry.instanceId, Clock.System.now())
    }
    
    // 3. At max — wait with timeout
    val acquired = waitChannel[key]?.receive(config.acquireTimeoutMs)
        ?: throw PoolAcquireTimeoutException(key, config.acquireTimeoutMs)
    
    acquired.state.set(InstanceState.ACTIVE)
    metrics.recordAcquire(key, fromIdle = true, waited = true)
    return PooledConnection(acquired.connection, key, acquired.instanceId, Clock.System.now())
}

// ProcessPoolManagerImpl.release()
suspend fun release(connection: PooledConnection) {
    val entry = findEntry(connection.poolKey, connection.instanceId)
        ?: return // Instance was killed while active (health check failure)
    
    val responseTimeMs = Clock.System.now().toEpochMilliseconds() - connection.acquiredAt.toEpochMilliseconds()
    entry.requestCount.incrementAndGet()
    entry.totalResponseTimeMs.addAndGet(responseTimeMs)
    entry.lastUsedAt.set(Clock.System.now())
    
    // Check if someone is waiting for this pool
    val waiter = waitChannel[connection.poolKey]
    if (waiter != null && !waiter.isEmpty) {
        // Don't set to IDLE — pass directly to waiter
        waiter.send(entry)
    } else {
        entry.state.set(InstanceState.IDLE)
    }
    
    metrics.recordRelease(connection.poolKey, responseTimeMs)
    
    // Trigger scale-up check if response time is slow
    if (responseTimeMs > config.slowResponseThresholdMs) {
        scalingPolicy.onSlowResponse(connection.poolKey, responseTimeMs)
    }
}
```

### 5.5 Scaling Policy

```kotlin
interface ScalingPolicy {
    /**
     * Called when a response is slow. May trigger scale-up.
     */
    suspend fun onSlowResponse(poolKey: PoolKey, responseTimeMs: Long)
    
    /**
     * Called periodically. May trigger scale-down for idle instances.
     */
    suspend fun evaluateScaleDown(pools: Map<PoolKey, List<PoolEntry>>): List<PoolEntry>
}

class DefaultScalingPolicy(private val config: PoolConfig) : ScalingPolicy {
    override suspend fun onSlowResponse(poolKey: PoolKey, responseTimeMs: Long) {
        // Scale up if avg response time > threshold AND under max
        // Debounce: don't scale up more than once per 30s per pool
    }
    
    override suspend fun evaluateScaleDown(pools: Map<PoolKey, List<PoolEntry>>): List<PoolEntry> {
        // Kill instances idle longer than idleTimeoutMs
        // Keep at least warmupInstances per pool
        return pools.flatMap { (_, entries) ->
            entries.filter { entry ->
                entry.state.get() == InstanceState.IDLE &&
                entry.lastUsedAt.get().plus(config.idleTimeoutMs.milliseconds) < Clock.System.now() &&
                entries.count { it.state.get() != InstanceState.DEAD } > config.warmupInstances
            }
        }
    }
}
```

### 5.6 Design Patterns

| Pattern | Where Used | Rationale |
|---------|-----------|-----------|
| Decorator | ProcessPoolManager wraps UpstreamServerManager | Add pooling without modifying existing code |
| Strategy | ScalingPolicy interface | Allow different scaling algorithms |
| Object Pool | PoolBucket manages PoolEntry instances | Reuse expensive process instances |
| Producer-Consumer | Channel-based wait queue | Fair ordering for waiting requests |
| State Machine | InstanceState enum | Clear lifecycle transitions |
| Value Object | PoolKey value class | Immutable, type-safe pool identifier |

### 5.7 Error Handling

| Exception | HTTP Status | Error Code | When Thrown |
|-----------|-------------|------------|------------|
| PoolAcquireTimeoutException | 503 | POOL_ACQUIRE_TIMEOUT | Waited acquireTimeoutMs, no instance available |
| PoolExhaustedException | 503 | POOL_EXHAUSTED | At max total instances, cannot spawn |
| PoolShuttingDownException | 503 | POOL_SHUTTING_DOWN | Pool is draining, not accepting new requests |
| PoolInstanceDeadException | 500 | POOL_INSTANCE_DEAD | Acquired instance failed during use |

---

## 6. Integration Design

### 6.1 Integration with UpstreamServerManager

| Attribute | Value |
|-----------|-------|
| Protocol | In-process (direct method call) |
| Pattern | Decorator — ProcessPoolManager delegates to UpstreamServerManager for actual connection creation |
| Timeout | Per-connection: inherited from UpstreamServerManager config |
| Retry Policy | On spawn failure: 2 retries with 1s backoff |

**Sequence: Acquire → Execute → Release**

```
Client → ProcessPoolManager.acquire(server, credHash)
    ProcessPoolManager → Pool: find idle instance
    [if idle found] → return PooledConnection
    [if no idle, under max] → UpstreamServerManager.connect(server) → new PoolEntry → return
    [if at max] → wait on Channel with timeout → return or throw

Client → PooledConnection.connection.sendRequest(method, params)
    (direct MCP call to upstream process)

Client → ProcessPoolManager.release(pooledConnection)
    ProcessPoolManager → update metrics, set IDLE or pass to waiter
```

![API Sequence](diagrams/api-sequence-acquire-release.png)

### 6.2 Integration with HealthMonitor

The existing `HealthMonitor` checks `UpstreamServerManager.getAllServerStates()`. ProcessPoolManager adds a `PoolHealthChecker` that:
1. Pings each IDLE instance every `healthCheckIntervalMs`
2. Marks DEAD instances that fail ping
3. Removes DEAD instances from pool
4. Reports to existing metrics infrastructure

### 6.3 Integration with Koin DI

```kotlin
// In AppModule.kt — add pool manager binding
single<ProcessPoolManager> {
    val config = get<OrchestratorConfig>()
    if (config.processPool.enabled) {
        ProcessPoolManagerImpl(
            config = config.processPool,
            serverManager = get<UpstreamServerManager>(),
            scalingPolicy = DefaultScalingPolicy(config.processPool)
        )
    } else {
        // Passthrough — no pooling, direct connection
        PassthroughPoolManager(get<UpstreamServerManager>())
    }
}
```

---

## 7. Security Design

### 7.1 Credential Isolation

- Pool key includes credential hash → different credentials NEVER share a process
- Credential hash is one-way (SHA-256) → cannot reverse to get actual credentials
- Credentials are never stored in pool state — only the hash for keying

### 7.2 Admin API Access

| Role | Endpoints | Permissions |
|------|-----------|-------------|
| Admin | /admin/pool/* | Full access (read + scale + drain) |
| User | None | No direct pool access (transparent) |

### 7.3 Process Isolation

- Each pooled process runs in its own OS process (stdio transport)
- Process environment variables contain credentials — isolated per process
- Process crash does not affect other pool instances

---

## 8. Performance & Scalability

### 8.1 Performance Targets

| Operation | Target | Measurement |
|-----------|--------|-------------|
| acquire() — idle hit | < 1ms | Time from call to return |
| acquire() — spawn new | < 5s | Time including process startup |
| acquire() — wait queue | < 30s (configurable) | acquireTimeoutMs |
| release() | < 1ms | Time to return to pool |
| Health check cycle | < 100ms per instance | Ping round-trip |

### 8.2 Connection Pooling

| Resource | Min (warmup) | Max Per Server | Max Total | Idle Timeout |
|----------|-------------|----------------|-----------|-------------|
| Process instances | 1 (warmupInstances) | 5 (maxInstancesPerServer) | 20 (maxTotalInstances) | 300000ms (5min) |

### 8.3 Memory Impact

| Instances | Estimated Memory | Notes |
|-----------|-----------------|-------|
| 1 (warmup) | ~50MB | Single JVM/Node process |
| 5 (per server max) | ~250MB | 5 processes for one server |
| 20 (total max) | ~1GB | All pools combined |

### 8.4 Scaling Triggers

| Trigger | Action | Cooldown |
|---------|--------|----------|
| avgResponseTime > slowResponseThresholdMs (10s) | Scale up +1 | 30s per pool |
| Instance idle > idleTimeoutMs (5min) | Scale down -1 | Immediate (keep warmup) |
| Health check fail (3 consecutive) | Mark DEAD, remove | Immediate |

---

## 9. Monitoring & Observability

### 9.1 Logging

| Log Event | Level | Fields | Destination |
|-----------|-------|--------|-------------|
| Instance spawned | INFO | poolKey, instanceId, totalInstances | stdout/logback |
| Instance killed (idle) | INFO | poolKey, instanceId, idleDuration | stdout/logback |
| Instance killed (dead) | WARN | poolKey, instanceId, failReason | stdout/logback |
| Scale up triggered | INFO | poolKey, reason, newCount | stdout/logback |
| Scale down triggered | INFO | poolKey, reason, newCount | stdout/logback |
| Acquire timeout | WARN | poolKey, waitDuration, queueDepth | stdout/logback |
| Pool exhausted | ERROR | poolKey, maxInstances, queueDepth | stdout/logback |

### 9.2 Metrics

| Metric | Type | Description | Alert Threshold |
|--------|------|-------------|-----------------|
| pool_instances_total | Gauge | Total instances across all pools | > 18 (90% of max) |
| pool_instances_active | Gauge | Currently active instances | — |
| pool_instances_idle | Gauge | Currently idle instances | — |
| pool_acquire_duration_ms | Histogram | Time to acquire connection | p95 > 5000ms |
| pool_request_duration_ms | Histogram | Time connection was held | p95 > 10000ms |
| pool_queue_depth | Gauge | Requests waiting for instance | > 5 |
| pool_spawn_count | Counter | Total instances spawned | — |
| pool_kill_count | Counter | Total instances killed | — |
| pool_timeout_count | Counter | Acquire timeouts | > 0 per minute |
| pool_utilization_percent | Gauge | active / total * 100 | > 80% |

### 9.3 Health Checks

| Endpoint | Checks | Expected Response |
|----------|--------|-------------------|
| /health | Pool manager running, at least 1 instance per warmup server | 200 OK |
| /admin/pool/status | Detailed pool state | 200 OK with JSON |

---

## 10. Deployment Considerations

### 10.1 Configuration

```yaml
processPool:
  enabled: true
  maxInstancesPerServer: 5
  maxTotalInstances: 20
  idleTimeoutMs: 300000        # 5 minutes
  slowResponseThresholdMs: 10000  # 10 seconds
  acquireTimeoutMs: 30000      # 30 seconds
  healthCheckIntervalMs: 60000  # 1 minute
  warmupInstances: 1           # Pre-spawn per server on startup
  scaleUpCooldownMs: 30000     # 30 seconds between scale-ups per pool
  healthCheckMaxFailures: 3    # Consecutive failures before marking DEAD
```

### 10.2 Feature Flags

| Flag | Default | Description |
|------|---------|-------------|
| processPool.enabled | false | Enable/disable pool manager (false = passthrough mode) |

### 10.3 Rollback Strategy

1. Set `processPool.enabled: false` in application.yml
2. Restart server — falls back to PassthroughPoolManager (direct connections)
3. No data migration needed (pool state is ephemeral)

### 10.4 Graceful Shutdown Sequence

```
1. Stop accepting new acquire() requests (throw PoolShuttingDownException)
2. Wait for all ACTIVE instances to be released (max 60s)
3. Kill all remaining instances (IDLE + DRAINING)
4. Cancel health checker coroutine
5. Report final metrics
```

---

## 11. Implementation Checklist

### Files to Create

| # | File | Module | Purpose |
|---|------|--------|---------|
| 1 | `ProcessPoolManager.kt` | orchestrator-client | Interface |
| 2 | `ProcessPoolManagerImpl.kt` | orchestrator-client | Core implementation |
| 3 | `PassthroughPoolManager.kt` | orchestrator-client | No-op passthrough when disabled |
| 4 | `PoolEntry.kt` | orchestrator-client | Instance state data class |
| 5 | `PoolKey.kt` | orchestrator-client | Value class for pool key |
| 6 | `PoolConfig.kt` | orchestrator-core | @Serializable config |
| 7 | `ScalingPolicy.kt` | orchestrator-client | Scaling strategy interface |
| 8 | `DefaultScalingPolicy.kt` | orchestrator-client | Default implementation |
| 9 | `PoolHealthChecker.kt` | orchestrator-client | Health check coroutine |
| 10 | `PoolMetrics.kt` | orchestrator-client | Metrics collection |
| 11 | `PoolExceptions.kt` | orchestrator-client | Custom exceptions |
| 12 | `PoolAdminRoutes.kt` | orchestrator-server | Admin API routes |
| 13 | `PoolAdminService.kt` | orchestrator-server | Admin service layer |

### Files to Modify

| # | File | Change |
|---|------|--------|
| 1 | `OrchestratorConfig.kt` | Add `processPool: PoolConfig` section |
| 2 | `AppModule.kt` | Add Koin binding for ProcessPoolManager |
| 3 | `ExecutionDispatcher.kt` (or equivalent) | Use ProcessPoolManager.acquire/release instead of direct getConnection |
| 4 | `application.yml` | Add processPool config section |

### Implementation Order

1. `PoolConfig.kt` + `PoolKey.kt` + `PoolEntry.kt` (data models)
2. `PoolExceptions.kt` (exceptions)
3. `ProcessPoolManager.kt` interface
4. `PassthroughPoolManager.kt` (simple passthrough)
5. `ScalingPolicy.kt` + `DefaultScalingPolicy.kt`
6. `PoolMetrics.kt`
7. `PoolHealthChecker.kt`
8. `ProcessPoolManagerImpl.kt` (core logic)
9. `PoolAdminRoutes.kt` + `PoolAdminService.kt`
10. Modify `OrchestratorConfig.kt` + `AppModule.kt`
11. Modify execution layer to use pool
12. Tests

---

## 12. Appendix

### Glossary

| Term | Definition |
|------|------------|
| Pool Key | Unique identifier for a pool: hash(serverName + credentialHash) |
| Credential Hash | SHA-256 hash of resolved user credentials for an upstream server |
| Warmup Instance | Pre-spawned instance on startup to reduce first-request latency |
| Acquire | Request a connection from the pool |
| Release | Return a connection to the pool after use |
| Drain | Gracefully stop a pool — finish active requests, then kill all |

### State Machine: Instance Lifecycle

```
WARMING ──(init success)──▶ IDLE
WARMING ──(init failure)──▶ DEAD

IDLE ──(acquired)──▶ ACTIVE
IDLE ──(idle timeout)──▶ [killed]
IDLE ──(health fail x3)──▶ DEAD

ACTIVE ──(released)──▶ IDLE
ACTIVE ──(released + drain)──▶ DRAINING ──▶ [killed]
ACTIVE ──(connection error)──▶ DEAD

DEAD ──(removed from pool)──▶ [garbage collected]
```

### Open Questions

| # | Question | Status | Answer |
|---|----------|--------|--------|
| 1 | Should pool metrics be exposed via Prometheus format? | Open | Consider adding /metrics endpoint later |
| 2 | Should we support HTTP transport pooling too? | Resolved | Yes — HTTP connections can also be pooled (connection reuse) |
| 3 | Should warmup happen lazily (first request) or eagerly (startup)? | Resolved | Eagerly on startup for configured servers |

---

## Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Architecture Overview | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
| 2 | Component Diagram | [component.png](diagrams/component.png) | [component.drawio](diagrams/component.drawio) |
| 3 | Acquire-Release Sequence | [api-sequence-acquire-release.png](diagrams/api-sequence-acquire-release.png) | [api-sequence-acquire-release.drawio](diagrams/api-sequence-acquire-release.drawio) |
