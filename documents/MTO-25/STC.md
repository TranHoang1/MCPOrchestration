# Software Test Cases (STC)

## MCPOrchestration — MTO-25: KB Refinery — Dual-Priority Queue (Kotlin Channels)

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-25 |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Related STP | STP-v1-MTO-25.docx |

---

## 1. Unit Tests (UT)

### UT-01: QueueServiceImpl — Enqueue HPQ task successfully

| Attribute | Value |
|-----------|-------|
| Class Under Test | QueueServiceImpl |
| Method | enqueue(task, Priority.HIGH) |
| Precondition | Valid task, mocked repository + queue |
| Steps | 1. Create valid QueueTask 2. Call enqueue with HIGH priority |
| Expected | Repository.insert called, queue.send called with HIGH, returns task_id |
| Mocks | TaskStateRepository (insert returns UUID), DualPriorityQueue (send succeeds) |

### UT-02: DualPriorityQueue — HPQ prioritized in selectNext

| Attribute | Value |
|-----------|-------|
| Class Under Test | DualPriorityQueue |
| Method | selectNext() |
| Precondition | Both HPQ and NPQ have tasks |
| Steps | 1. Send task to NPQ 2. Send task to HPQ 3. Call selectNext() |
| Expected | HPQ task returned first |

### UT-03: QueueServiceImpl — Enqueue NPQ task successfully

| Attribute | Value |
|-----------|-------|
| Class Under Test | QueueServiceImpl |
| Method | enqueue(task, Priority.NORMAL) |
| Precondition | Valid task |
| Steps | 1. Create valid QueueTask 2. Call enqueue with NORMAL priority |
| Expected | Repository.insert called, queue.send called with NORMAL |

### UT-04: QueueServiceImpl — Validation rejects blank task_type

| Attribute | Value |
|-----------|-------|
| Class Under Test | QueueServiceImpl |
| Method | enqueue(task, priority) |
| Precondition | Task with blank task_type |
| Steps | 1. Create QueueTask with taskType="" 2. Call enqueue |
| Expected | Throws InvalidTaskException |

### UT-05: QueueWorker — Preemption cancels NPQ job

| Attribute | Value |
|-----------|-------|
| Class Under Test | QueueWorker |
| Method | processNpqTaskWithPreemption |
| Precondition | NPQ task running, preemption signal sent |
| Steps | 1. Start processing NPQ task 2. Send preemption signal |
| Expected | NPQ job cancelled, task re-queued |

### UT-06: QueueWorker — HPQ task never preempted

| Attribute | Value |
|-----------|-------|
| Class Under Test | QueueWorker |
| Method | processHpqTask |
| Precondition | HPQ task running |
| Steps | 1. Start processing HPQ task 2. Send preemption signal |
| Expected | HPQ task continues uninterrupted |

### UT-07: QueueWorker — Re-queue after preemption

| Attribute | Value |
|-----------|-------|
| Class Under Test | QueueWorker |
| Method | handlePreemption |
| Precondition | Task was preempted |
| Steps | 1. Call handlePreemption with task |
| Expected | Repository.updateStatus(Pending) called, queue.requeue called |

### UT-08: QueueWorker — Preemption does not increment retry_count

| Attribute | Value |
|-----------|-------|
| Class Under Test | QueueWorker |
| Method | handlePreemption |
| Precondition | Task with retryCount=0 preempted |
| Steps | 1. Preempt task 2. Check repository calls |
| Expected | incrementRetryAndRequeue NOT called, updateStatus(Pending) called |

### UT-09: QueueWatchdog — Detects stuck task and re-queues

| Attribute | Value |
|-----------|-------|
| Class Under Test | QueueWatchdog |
| Method | scanStuckTasks |
| Precondition | Task stuck > 5min, retryCount=1 |
| Steps | 1. Mock findStuckTasks returns task 2. Run scan |
| Expected | incrementRetryAndRequeue called |

### UT-10: QueueWatchdog — Marks stuck task as Failed when max retries

| Attribute | Value |
|-----------|-------|
| Class Under Test | QueueWatchdog |
| Method | scanStuckTasks |
| Precondition | Task stuck > 5min, retryCount=3 |
| Steps | 1. Mock findStuckTasks returns task 2. Run scan |
| Expected | markFailed called |

### UT-11: QueueWorker — Retry with exponential backoff

| Attribute | Value |
|-----------|-------|
| Class Under Test | QueueWorker |
| Method | handleTaskFailure |
| Precondition | Task failed, retryCount=0 |
| Steps | 1. Simulate task failure 2. Verify delay and re-queue |
| Expected | Delay = 2000ms (1000 * 2^1), incrementRetryAndRequeue called |

### UT-12: QueueWorker — No retry after max retries

| Attribute | Value |
|-----------|-------|
| Class Under Test | QueueWorker |
| Method | handleTaskFailure |
| Precondition | Task failed, retryCount=2 (will become 3) |
| Steps | 1. Simulate task failure |
| Expected | markFailed called, no re-queue |

### UT-13: CrashRecoveryService — Recovers interrupted tasks

| Attribute | Value |
|-----------|-------|
| Class Under Test | CrashRecoveryService |
| Method | recover |
| Precondition | 3 tasks in Processing state, retryCount < 3 |
| Steps | 1. Call recover() |
| Expected | All 3 tasks re-queued, returns (3, 0) |

### UT-14: CrashRecoveryService — Fails tasks exceeding max retries

| Attribute | Value |
|-----------|-------|
| Class Under Test | CrashRecoveryService |
| Method | recover |
| Precondition | 1 task in Processing, retryCount=3 |
| Steps | 1. Call recover() |
| Expected | Task marked Failed, returns (0, 1) |

---

## 2. Integration Tests (IT)

### IT-01: Enqueue HPQ → Worker processes immediately

| Attribute | Value |
|-----------|-------|
| Components | QueueService + DualPriorityQueue + QueueWorker + PostgreSQL |
| Precondition | Testcontainers PostgreSQL running, migration applied |
| Steps | 1. Enqueue HPQ task 2. Wait for worker to process 3. Query DB |
| Expected | Task status=Completed in DB, started_at and completed_at populated |

### IT-02: Enqueue NPQ → Worker processes when HPQ empty

| Attribute | Value |
|-----------|-------|
| Components | QueueService + DualPriorityQueue + QueueWorker + PostgreSQL |
| Steps | 1. Enqueue NPQ task 2. Wait for processing 3. Query DB |
| Expected | Task status=Completed |

### IT-03: Preemption — HPQ interrupts NPQ processing

| Attribute | Value |
|-----------|-------|
| Components | Full queue system + PostgreSQL |
| Steps | 1. Enqueue slow NPQ task 2. Wait for NPQ processing to start 3. Enqueue HPQ task 4. Verify NPQ preempted |
| Expected | NPQ task status=Pending (re-queued), HPQ task status=Completed |

### IT-04: Re-queue — Preempted task retry_count unchanged

| Attribute | Value |
|-----------|-------|
| Components | Full queue system + PostgreSQL |
| Steps | 1. Enqueue NPQ task (retryCount=0) 2. Preempt it 3. Query DB |
| Expected | retry_count still 0, status=Pending |

### IT-05: DB-first — Task persisted before channel send

| Attribute | Value |
|-----------|-------|
| Components | QueueService + PostgreSQL |
| Steps | 1. Enqueue task 2. Immediately query DB (before worker picks up) |
| Expected | Task exists in DB with status=Pending |

### IT-06: State transitions — Full lifecycle

| Attribute | Value |
|-----------|-------|
| Components | Full system + PostgreSQL |
| Steps | 1. Enqueue 2. Verify Pending 3. Worker picks up → Processing 4. Complete → Completed |
| Expected | All state transitions recorded with timestamps |

### IT-07: Concurrent enqueue — Multiple tasks

| Attribute | Value |
|-----------|-------|
| Components | QueueService + PostgreSQL |
| Steps | 1. Enqueue 50 HPQ + 100 NPQ tasks concurrently |
| Expected | All 150 tasks persisted, no duplicates, no exceptions |

### IT-08: Watchdog — Detects and recovers stuck task in DB

| Attribute | Value |
|-----------|-------|
| Components | QueueWatchdog + PostgreSQL |
| Steps | 1. Insert task with status=Processing, started_at=10min ago 2. Run watchdog scan |
| Expected | Task status=Pending, retry_count incremented |

### IT-09: Retry — Exponential backoff timing

| Attribute | Value |
|-----------|-------|
| Components | QueueWorker + PostgreSQL |
| Steps | 1. Enqueue task with failing handler 2. Observe retry timing |
| Expected | Retries at ~2s, ~4s, ~8s intervals, then Failed |

### IT-10: Crash recovery — Startup re-queues Processing tasks

| Attribute | Value |
|-----------|-------|
| Components | CrashRecoveryService + PostgreSQL |
| Steps | 1. Insert 5 tasks with status=Processing 2. Run crash recovery |
| Expected | Tasks with retryCount<3 → Pending, retryCount>=3 → Failed |

---

## 3. E2E-API Tests

### E2E-01: Full HPQ lifecycle with timing

| Steps | 1. Enqueue HPQ task 2. Measure time to completion |
| Expected | Task completed, total time < 200ms (including DB ops) |

### E2E-02: Full NPQ batch processing

| Steps | 1. Enqueue 10 NPQ tasks 2. Wait for all to complete |
| Expected | All 10 tasks completed in order |

### E2E-03: Preemption E2E with timing

| Steps | 1. Enqueue slow NPQ task (sleeps 5s) 2. After 100ms, enqueue HPQ task 3. Measure preemption time |
| Expected | HPQ completed, NPQ re-queued, preemption < 500ms |

### E2E-04: State tracking E2E

| Steps | 1. Enqueue task 2. Query status at each stage |
| Expected | Pending → Processing → Completed transitions verified |

### E2E-05: Watchdog E2E

| Steps | 1. Enqueue task with handler that hangs 2. Wait for watchdog scan 3. Verify recovery |
| Expected | Task re-queued after stuck threshold |

### E2E-06: Retry exhaustion E2E

| Steps | 1. Enqueue task with always-failing handler 2. Wait for 3 retries |
| Expected | Task status=Failed after 3 retries, error_message populated |

### E2E-07: Crash recovery E2E

| Steps | 1. Seed DB with Processing tasks 2. Initialize CrashRecoveryService 3. Verify recovery |
| Expected | All tasks recovered or failed appropriately |

---

## 4. Property-Based Tests (PBT)

### PBT-01: Retry count never exceeds max_retries

| Property | For any task that fails repeatedly, retry_count ≤ max_retries |
| Generator | Random task_type, random payload, random initial retryCount (0..5) |
| Iterations | 1000 |

### PBT-02: Task validation rejects invalid inputs

| Property | For any blank/null task_type, InvalidTaskException is thrown |
| Generator | Random strings including empty, blank, >100 chars |
| Iterations | 1000 |

### PBT-03: Priority ordering invariant

| Property | When both HPQ and NPQ have tasks, selectNext always returns HPQ first |
| Generator | Random task pairs (one HPQ, one NPQ) |
| Iterations | 500 |

---

## 5. Test Summary

| Level | Test Count | Automated | Manual |
|-------|-----------|-----------|--------|
| PBT | 3 | 3 | 0 |
| UT | 14 | 14 | 0 |
| IT | 10 | 10 | 0 |
| E2E-API | 7 | 7 | 0 |
| **Total** | **34** | **34** | **0** |

---

## 6. Test Data Files

| File | Purpose | Format |
|------|---------|--------|
| testdata/enqueue-tasks.csv | Sample tasks for enqueue tests | task_type, payload_json, priority |
| testdata/stuck-tasks.csv | Pre-seeded stuck tasks | task_id, status, started_at, retry_count |
| testdata/crash-recovery.csv | Processing tasks for recovery | task_id, status, retry_count, worker_id |
