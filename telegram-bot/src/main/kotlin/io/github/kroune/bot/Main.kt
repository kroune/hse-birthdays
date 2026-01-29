package io.github.kroune.bot

import db.initDatabase
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.types.component.HttpLogLevel
import eu.vendeli.tgbot.types.component.LogLvl
import eu.vendeli.tgbot.types.configuration.RateLimits
import io.github.kroune.Env
import io.github.kroune.bot.table.initTelegramTables
import io.github.kroune.common.config.BotConfig
import io.github.kroune.common.logging.Loggers

private val logger = Loggers.bot

suspend fun main(args: Array<String>) {
    logger.info { "Starting Birthday Notification Bot..." }

    // Initialize database
    logger.info { "Initializing database..." }
    initDatabase()
    initTelegramTables()
    logger.info { "Database initialized successfully" }

    // Get bot config from environment or command line
    val botToken = args.getOrNull(0) ?: Env.get("TELEGRAM_BOT_TOKEN")
    requireNotNull(botToken) {
        "Bot token not provided. Set TELEGRAM_BOT_TOKEN in .env or environment variable or pass as command line argument"
    }

    val config = runCatching { BotConfig.fromEnv() }.getOrElse {
        BotConfig(token = botToken)
    }

    // Initialize bot
    val bot = initBot(config)

    // Start bot with scheduler
    logger.info { "Starting bot and birthday scheduler..." }
    startBot(bot)
}

/**
 * Initialize and configure the Telegram bot
 */
fun initBot(config: BotConfig): TelegramBot {
    logger.info { "Initializing Telegram bot..." }

    val bot = TelegramBot(config.token) {
        rateLimiter {
            this.limits = RateLimits(
                period = config.rateLimitPeriodMs,
                rate = config.rateLimitRate,
            )
        }
        logging {
            this.botLogLevel = LogLvl.DEBUG
            this.httpLogLevel = HttpLogLevel.NONE
        }
        httpClient {
            this.connectTimeoutMillis = config.connectTimeoutMs
            this.socketTimeoutMillis = config.socketTimeoutMs
            this.requestTimeoutMillis = config.requestTimeoutMs
        }
    }

    logger.info { "Bot initialized successfully" }
    return bot
}


