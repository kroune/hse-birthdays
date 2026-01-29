package io.github.kroune.common.config

import io.github.kroune.Env

/**
 * Configuration for database connection
 */
data class DatabaseConfig(
    val url: String,
    val driver: String,
    val user: String,
    val password: String
) {
    companion object {
        fun fromEnv(): DatabaseConfig {
            return DatabaseConfig(
                url = Env.get("DB_URL") ?: "jdbc:postgresql://localhost:5432/postgres",
                driver = Env.get("DB_DRIVER") ?: "org.postgresql.Driver",
                user = Env.get("DB_USER") ?: "postgres",
                password = Env.require("DB_PASSWORD")
            )
        }
    }
}
