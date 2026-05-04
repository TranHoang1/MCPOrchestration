package com.orchestrator.mcp.vectordb

import com.orchestrator.mcp.config.VectorDbConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DatabaseFactoryTest {

    @Test
    fun `should normalize connection string with credentials`() {
        val config = VectorDbConfig(
            connectionString = "postgresql://postgres:postgres@localhost:5432/jira_assistant"
        )
        
        val hikariConfig = DatabaseFactory.createHikariConfig(config)
        
        assertEquals("jdbc:postgresql://localhost:5432/jira_assistant", hikariConfig.jdbcUrl)
        assertEquals("postgres", hikariConfig.username)
        assertEquals("postgres", hikariConfig.password)
    }

    @Test
    fun `should normalize connection string without credentials`() {
        val config = VectorDbConfig(
            connectionString = "jdbc:postgresql://localhost:5432/jira_assistant"
        )
        
        val hikariConfig = DatabaseFactory.createHikariConfig(config)
        
        assertEquals("jdbc:postgresql://localhost:5432/jira_assistant", hikariConfig.jdbcUrl)
    }

    @Test
    fun `should handle postgresql prefix without jdbc`() {
        val config = VectorDbConfig(
            connectionString = "postgresql://localhost:5432/jira_assistant"
        )
        
        val hikariConfig = DatabaseFactory.createHikariConfig(config)
        
        assertEquals("jdbc:postgresql://localhost:5432/jira_assistant", hikariConfig.jdbcUrl)
    }

    @Test
    fun `should fallback to host and port if connection string is empty`() {
        val config = VectorDbConfig(
            connectionString = "",
            host = "db-server",
            port = 5432,
            collectionName = "test_db"
        )
        
        val hikariConfig = DatabaseFactory.createHikariConfig(config)
        
        assertEquals("jdbc:postgresql://db-server:5432/test_db", hikariConfig.jdbcUrl)
    }

    @Test
    fun `should handle raw host and port as connection string`() {
        val config = VectorDbConfig(
            connectionString = "myhost:5432/mydb"
        )
        
        val hikariConfig = DatabaseFactory.createHikariConfig(config)
        
        assertEquals("jdbc:postgresql://myhost:5432/mydb", hikariConfig.jdbcUrl)
    }
    
    @Test
    fun `should handle complex passwords with at sign`() {
        val config = VectorDbConfig(
            connectionString = "postgresql://user:pass@word@host:5432/db"
        )
        
        val hikariConfig = DatabaseFactory.createHikariConfig(config)
        
        assertEquals("jdbc:postgresql://host:5432/db", hikariConfig.jdbcUrl)
        assertEquals("user", hikariConfig.username)
        assertEquals("pass@word", hikariConfig.password)
    }

    @Test
    fun `should use separate user and password if not in connection string`() {
        val config = VectorDbConfig(
            connectionString = "postgresql://localhost:5432/db",
            user = "myuser",
            password = "mypassword"
        )
        
        val hikariConfig = DatabaseFactory.createHikariConfig(config)
        
        assertEquals("jdbc:postgresql://localhost:5432/db", hikariConfig.jdbcUrl)
        assertEquals("myuser", hikariConfig.username)
        assertEquals("mypassword", hikariConfig.password)
    }
}
