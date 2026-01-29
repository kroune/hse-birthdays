package commands

import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.interfaces.helper.Guard
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.component.ProcessedUpdate
import eu.vendeli.tgbot.types.component.chatOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object BotStartedGuard : Guard {
    override suspend fun condition(user: User?, update: ProcessedUpdate, bot: TelegramBot): Boolean {
        val chat = update.chatOrNull ?: return false
        val chatDbId = transaction {
            BirthdayChats.selectAll()
                .where { BirthdayChats.telegramChatId eq chat.id }
                .singleOrNull()
                ?.get(BirthdayChats.id)
        }

        if (chatDbId == null) {
            message {
                """
                ❌ Ваш чат еще не зарегистрирован.
                
                Пожалуйста, сначала используйте /start, чтобы зарегистрировать свой чат.
                """.trimIndent()
            }.send(chat, bot)
            return false
        }
        return true
    }
}