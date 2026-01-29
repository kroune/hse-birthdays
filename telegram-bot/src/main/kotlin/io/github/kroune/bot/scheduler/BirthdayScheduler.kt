package io.github.kroune.bot.scheduler

import eu.vendeli.tgbot.TelegramBot
import io.github.kroune.bot.service.BirthdayService
import io.github.kroune.bot.service.NotificationService
import io.github.kroune.bot.table.BirthdayCheckLog
import io.github.kroune.common.logging.Loggers
import io.github.kroune.common.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val logger = Loggers.scheduler

private const val TARGET_HOUR = 9
private const val TARGET_MINUTE = 0

/**
 * Starts the birthday notification scheduler that runs daily at 9 AM
 * Also checks on startup if today's birthday check was missed
 */
fun startBirthdayScheduler(bot: TelegramBot, scope: CoroutineScope) {
    logger.info { "Starting birthday notification scheduler..." }

    scope.launch {
        // Check if we missed today's birthday check (in case bot was down)
        checkMissedBirthdayNotification(bot)

        while (isActive) {
            val delayMillis = calculateDelayUntilNextRun()
            logger.info { "Next birthday check in ${delayMillis / 1000 / 60} minutes" }

            delay(delayMillis)

            runCatching {
                checkAndNotifyBirthdays(bot)
            }.onFailure { e ->
                logger.error(e) { "Error checking birthdays: ${e.message}" }
            }
        }
    }
}

/**
 * Calculate delay until next 9 AM using java.time
 */
private fun calculateDelayUntilNextRun(): Long {
    val now = LocalDateTime.now(ZoneId.systemDefault())
    val targetTime = LocalTime.of(TARGET_HOUR, TARGET_MINUTE)

    var nextRun = now.toLocalDate().atTime(targetTime)
    if (now.isAfter(nextRun)) {
        nextRun = nextRun.plusDays(1)
    }

    return ChronoUnit.MILLIS.between(now, nextRun)
}

/**
 * Check if today's birthday notification was missed and run it if needed
 */
private suspend fun checkMissedBirthdayNotification(bot: TelegramBot) {
    val today = DateUtils.today()
    val todayStr = "${today.year}-${today.monthValue.toString().padStart(2, '0')}-${today.dayOfMonth.toString().padStart(2, '0')}"

    val wasCheckedToday = transaction {
        BirthdayCheckLog.selectAll()
            .where { BirthdayCheckLog.checkDate eq todayStr }
            .any()
    }

    val now = LocalDateTime.now(ZoneId.systemDefault())

    if (!wasCheckedToday && now.hour >= TARGET_HOUR) {
        logger.info { "Birthday check was not performed today yet. Running now..." }
        runCatching {
            checkAndNotifyBirthdays(bot)
        }.onFailure { e ->
            logger.error(e) { "Error running missed birthday check: ${e.message}" }
        }
    } else if (wasCheckedToday) {
        logger.info { "Birthday check was already performed today" }
    }
}

/**
 * Check for birthdays and send notifications to corresponding chats
 * @param bot The Telegram bot instance
 * @param specificChatId If provided, only check and notify this specific chat (for manual checks)
 */
suspend fun checkAndNotifyBirthdays(bot: TelegramBot, specificChatId: Long? = null) {
    logger.info { "Checking for birthdays..." + if (specificChatId != null) " for chat $specificChatId" else " for all chats" }

    val chatGroups = if (specificChatId != null) {
        listOfNotNull(BirthdayService.getChatWithGroups(specificChatId))
    } else {
        BirthdayService.getActiveChatsWithGroups()
    }

    logger.info { "Found ${chatGroups.size} chat(s) to check" }

    chatGroups.forEach { (chatId, _, isActive) ->
        if (!isActive) {
            logger.info { "Skipping inactive chat $chatId" }
            return@forEach
        }

        val birthdayUsers = BirthdayService.findBirthdayUsersForChat(chatId)

        if (birthdayUsers.isNotEmpty()) {
            logger.info { "Found ${birthdayUsers.size} birthday(s) for chat $chatId" }
            NotificationService.sendBirthdayNotification(bot, chatId, birthdayUsers)
        } else {
            logger.debug { "No birthdays found for chat $chatId today" }
        }
    }

    // Log the check only for scheduled checks (not manual checks)
    if (specificChatId == null) {
        logBirthdayCheck()
    }

    logger.info { "Birthday check completed" }
}

/**
 * Log that a birthday check was performed
 */
private fun logBirthdayCheck() {
    val today = DateUtils.today()
    val todayStr = "${today.year}-${today.monthValue.toString().padStart(2, '0')}-${today.dayOfMonth.toString().padStart(2, '0')}"
    val timestamp = System.currentTimeMillis()

    transaction {
        val existingLog = BirthdayCheckLog.selectAll()
            .where { BirthdayCheckLog.checkDate eq todayStr }
            .singleOrNull()

        if (existingLog == null) {
            BirthdayCheckLog.insert {
                it[checkDate] = todayStr
                it[checkTimestamp] = timestamp
            }
            logger.info { "Logged birthday check for $todayStr" }
        }
    }
}

/**
 * Check if a newly added user has a birthday today and notify if so
 */
suspend fun checkBirthdayForNewUser(bot: TelegramBot, chatId: Long, userId: Int) {
    logger.info { "Checking birthday for newly added user $userId in chat $chatId" }

    val birthdayUser = BirthdayService.checkUserBirthdayToday(userId)

    if (birthdayUser != null) {
        logger.info { "Newly added user ${birthdayUser.fullName} has birthday today!" }
        NotificationService.sendBirthdayNotification(bot, chatId, listOf(birthdayUser))
    } else {
        logger.debug { "Newly added user $userId does not have birthday today" }
    }
}

/**
 * Check if any users in a newly added group have birthdays today and notify if so
 */
suspend fun checkBirthdaysForNewGroup(bot: TelegramBot, chatId: Long, groupName: String) {
    logger.info { "Checking birthdays for newly added group '$groupName' in chat $chatId" }

    val birthdayUsers = BirthdayService.findGroupBirthdaysToday(groupName)

    if (birthdayUsers.isNotEmpty()) {
        logger.info { "Found ${birthdayUsers.size} birthday(s) in newly added group '$groupName'" }
        NotificationService.sendBirthdayNotification(bot, chatId, birthdayUsers)
    } else {
        logger.debug { "No birthdays found in newly added group '$groupName' today" }
    }
}
