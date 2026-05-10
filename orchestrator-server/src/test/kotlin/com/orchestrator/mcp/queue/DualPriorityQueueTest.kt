package com.orchestrator.mcp.queue

import com.orchestrator.mcp.queue.config.QueueConfig
import com.orchestrator.mcp.queue.model.Priority
import com.orchestrator.mcp.queue.model.QueueTask
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DualPriorityQueueTest : FunSpec({

    val config = QueueConfig(hpqCapacity = 5, npqCapacity = 10)

    fun testTask(id: String = "task") = QueueTask(
        taskType = "test",
        payload = buildJsonObject { put("id", id) },
        priority = Priority.NORMAL
    )

    test("UT-08: HPQ prioritized over NPQ in selectNext") {
        val queue = DualPriorityQueue(config)
        val npqTask = testTask("npq")
        val hpqTask = testTask("hpq")

        // Send NPQ first, then HPQ
        queue.send(npqTask, Priority.NORMAL)
        queue.send(hpqTask, Priority.HIGH)

        // selectNext should return HPQ first
        val first = queue.selectNext()
        first.payload.toString() shouldBe """{"id":"hpq"}"""

        val second = queue.selectNext()
        second.payload.toString() shouldBe """{"id":"npq"}"""

        queue.close()
    }

    test("UT-09: send HIGH triggers preemption signal") {
        val queue = DualPriorityQueue(config)
        val task = testTask("hpq")

        queue.send(task, Priority.HIGH)

        // Preemption signal should have been sent
        val signal = queue.preemptionSignal.tryReceive()
        signal.isSuccess shouldBe true

        queue.close()
    }

    test("UT-10: send NORMAL does NOT trigger preemption signal") {
        val queue = DualPriorityQueue(config)
        val task = testTask("npq")

        queue.send(task, Priority.NORMAL)

        val signal = queue.preemptionSignal.tryReceive()
        signal.isSuccess shouldBe false

        queue.close()
    }

    test("UT-11: requeue sends task back to NPQ") {
        val queue = DualPriorityQueue(config)
        val task = testTask("requeued")

        queue.requeue(task)

        val received = queue.normalPriorityChannel.tryReceive()
        received.isSuccess shouldBe true
        received.getOrNull()?.payload.toString() shouldBe """{"id":"requeued"}"""

        queue.close()
    }

    test("UT-12: close prevents further sends") {
        val queue = DualPriorityQueue(config)
        queue.close()

        val result = queue.highPriorityChannel.trySend(testTask())
        result.isSuccess shouldBe false
    }

    test("UT-13: selectNext suspends when both channels empty") {
        val queue = DualPriorityQueue(config)
        var received = false

        val job = async {
            queue.selectNext()
            received = true
        }

        delay(100)
        received shouldBe false // Still suspended

        queue.send(testTask("wake"), Priority.NORMAL)
        delay(50)
        received shouldBe true

        queue.close()
    }
})
