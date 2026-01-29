import db.initDatabase
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.types.component.HttpLogLevel
import eu.vendeli.tgbot.types.component.LogLvl
import eu.vendeli.tgbot.types.configuration.RateLimits
import io.github.kroune.Env
import io.github.kroune.logger
import kotlin.time.Duration.Companion.seconds

suspend fun main(args: Array<String>) {
    logger.info { "Starting Birthday Notification Bot..." }

    // Initialize database
    logger.info { "Initializing database..." }
    initDatabase()
    initTelegramDatabase()
    logger.info { "Database initialized successfully" }

    // Get bot token from environment variable or command line argument
    val botToken = args.getOrNull(0) ?: Env.get("TELEGRAM_BOT_TOKEN")
    requireNotNull(botToken) {
        "Bot token not provided. Set TELEGRAM_BOT_TOKEN in .env or environment variable or pass as command line argument"
    }

    // Initialize bot
    val bot = initBot(botToken)

    // Start bot with scheduler
    logger.info { "Starting bot and birthday scheduler..." }
    startBot(bot)
}

/**
 * Initialize and configure the Telegram bot
 */
fun initBot(token: String): TelegramBot {
    logger.info { "Initializing Telegram bot..." }

    val bot = TelegramBot(token) {
        rateLimiter {
            this.limits = RateLimits(
                period = 1.seconds.inWholeMilliseconds,
                rate = 20,
            )
        }
        logging {
            this.botLogLevel = LogLvl.DEBUG
            this.httpLogLevel = HttpLogLevel.NONE
        }
        httpClient {
            this.connectTimeoutMillis = 30000L
            this.socketTimeoutMillis = 30000L
            this.requestTimeoutMillis = 30000L
        }
    }

    logger.info { "Bot initialized successfully" }
    return bot
}
