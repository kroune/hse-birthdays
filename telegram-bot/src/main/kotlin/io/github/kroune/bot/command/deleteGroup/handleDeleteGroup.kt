package io.github.kroune.bot.command.deleteGroup

import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chat.Chat
import io.github.kroune.bot.getInternalChatId
import io.github.kroune.bot.getTargetGroups
import io.github.kroune.bot.guard.BotStartedGuard
import io.github.kroune.common.logging.Loggers

private val logger = Loggers.command

@Guard(BotStartedGuard::class)
@CommandHandler(["/deletegroup"])
suspend fun handleDeleteGroup(user: User, bot: TelegramBot, chat: Chat) {
    logger.info { "User ${user.id} started group deletion" }

    val chatDbId = getInternalChatId(chat)

    try {
        val targetGroups = getTargetGroups(chatDbId)

        if (targetGroups.isEmpty()) {
            message {
                """
                ℹ️ У вас нет сохраненных групп для удаления.

                Используйте /addgroup, чтобы добавить группу.
                """.trimIndent()
            }.send(chat, bot)
            return
        }

        message {
            """
            📚 Выберите группу для удаления:

            ${targetGroups.mapIndexed { idx, group -> "${idx + 1}. $group" }.joinToString("\n")}

            Введите номер группы или название группы, которую вы хотите удалить, или введите "отмена" для отмены.
            """.trimIndent()
        }.send(chat, bot)

        bot.inputListener[user] = DeleteGroupChain.GroupSelection
    } catch (e: Exception) {
        logger.error(e) { "Error in deletegroup command: ${e.message}" }
        message { "❌ Произошла ошибка при удалении группы. Пожалуйста, попробуйте еще раз." }.send(chat, bot)
    }
}
