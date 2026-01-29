package io.github.kroune.common.config

import io.github.kroune.Env

/**
 * Configuration for Telegram bot
 */
data class BotConfig(
    val token: String,
    val rateLimitPeriodSec: Long = 60L,
    val rateLimitRate: Long = 100L,
    val connectTimeoutMs: Long = 30000L,
    val socketTimeoutMs: Long = 30000L,
    val requestTimeoutMs: Long = 30000L
) {
    companion object {
        fun fromEnv(): BotConfig {
            return BotConfig(
                token = Env.require("TELEGRAM_BOT_TOKEN"),
                rateLimitPeriodSec = Env.get("BOT_RATE_LIMIT_PERIOD_SEC")?.toLongOrNull() ?: 60L,
                rateLimitRate = Env.get("BOT_RATE_LIMIT_RATE")?.toLongOrNull() ?: 100L,
                connectTimeoutMs = Env.get("BOT_CONNECT_TIMEOUT_MS")?.toLongOrNull() ?: 30000L,
                socketTimeoutMs = Env.get("BOT_SOCKET_TIMEOUT_MS")?.toLongOrNull() ?: 30000L,
                requestTimeoutMs = Env.get("BOT_REQUEST_TIMEOUT_MS")?.toLongOrNull() ?: 30000L
            )
        }
    }
}
