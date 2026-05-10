package com.orchestrator.mcp.security

import com.orchestrator.mcp.security.model.KbRole
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import java.sql.Connection
import java.sql.Statement
import javax.sql.DataSource

class RlsConnectionWrapperTest : FunSpec({

    lateinit var dataSource: DataSource
    lateinit var connection: Connection
    lateinit var statement: Statement
    lateinit var wrapper: RlsConnectionWrapper

    beforeEach {
        statement = mockk(relaxed = true)
        connection = mockk(relaxed = true)
        dataSource = mockk()

        every { dataSource.connection } returns connection
        every { connection.createStatement() } returns statement

        wrapper = RlsConnectionWrapper(dataSource)
    }

    test("executeWithRole sets role before executing block") {
        runTest {
            wrapper.executeWithRole(KbRole.DEVELOPER) { conn ->
                "result"
            }

            verifyOrder {
                connection.autoCommit = false
                statement.execute("SET LOCAL ROLE 'kb_developer'")
                connection.commit()
            }
        }
    }

    test("executeWithRole returns block result") {
        runTest {
            val result = wrapper.executeWithRole(KbRole.BA_ADMIN) { "hello" }
            result shouldBe "hello"
        }
    }

    test("executeWithRole rolls back on exception") {
        runTest {
            shouldThrow<RuntimeException> {
                wrapper.executeWithRole(KbRole.DEVELOPER) {
                    throw RuntimeException("test error")
                }
            }

            verify { connection.rollback() }
        }
    }

    test("executeWithRole resets autoCommit in finally") {
        runTest {
            runCatching {
                wrapper.executeWithRole(KbRole.LOW_PRIVILEGE) {
                    throw RuntimeException("fail")
                }
            }

            verify { connection.autoCommit = true }
            verify { connection.close() }
        }
    }

    test("executeWithRole uses correct pgRoleName for each role") {
        runTest {
            wrapper.executeWithRole(KbRole.BA_ADMIN) { "ok" }
            verify { statement.execute("SET LOCAL ROLE 'kb_admin'") }
        }
    }
})
