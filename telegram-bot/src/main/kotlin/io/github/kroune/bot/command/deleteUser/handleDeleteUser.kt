package io.github.kroune.bot.command.deleteUser

import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chat.Chat
import io.github.kroune.bot.getAdditionalUsers
import io.github.kroune.bot.getInternalChatId
import io.github.kroune.bot.guard.BotStartedGuard
import io.github.kroune.bot.model.UserWrapper
import io.github.kroune.common.logging.Loggers

private val logger = Loggers.command

@Guard(BotStartedGuard::class)
@CommandHandler(["/deleteuser"])
suspend fun handleDeleteUser(user: User, bot: TelegramBot, chat: Chat) {
    logger.info { "User ${user.id} started additional user deletion" }

    val chatDbId = getInternalChatId(chat)

    try {
        val additionalUsers = getAdditionalUsers(chatDbId).map { userId ->
            UserWrapper(userId).fullName
        }

        if (additionalUsers.isEmpty()) {
            message {
                """
                ℹ️ У вас нет дополнительных пользователей для удаления.

                Используйте /adduser, чтобы добавить пользователей.
                """.trimIndent()
            }.send(chat, bot)
            return
        }

        message {
            """
            👥 Выберите пользователя для удаления:

            ${additionalUsers.mapIndexed { index, fullName -> "${index + 1}. $fullName" }.joinToString("\n")}

            Введите номер пользователя или его имя, которого вы хотите удалить, или введите "отмена" для отмены.
            """.trimIndent()
        }.send(chat, bot)

        bot.inputListener[user] = DeleteUserChain.UserSelection
    } catch (e: Exception) {
        logger.error(e) { "Error in deleteuser command: ${e.message}" }
        message { "❌ Произошла ошибка при удалении пользователя. Пожалуйста, попробуйте еще раз." }.send(chat, bot)
    }
}
