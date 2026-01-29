package io.github.kroune.bot

import eu.vendeli.tgbot.TelegramBot
import io.github.kroune.bot.cache.TTLCache
import io.github.kroune.bot.model.UserInfo
import io.github.kroune.bot.scheduler.startBirthdayScheduler
import io.github.kroune.common.logging.Loggers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.hours

private val logger = Loggers.bot

// Storage for list user pagination sessions - TTL-based cache with 1 hour expiration
val listUserSessions = TTLCache<Long, ListUserSession>(1.hours.inWholeMilliseconds)

data class ListUserSession(
    val userList: List<UserInfo>,
    val currentPage: Int = 0,
    val messageId: Long? = null
)

/**
 * Start the bot and begin listening for updates
 */
suspend fun startBot(bot: TelegramBot) {
    logger.info { "Starting bot update listener..." }

    // Start the birthday scheduler
    val schedulerScope = CoroutineScope(Dispatchers.IO)
    startBirthdayScheduler(bot, schedulerScope)

    // Start TTL cache cleanup tasks
    listUserSessions.startCleanupTask()
    // Note: searchSessions cleanup is started by the UserSearchChain module on first use
    // to keep initialization decoupled from Bot.kt

    bot.handleUpdates()
}


