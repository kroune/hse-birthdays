import eu.vendeli.tgbot.TelegramBot
import io.github.kroune.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import model.UserInfo
import kotlin.time.Duration.Companion.hours

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
