package com.orchestrator.mcp.session

import com.orchestrator.mcp.model.ServerOverloadedException
import com.orchestrator.mcp.model.SessionExpiredException
import com.orchestrator.mcp.model.SessionNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

class SessionManagerImplTest : DescribeSpec({

    val config = HttpSessionConfig(
        maxSessions = 3,
        sessionTtlMinutes = 30,
        eventBufferSize = 100,
        cleanupIntervalSeconds = 60
    )

    describe("createSession") {
        it("should create a new session with unique ID") {
            val manager = SessionManagerImpl(config)
            val session = manager.createSession(ClientInfo("test-client", "1.0"))
            session.id shouldNotBe null
            session.clientInfo?.name shouldBe "test-client"
            session.state shouldBe SessionState.ACTIVE
            manager.getActiveSessionCount() shouldBe 1
        }

        it("should throw ServerOverloadedException when max sessions reached") {
            val manager = SessionManagerImpl(config)
            manager.createSession()
            manager.createSession()
            manager.createSession()
            shouldThrow<ServerOverloadedException> {
                manager.createSession()
            }
        }
    }

    describe("validateSession") {
        it("should return session for valid ID") {
            val manager = SessionManagerImpl(config)
            val session = manager.createSession()
            val validated = manager.validateSession(session.id)
            validated.id shouldBe session.id
        }

        it("should throw SessionNotFoundException for unknown ID") {
            val manager = SessionManagerImpl(config)
            shouldThrow<SessionNotFoundException> {
                manager.validateSession(UUID.randomUUID())
            }
        }
    }

    describe("terminateSession") {
        it("should remove session from active sessions") {
            val manager = SessionManagerImpl(config)
            val session = manager.createSession()
            manager.getActiveSessionCount() shouldBe 1
            manager.terminateSession(session.id)
            manager.getActiveSessionCount() shouldBe 0
        }
    }

    describe("addEvent") {
        it("should add event to session buffer") {
            val manager = SessionManagerImpl(config)
            val session = manager.createSession()
            val event = manager.addEvent(session.id, """{"jsonrpc":"2.0","id":1}""")
            event.id shouldBe "evt-1"
            event.data shouldBe """{"jsonrpc":"2.0","id":1}"""
        }
    }

    describe("getEventsAfter") {
        it("should return events after specified event ID") {
            val manager = SessionManagerImpl(config)
            val session = manager.createSession()
            manager.addEvent(session.id, "event1")
            manager.addEvent(session.id, "event2")
            manager.addEvent(session.id, "event3")
            val events = manager.getEventsAfter(session.id, "evt-1")
            events.size shouldBe 2
            events[0].data shouldBe "event2"
            events[1].data shouldBe "event3"
        }
    }

    describe("cleanupExpiredSessions") {
        it("should remove expired sessions") {
            val fixedClock = object : Clock {
                var now = Instant.fromEpochMilliseconds(0)
                override fun now() = now
            }
            val manager = SessionManagerImpl(config, fixedClock)
            manager.createSession()
            manager.getActiveSessionCount() shouldBe 1

            // Advance time past TTL
            fixedClock.now = Instant.fromEpochMilliseconds(31.minutes.inWholeMilliseconds)
            val cleaned = manager.cleanupExpiredSessions()
            cleaned shouldBe 1
            manager.getActiveSessionCount() shouldBe 0
        }
    }
})
