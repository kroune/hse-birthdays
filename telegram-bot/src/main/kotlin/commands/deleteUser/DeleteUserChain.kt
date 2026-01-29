package commands.deleteUser

import commands.BotStartedGuard
import model.UserWrapper
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.annotations.InputChain
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chain.ChainLink
import eu.vendeli.tgbot.types.component.ProcessedUpdate
import eu.vendeli.tgbot.types.component.getChat
import eu.vendeli.tgbot.utils.common.setChain
import getInternalChatId
import io.github.kroune.logger
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Guard(BotStartedGuard::class)
@InputChain
object DeleteUserChain {
    object UserSelection : ChainLink() {
        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            val text = update.text.trim()
            val chat = update.getChat()

            if (text.equals("отмена", ignoreCase = true)) {
                logger.info { "User ${user.id} cancelled user deletion" }

                message {
                    """
                    ❌ Удаление пользователя отменено.
                    
                    Используйте /deleteuser, чтобы попробовать снова.
                    """.trimIndent()
                }.send(chat, bot)
                return
            }

            val chatDbId = getInternalChatId(chat)

            // Get list of additional users
            val additionalUsers = transaction {
                BirthdayChatAdditionalUsers.selectAll()
                    .where { BirthdayChatAdditionalUsers.birthdayChat eq chatDbId }
                    .mapNotNull { additionalRow ->
                        val userId = additionalRow[BirthdayChatAdditionalUsers.user].value
                        UserWrapper(userId).fullName to additionalRow[BirthdayChatAdditionalUsers.id]
                    }
            }

            // Try to find the user by number or name
            val userToDelete = try {
                val index = text.toInt() - 1
                if (index >= 0 && index < additionalUsers.size) {
                    additionalUsers[index]
                } else {
                    null
                }
            } catch (_: NumberFormatException) {
                additionalUsers.find { it.first.equals(text, ignoreCase = true) }
            }

            if (userToDelete == null) {
                logger.warn { "User ${user.id} entered invalid user selection: $text" }

                message {
                    """
                    ❌ Пользователь не найден. Пожалуйста, введите корректный номер или имя пользователя.
                    
                    Доступные пользователи:
                    ${
                        additionalUsers
                            .mapIndexed { idx, (fullName, _) -> "${idx + 1}. $fullName" }
                            .joinToString("\n")
                    }
                    
                    Введите номер или имя, или введите "отмена" для отмены.
                    """.trimIndent()
                }.send(chat, bot)

                // Re-prompt the user - stay in this chain link
                bot.inputListener.setChain(user, UserSelection)
                return
            }

            logger.info { "User ${user.id} deleting additional user: ${userToDelete.first}" }

            // Delete the user from BirthdayChatAdditionalUsers
            transaction {
                BirthdayChatAdditionalUsers.deleteWhere { BirthdayChatAdditionalUsers.id eq userToDelete.second }
            }

            logger.info { "User ${user.id} successfully deleted additional user: ${userToDelete.first}" }

            message {
                """
                ✅ Пользователь успешно удален!
                
                Пользователь "${userToDelete.first}" был удален из вашего списка уведомлений.
                Вы больше не будете получать уведомления о его дне рождения.
                
                Используйте /listusers, чтобы увидеть обновленный список пользователей.
                """.trimIndent()
            }.send(chat, bot)
        }
    }
}