package commands

import checkAndNotifyBirthdays
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chat.Chat
import io.github.kroune.logger

@Guard(BotStartedGuard::class)
@CommandHandler(["/checkbirthdays"])
suspend fun handleCheckBirthdays(user: User, bot: TelegramBot, chat: Chat) {
    logger.info { "User ${user.id} manually triggered birthday check for chat ${chat.id}" }

    message {
        "🔍 Проверяю сегодняшние дни рождения..."
    }.send(chat, bot)

    try {
        checkAndNotifyBirthdays(bot, chat.id)
        message {
            "✅ Проверка дней рождения завершена!"
        }.send(chat, bot)
    } catch (e: Exception) {
        logger.error(e) { "Error during manual birthday check: ${e.message}" }
        message {
            "❌ Ошибка при проверке дней рождения: ${e.message}"
        }.send(chat, bot)
    }
}