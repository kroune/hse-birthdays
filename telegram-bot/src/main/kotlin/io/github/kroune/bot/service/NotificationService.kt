package io.github.kroune.bot.service

import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.api.message.message
import io.github.kroune.bot.table.BirthdayChats
import io.github.kroune.common.logging.Loggers
import io.github.kroune.common.util.DateUtils
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

private val logger = Loggers.scheduler

/**
 * Service for sending birthday notifications
 */
object NotificationService {

    /**
     * Send birthday notification to a chat
     */
    suspend fun sendBirthdayNotification(
        bot: TelegramBot,
        chatId: Long,
        birthdayUsers: List<BirthdayPerson>
    ) {
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
     * Build the birthday notification message
     */
    fun buildBirthdayMessage(birthdayUsers: List<BirthdayPerson>): String {
        return if (birthdayUsers.size == 1) {
            val user = birthdayUsers.first()
            val age = DateUtils.calculateAge(user.birthDate)
            val ageText = if (age != null) " (сегодня исполняется $age)" else ""

            """
            🎉🎂 С Днем Рождения! 🎂🎉
            
            Сегодня день рождения у ${user.fullName}$ageText!
            
            Давайте пожелаем ему/ей прекрасного дня! 🎈
            """.trimIndent()
        } else {
            val usersList = birthdayUsers.joinToString("\n") { user ->
                val age = DateUtils.calculateAge(user.birthDate)
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
     * Handle errors when sending messages to a chat
     */
    private fun handleSendMessageError(e: Throwable, chatId: Long, birthdayUsers: List<BirthdayPerson>) {
        val errorMessage = e.message ?: "Unknown error"

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
}
