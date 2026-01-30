package io.github.kroune.bot.command

import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chat.Chat
import io.github.kroune.common.logging.Loggers

private val logger = Loggers.command

@CommandHandler(["/about"])
suspend fun handleAbout(user: User, bot: TelegramBot, chat: Chat) {
    logger.info { "User ${user.id} requested about information" }
    message {
        """
        Бота сделал @LichnyiSvetM. Если у вас есть идеи или предложения - пишите в лс.
        Ссылка на исходники на github - https://github.com/kroune/hse-birthdays/
        """.trimIndent()
    }.send(chat, bot)
}
