package com.orchestrator.mcp.client.vectordb

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.orchestrator.mcp.core.config.VectorDbConfig

object DatabaseFactory {
    fun createDataSource(config: VectorDbConfig): HikariDataSource {
        val hikariConfig = createHikariConfig(config)
        return HikariDataSource(hikariConfig)
    }

    fun createHikariConfig(config: VectorDbConfig): HikariConfig {
        return HikariConfig().apply {
            var rawUrl = config.connectionString.ifEmpty { 
                "jdbc:postgresql://${config.host}:${config.port}/${config.collectionName}" 
            }
            
            // Normalize postgresql connection strings
            var normalizedUrl = rawUrl
            if (normalizedUrl.startsWith("postgresql://")) {
                normalizedUrl = "jdbc:" + normalizedUrl
            } else if (!normalizedUrl.startsWith("jdbc:postgresql:")) {
                normalizedUrl = "jdbc:postgresql://" + normalizedUrl.removePrefix("//")
            }

            // Now it should start with jdbc:postgresql://
            val prefix = "jdbc:postgresql://"
            if (normalizedUrl.startsWith(prefix)) {
                val afterPrefix = normalizedUrl.substring(prefix.length)
                if (afterPrefix.contains("@")) {
                    val atIndex = afterPrefix.lastIndexOf("@")
                    val credentialsPart = afterPrefix.substring(0, atIndex)
                    val serverPart = afterPrefix.substring(atIndex + 1)
                    
                    val creds = credentialsPart.split(":", limit = 2)
                    username = creds[0]
                    if (creds.size > 1) {
                        password = creds[1]
                    }
                    jdbcUrl = prefix + serverPart
                } else {
                    jdbcUrl = normalizedUrl
                    // If no credentials in URL, use separate fields
                    if (username.isNullOrEmpty() && config.user.isNotEmpty()) {
                        username = config.user
                    }
                    if (password.isNullOrEmpty() && config.password.isNotEmpty()) {
                        password = config.password
                    }
                }
            } else {
                jdbcUrl = normalizedUrl
                // If not a postgresql URL or prefix missing, still try to apply credentials
                if (username.isNullOrEmpty() && config.user.isNotEmpty()) {
                    username = config.user
                }
                if (password.isNullOrEmpty() && config.password.isNotEmpty()) {
                    password = config.password
                }
            }
            
            driverClassName = "org.postgresql.Driver"
            
            maximumPoolSize = 10
            minimumIdle = 2
            idleTimeout = 600000 // 10 minutes
            connectionTimeout = 30000 // 30 seconds
            
            // Optimization for PostgreSQL
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("reWriteBatchedInserts", "true")
        }
    }
}
