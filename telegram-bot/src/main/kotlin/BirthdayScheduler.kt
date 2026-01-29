import db.Users
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.api.message.message
import io.github.kroune.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import model.UserWrapper
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
            val now = LocalDateTime.now(ZoneId.systemDefault())
            val nextRun = calculateNextRunTime(now)
            val delayMillis = ChronoUnit.MILLIS.between(now, nextRun)

            logger.info { "Next birthday check scheduled at: $nextRun (in ${delayMillis / 1000 / 60} minutes)" }

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
 * Check if today's birthday notification was missed and run it if needed
 */
private suspend fun checkMissedBirthdayNotification(bot: TelegramBot) {
    val today = LocalDate.now()
    val todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    val wasCheckedToday = transaction {
        BirthdayCheckLog.selectAll()
            .where { BirthdayCheckLog.checkDate eq todayStr }
            .any()
    }

    val now = LocalDateTime.now(ZoneId.systemDefault())
    val nextRun = now.withHour(9).withMinute(0).withSecond(0).withNano(0)

    // If we've already passed 9 AM today
    if (!wasCheckedToday && now.isAfter(nextRun)) {
        logger.info { "Birthday check was not performed today yet. Running now..." }
        runCatching {
            checkAndNotifyBirthdays(bot)
        }.onFailure { e ->
            logger.error(e) { "Error running missed birthday check: ${e.message}" }
        }
    } else {
        logger.info { "Birthday check was already performed today at: ${getBirthdayCheckTime(todayStr)}" }
    }
}

/**
 * Get the time when birthday check was performed for a given date
 */
private fun getBirthdayCheckTime(dateStr: String): String {
    return transaction {
        BirthdayCheckLog.selectAll()
            .where { BirthdayCheckLog.checkDate eq dateStr }
            .map {
                val timestamp = it[BirthdayCheckLog.checkTimestamp]
                LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(timestamp),
                    ZoneId.systemDefault()
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }
            .singleOrNull() ?: "unknown"
    }
}

/**
 * Log that a birthday check was performed
 */
private fun logBirthdayCheck() {
    val today = LocalDate.now()
    val todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val timestamp = System.currentTimeMillis()

    transaction {
        // Check if already logged today (avoid duplicates)
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
 * Calculate the next run time (next 9 AM)
 */
private fun calculateNextRunTime(now: LocalDateTime): LocalDateTime {
    var nextRun = now.withHour(9).withMinute(0).withSecond(0).withNano(0)

    // If we've already passed 9 AM today, schedule for tomorrow
    if (now.isAfter(nextRun)) {
        nextRun = nextRun.plusDays(1)
    }

    return nextRun
}

/**
 * Check for birthdays and send notifications to corresponding chats
 * @param bot The Telegram bot instance
 * @param specificChatId If provided, only check and notify this specific chat (for manual checks)
 */
suspend fun checkAndNotifyBirthdays(bot: TelegramBot, specificChatId: Long? = null) {
    logger.info { "Checking for birthdays..." + if (specificChatId != null) " for chat $specificChatId" else " for all chats" }

    val today = LocalDate.now()
    val todayFormatted = today.format(DateTimeFormatter.ofPattern("MM-dd"))

    logger.info { "Today's date (MM-dd): $todayFormatted" }

    // Get all registered chats with their target groups (or just the specific chat if provided)
    val chatGroups = transaction {
        val query = if (specificChatId != null) {
            BirthdayChats.selectAll().where { BirthdayChats.telegramChatId eq specificChatId }
        } else {
            // Only get active chats for scheduled checks
            BirthdayChats.selectAll().where { BirthdayChats.isActive eq true }
        }

        query.map { chatRow ->
            val chatId = chatRow[BirthdayChats.telegramChatId]
            val chatDbId = chatRow[BirthdayChats.id]
            val isActive = chatRow[BirthdayChats.isActive]

            val groups = BirthdayChatTargetGroups.selectAll()
                .where { BirthdayChatTargetGroups.birthdayChat eq chatDbId }
                .map { it[BirthdayChatTargetGroups.targetGroup] }

            Triple(chatId, groups, isActive)
        }
    }

    logger.info { "Found ${chatGroups.size} registered chat(s) to check" }

    // For each chat, find users with birthdays in their target groups and additional users
    chatGroups.forEach { (chatId, groups, isActive) ->
        // Skip inactive chats for manual checks too (they can reactivate by using /start)
        if (!isActive) {
            logger.info { "Skipping inactive chat $chatId (bot was blocked or chat was deleted)" }
            return@forEach
        }

        logger.info { "Checking birthdays for chat $chatId" }

        val birthdayUsers = transaction {
            val chatDbId = BirthdayChats.selectAll()
                .where { BirthdayChats.telegramChatId eq chatId }
                .single()[BirthdayChats.id]

            // Get users from target groups
            val groupUsers = if (groups.isNotEmpty()) {
                groups.flatMap { groupName ->
                    Users.selectAll()
                        .where { Users.description ilike "%$groupName%" }
                        .filter {
                            checkBirthdayMatch(it[Users.birthDate], todayFormatted)
                        }
                        .map {
                            BirthdayPerson(
                                fullName = it[Users.fullName],
                                birthDate = it[Users.birthDate] ?: "",
                                groupName = groupName
                            )
                        }
                }
            } else {
                emptyList()
            }

            // Get manually added users
            val additionalUsers = BirthdayChatAdditionalUsers.selectAll()
                .where { BirthdayChatAdditionalUsers.birthdayChat eq chatDbId }
                .mapNotNull { addUserRow ->
                    val userId = addUserRow[BirthdayChatAdditionalUsers.user]
                    UserWrapper(userId.value)
                }
                .filter { userWrapper ->
                    checkBirthdayMatch(userWrapper.birthDate, todayFormatted)
                }
                .map { userWrapper ->
                    BirthdayPerson(
                        fullName = userWrapper.fullName,
                        birthDate = userWrapper.birthDate ?: "",
                        groupName = "Дополнительный"
                    )
                }

            // Combine and remove duplicates
            (groupUsers + additionalUsers).distinctBy { it.fullName }
        }

        if (birthdayUsers.isNotEmpty()) {
            logger.info { "Found ${birthdayUsers.size} birthday(s) for chat $chatId" }
            sendBirthdayNotification(bot, chatId, birthdayUsers)
        } else {
            logger.debug { "No birthdays found for chat $chatId today" }
        }
    }

    // Log the check only for scheduled checks (not manual checks for specific chats)
    if (specificChatId == null) {
        logBirthdayCheck()
    }

    logger.info { "Birthday check completed" }
}

/**
 * Check if a birth date matches today's date (month-day)
 */
private fun checkBirthdayMatch(birthDate: String?, todayFormatted: String): Boolean {
    if (birthDate == null || birthDate.isBlank()) return false

    return runCatching {
        // Extract month-day portion (handles both normal dates and 0000-MM-dd format)
        // birth_date format: yyyy-MM-dd (e.g., 2002-05-21 or 0000-04-24)
        val birthDayFormatted = if (birthDate.length >= 10) {
            // Extract MM-dd from position 5-9
            birthDate.substring(5, 10)
        } else {
            null
        }

        birthDayFormatted == todayFormatted
    }.getOrElse {
        logger.warn { "Invalid birth date format for user: $birthDate" }
        false
    }
}

/**
 * Send birthday notification to a chat
 */
private suspend fun sendBirthdayNotification(bot: TelegramBot, chatId: Long, birthdayUsers: List<BirthdayPerson>) {
    runCatching {
        val messageText = buildBirthdayMessage(birthdayUsers)

        message {
            messageText
        }.send(chatId, bot)

        logger.info { "Sent birthday notification to chat $chatId for ${birthdayUsers.size} person(s)" }

        // If we successfully sent a message, ensure chat is marked as active
        markChatAsActive(chatId)
    }.onFailure { e ->
        handleSendMessageError(e, chatId, birthdayUsers)
    }
}

/**
 * Handle errors when sending messages to a chat
 */
private fun handleSendMessageError(e: Throwable, chatId: Long, birthdayUsers: List<BirthdayPerson>) {
    val errorMessage = e.message ?: "Unknown error"

    // Check if this is a bot blocked error (403 Forbidden)
    // Telegram API returns errors with status codes in the message or as specific exception types
    val isBotBlocked = errorMessage.contains("403", ignoreCase = true) ||
            errorMessage.contains("Forbidden", ignoreCase = true) ||
            errorMessage.contains("bot was blocked", ignoreCase = true) ||
            errorMessage.contains("user is deactivated", ignoreCase = true) ||
            errorMessage.contains("bot can't initiate conversation", ignoreCase = true)

    val isChatNotFound = errorMessage.contains("chat not found", ignoreCase = true) ||
            errorMessage.contains("400", ignoreCase = true)

    if (isBotBlocked || isChatNotFound) {
        logger.warn {
            "Chat $chatId is no longer accessible (bot blocked or chat deleted). Marking as inactive. " +
                    "Error: $errorMessage. Users affected: ${birthdayUsers.map { it.fullName }}"
        }
        markChatAsInactive(chatId)
    } else {
        // For other errors (transient failures), just log them
        // The library already handles retries for network/server errors
        logger.error(e) {
            "Failed to send birthday notification to chat $chatId. " +
                    "Error: $errorMessage. " +
                    "Users affected: ${birthdayUsers.map { it.fullName }}. " +
                    "This may be a transient error that will be retried."
        }
    }
}

/**
 * Mark a chat as inactive due to bot being blocked or chat being deleted
 */
private fun markChatAsInactive(chatId: Long) {
    transaction {
        val chat = BirthdayChats.selectAll()
            .where { BirthdayChats.telegramChatId eq chatId }
            .singleOrNull()

        if (chat != null) {
            val chatDbId = chat[BirthdayChats.id]
            BirthdayChats.update({ BirthdayChats.id eq chatDbId }) {
                it[isActive] = false
            }
            logger.info { "Chat $chatId marked as inactive in database" }
        }
    }
}

/**
 * Mark a chat as active (called after successful message send)
 */
private fun markChatAsActive(chatId: Long) {
    transaction {
        val chat = BirthdayChats.selectAll()
            .where { BirthdayChats.telegramChatId eq chatId }
            .singleOrNull()

        if (chat != null && !chat[BirthdayChats.isActive]) {
            val chatDbId = chat[BirthdayChats.id]
            BirthdayChats.update({ BirthdayChats.id eq chatDbId }) {
                it[isActive] = true
            }
            logger.info { "Chat $chatId marked as active again" }
        }
    }
}


/**
 * Build the birthday notification message
 */
private fun buildBirthdayMessage(birthdayUsers: List<BirthdayPerson>): String {
    return if (birthdayUsers.size == 1) {
        val user = birthdayUsers.first()
        val age = calculateAge(user.birthDate)
        val ageText = if (age != null) " (сегодня исполняется $age)" else ""

        """
        🎉🎂 С Днем Рождения! 🎂🎉
        
        Сегодня день рождения у ${user.fullName}$ageText!
        
        Давайте пожелаем ему/ей прекрасного дня! 🎈
        """.trimIndent()
    } else {
        val usersList = birthdayUsers.joinToString("\n") { user ->
            val age = calculateAge(user.birthDate)
            val ageText = if (age != null) " (исполняется $age)" else ""
            "• ${user.fullName}$ageText"
        }

        """
        🎉🎂 С Днем Рождения! 🎂🎉
        
        Сегодня мы празднуем ${birthdayUsers.size} дня рождения:
        
        $usersList
        
        Давайте пожелаем им всем прекрасного дня! 🎈
        """.trimIndent()
    }
}

/**
 * Calculate age from birth date string
 * Returns null if the year is 0000 (malformed date) or if parsing fails
 */
private fun calculateAge(birthDateStr: String): Int? {
    return try {
        // Check if year is 0000 (malformed date)
        if (birthDateStr.startsWith("0000-")) {
            return null
        }

        val birthDate = LocalDate.parse(birthDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val today = LocalDate.now()
        ChronoUnit.YEARS.between(birthDate, today).toInt()
    } catch (_: Exception) {
        null
    }
}

/**
 * Check if a newly added user has a birthday today and notify if so
 * @param bot The Telegram bot instance
 * @param chatId The chat ID to notify
 * @param userId The database ID of the user that was added
 */
suspend fun checkBirthdayForNewUser(bot: TelegramBot, chatId: Long, userId: Int) {
    logger.info { "Checking birthday for newly added user $userId in chat $chatId" }

    val today = LocalDate.now()
    val todayFormatted = today.format(DateTimeFormatter.ofPattern("MM-dd"))

    val birthdayUser = transaction {
        Users.selectAll()
            .where { Users.id eq userId }
            .singleOrNull()
            ?.let { userRow ->
                if (checkBirthdayMatch(userRow[Users.birthDate], todayFormatted)) {
                    BirthdayPerson(
                        fullName = userRow[Users.fullName],
                        birthDate = userRow[Users.birthDate] ?: "",
                        groupName = "Дополнительный"
                    )
                } else null
            }
    }

    if (birthdayUser != null) {
        logger.info { "Newly added user ${birthdayUser.fullName} has birthday today!" }
        sendBirthdayNotification(bot, chatId, listOf(birthdayUser))
    } else {
        logger.debug { "Newly added user $userId does not have birthday today" }
    }
}

/**
 * Check if any users in a newly added group have birthdays today and notify if so
 * @param bot The Telegram bot instance
 * @param chatId The chat ID to notify
 * @param groupName The name of the group that was added
 */
suspend fun checkBirthdaysForNewGroup(bot: TelegramBot, chatId: Long, groupName: String) {
    logger.info { "Checking birthdays for newly added group '$groupName' in chat $chatId" }

    val today = LocalDate.now()
    val todayFormatted = today.format(DateTimeFormatter.ofPattern("MM-dd"))

    val birthdayUsers = transaction {
        Users.selectAll()
            .where {
                (Users.description ilike "%$groupName%") and
                        (Users.type eq "STUDENT")
            }
            .filter { userRow ->
                checkBirthdayMatch(userRow[Users.birthDate], todayFormatted)
            }
            .map { userRow ->
                BirthdayPerson(
                    fullName = userRow[Users.fullName],
                    birthDate = userRow[Users.birthDate] ?: "",
                    groupName = groupName
                )
            }
    }

    if (birthdayUsers.isNotEmpty()) {
        logger.info { "Found ${birthdayUsers.size} birthday(s) in newly added group '$groupName'" }
        sendBirthdayNotification(bot, chatId, birthdayUsers)
    } else {
        logger.debug { "No birthdays found in newly added group '$groupName' today" }
    }
}

/**
 * Data class to hold birthday person information
 */
private data class BirthdayPerson(
    val fullName: String,
    val birthDate: String,
    val groupName: String
)
