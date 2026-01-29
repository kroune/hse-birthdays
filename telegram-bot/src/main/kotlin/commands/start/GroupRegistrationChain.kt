package commands.start

import commands.BotStartedGuard
import db.Users
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.annotations.InputChain
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chain.ChainLink
import eu.vendeli.tgbot.types.component.ProcessedUpdate
import eu.vendeli.tgbot.utils.common.setChain
import getInternalChatId
import ilike
import io.github.kroune.logger
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Guard(BotStartedGuard::class)
@InputChain
object GroupRegistrationChain {
    object GroupName : ChainLink() {
        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            val chat = update.origin.message!!.chat
            val chatDbId = getInternalChatId(chat)
            val text = update.text.trim()

            if (text.equals("пропустить", ignoreCase = true)) {
                logger.info { "User ${user.id} skipped group registration" }

                message {
                    """
                    ✅ Чат успешно зарегистрирован!
                    
                    Вы можете снова запустить бота позже, чтобы добавить название группы.
                    """.trimIndent()
                }.send(chat, bot)
                return
            }

            // Check if group exists in Users table
            val groupExists = transaction {
                Users.selectAll()
                    .where { Users.description ilike "%$text%" }
                    .count() > 0
            }

            if (!groupExists) {
                logger.warn { "Invalid group name entered: $text" }

                message {
                    """
                    ❌ Группа "$text" не найдена в базе данных.
                    
                    Пожалуйста, введите действительное название группы или введите "пропустить", чтобы пропустить этот шаг.
                    """.trimIndent()
                }.send(chat, bot)

                // Re-prompt the user - stay in this chain link
                bot.inputListener.setChain(user, GroupName)
                return
            }

            logger.info { "User ${user.id} entered group name: $text" }

            // Add group to BirthdayChatTargetGroups
            transaction {
                // Check if group is already added
                val existingGroup = BirthdayChatTargetGroups.selectAll()
                    .where {
                        (BirthdayChatTargetGroups.birthdayChat eq chatDbId) and
                                (BirthdayChatTargetGroups.targetGroup eq text)
                    }
                    .singleOrNull()

                if (existingGroup == null) {
                    BirthdayChatTargetGroups.insert {
                        it[BirthdayChatTargetGroups.birthdayChat] = chatDbId
                        it[BirthdayChatTargetGroups.targetGroup] = text
                    }
                }
            }

            logger.info { "User ${user.id} successfully registered group: $text" }

            message {
                """
                ✅ Регистрация завершена!
                
                Группа "$text" была добавлена в ваш чат.
                Теперь вы будете получать уведомления о днях рождения людей в этой группе.
                """.trimIndent()
            }.send(chat, bot)
        }
    }
}