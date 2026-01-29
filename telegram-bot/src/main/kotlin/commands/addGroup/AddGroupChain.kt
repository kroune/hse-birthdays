package commands.addGroup

import checkBirthdaysForNewGroup
import checkGroupExistence
import commands.BotStartedGuard
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.annotations.InputChain
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chain.ChainLink
import eu.vendeli.tgbot.types.component.ProcessedUpdate
import eu.vendeli.tgbot.utils.common.setChain
import io.github.kroune.logger
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Guard(BotStartedGuard::class)
@InputChain
object AddGroupChain {
    object GroupName : ChainLink() {
        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            val text = update.text.trim()
            val chatId = update.origin.message?.chat?.id ?: user.id

            if (text.equals("отмена", ignoreCase = true)) {
                logger.info { "User ${user.id} cancelled group addition" }

                message {
                    """
                    ❌ Добавление группы отменено.
                    
                    Используйте /addgroup, чтобы попробовать снова.
                    """.trimIndent()
                }.send(chatId, bot)
                return
            }

            if (!checkGroupExistence(text)) {
                logger.warn { "Invalid group name entered: $text" }

                message {
                    """
                    ❌ Группа "$text" не найдена в базе данных.
                    
                    Пожалуйста, введите действительное название группы или введите "отмена" для отмены.
                    """.trimIndent()
                }.send(chatId, bot)

                // Re-prompt the user - stay in this chain link
                bot.inputListener.setChain(user, GroupName)
                return
            }

            logger.info { "User ${user.id} adding group: $text" }

            // Add group to BirthdayChatTargetGroups
            val alreadyExists = transaction {
                val chatDbId = BirthdayChats.selectAll()
                    .where { BirthdayChats.telegramChatId eq chatId }
                    .single()[BirthdayChats.id]

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
                    false
                } else {
                    true
                }
            }

            if (alreadyExists) {
                logger.info { "User ${user.id} tried to add group that already exists: $text" }

                message {
                    """
                    ℹ️ Группа "$text" уже находится в вашем списке уведомлений.
                    
                    Используйте /listusers, чтобы увидеть всех отслеживаемых пользователей.
                    """.trimIndent()
                }.send(chatId, bot)
            } else {
                logger.info { "User ${user.id} successfully added group: $text" }

                message {
                    """
                    ✅ Группа успешно добавлена!
                    
                    Группа "$text" была добавлена в ваш чат.
                    Теперь вы будете получать уведомления о днях рождения людей в этой группе.
                    
                    Используйте /listusers, чтобы увидеть всех отслеживаемых пользователей.
                    """.trimIndent()
                }.send(chatId, bot)

                // Check if any users in the newly added group have birthdays today
                checkBirthdaysForNewGroup(bot, chatId, text)
            }
        }
    }
}