package com.orchestrator.mcp.it

import com.orchestrator.mcp.client.vectordb.DatabaseInitializer
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.ResultSet

@Testcontainers(disabledWithoutDocker = true)
class DatabaseInitializationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("mcp_db")
            .withUsername("test")
            .withPassword("test")

        private lateinit var dataSource: HikariDataSource

        @JvmStatic
        @BeforeAll
        fun setup() {
            val config = HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                driverClassName = postgres.driverClassName
            }
            dataSource = HikariDataSource(config)
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            dataSource.close()
        }
    }

    @Test
    fun `should create required tables on initialization`() = runBlocking {
        // Skip if Docker is not available
        org.junit.jupiter.api.Assumptions.assumeTrue(postgres.isRunning, "PostgreSQL container is not running (Docker might be missing)")
        
        val initializer = DatabaseInitializer(dataSource)
        
        // When
        initializer.initialize()
        
        // Then
        dataSource.connection.use { conn ->
            val tables = mutableListOf<String>()
            val rs: ResultSet = conn.metaData.getTables(null, null, "%", arrayOf("TABLE"))
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME").lowercase())
            }
            
            assertTrue(tables.contains("server_config"), "Table 'server_config' should exist. Found: ${tables.joinToString()}")
            assertTrue(tables.contains("tool_toggle_state"), "Table 'tool_toggle_state' should exist. Found: ${tables.joinToString()}")
        }
    }
}
