package io.github.kroune.bot.command.addGroup

import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chat.Chat
import eu.vendeli.tgbot.utils.common.setChain
import io.github.kroune.bot.guard.BotStartedGuard
import io.github.kroune.common.logging.Loggers

private val logger = Loggers.command

@Guard(BotStartedGuard::class)
@CommandHandler(["/addgroup"])
suspend fun handleAddGroup(user: User, bot: TelegramBot, chat: Chat) {
    logger.info { "User ${user.id} started group addition" }

    message {
        """
        📚 Добавление новой группы
        
        Пожалуйста, введите название группы (например, БДРИП251 или БПМИ25 для всех групп ПМИ 25 года) или введите "отмена" для отмены.
        """.trimIndent()
    }.send(chat, bot)

    bot.inputListener.setChain(user, AddGroupChain.GroupName)
}