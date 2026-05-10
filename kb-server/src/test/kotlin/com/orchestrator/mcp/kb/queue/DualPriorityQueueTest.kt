package com.orchestrator.mcp.kb.queue

import com.orchestrator.mcp.kb.config.KbQueueConfig
import com.orchestrator.mcp.kb.queue.model.Priority
import com.orchestrator.mcp.kb.queue.model.QueueTask
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Unit tests for DualPriorityQueue.
 * Verifies HPQ priority, preemption signal, and channel behavior.
 */
class DualPriorityQueueTest : DescribeSpec({

    val testConfig = KbQueueConfig(hpqCapacity = 10, npqCapacity = 50)

    describe("DualPriorityQueue") {

        it("HPQ task is selected before NPQ task") {
            val queue = DualPriorityQueue(testConfig)
            val hpqTask = createTask(Priority.HIGH, "high-task")
            val npqTask = createTask(Priority.NORMAL, "normal-task")

            queue.sendNormal(npqTask)
            queue.sendHigh(hpqTask)

            val first = queue.selectNext()
            first.taskType shouldBe "high-task"
        }

        it("NPQ task is returned when HPQ is empty") {
            val queue = DualPriorityQueue(testConfig)
            val npqTask = createTask(Priority.NORMAL, "normal-task")

            queue.sendNormal(npqTask)

            val result = queue.selectNext()
            result.taskType shouldBe "normal-task"
        }

        it("preemption signal is sent when HPQ task enqueued") {
            val queue = DualPriorityQueue(testConfig)
            val hpqTask = createTask(Priority.HIGH, "high-task")

            queue.sendHigh(hpqTask)

            val signal = queue.preemptionSignal.tryReceive()
            signal.isSuccess shouldBe true
        }

        it("tracks queue depth correctly") {
            val queue = DualPriorityQueue(testConfig)

            queue.sendHigh(createTask(Priority.HIGH, "h1"))
            queue.sendNormal(createTask(Priority.NORMAL, "n1"))
            queue.sendNormal(createTask(Priority.NORMAL, "n2"))

            queue.hpqDepth() shouldBe 1
            queue.npqDepth() shouldBe 2

            queue.selectNext() // consumes HPQ
            queue.hpqDepth() shouldBe 0
        }

        it("selectNext suspends when both channels empty") {
            val queue = DualPriorityQueue(testConfig)
            var received = false

            val job = async {
                queue.selectNext()
                received = true
            }

            delay(50)
            received shouldBe false

            queue.sendNormal(createTask(Priority.NORMAL, "late"))
            delay(50)
            received shouldBe true
            job.await()
        }
    }
})

private fun createTask(priority: Priority, type: String) = QueueTask(
    taskType = type,
    payload = buildJsonObject { put("test", true) },
    priority = priority
)
