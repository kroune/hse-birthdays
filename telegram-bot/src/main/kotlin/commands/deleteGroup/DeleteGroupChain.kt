package commands.deleteGroup

import BirthdayChatTargetGroups
import commands.BotStartedGuard
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
import getTargetGroups
import io.github.kroune.logger
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Guard(BotStartedGuard::class)
@InputChain
object DeleteGroupChain {
    object GroupSelection : ChainLink() {
        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            val chat = update.getChat()
            val chatDbId = getInternalChatId(chat)
            val text = update.text.trim()

            if (text.equals("отмена", ignoreCase = true)) {
                logger.info { "User ${user.id} cancelled group deletion" }

                message {
                    """
                    ❌ Удаление группы отменено.
                    
                    Используйте /deletegroup, чтобы попробовать снова.
                    """.trimIndent()
                }.send(chat, bot)
                return
            }

            // Get list of target groups
            val targetGroups = getTargetGroups(chatDbId)

            // Try to find the group by number or name
            val groupToDelete = try {
                val index = text.toInt() - 1
                if (index >= 0 && index < targetGroups.size) {
                    targetGroups[index]
                } else {
                    null
                }
            } catch (_: NumberFormatException) {
                targetGroups.find { it.equals(text, ignoreCase = true) }
            }

            if (groupToDelete == null) {
                logger.warn { "User ${user.id} entered invalid group selection: $text" }

                message {
                    """
                    ❌ Группа не найдена. Пожалуйста, введите корректный номер или название группы.
                    
                    Доступные группы:
                    ${targetGroups.mapIndexed { idx, group -> "${idx + 1}. $group" }.joinToString("\n")}
                    
                    Введите номер или название, или введите "отмена" для отмены.
                    """.trimIndent()
                }.send(chat, bot)

                // Re-prompt the user - stay in this chain link
                bot.inputListener.setChain(user, GroupSelection)
                return
            }

            logger.info { "User ${user.id} deleting group: $groupToDelete" }

            // Delete the group from BirthdayChatTargetGroups
            val deletedCount = transaction {
                BirthdayChatTargetGroups.deleteWhere {
                    (BirthdayChatTargetGroups.birthdayChat eq chatDbId) and
                            (BirthdayChatTargetGroups.targetGroup eq groupToDelete)
                }
            }

            logger.info { "User ${user.id} successfully deleted group: $groupToDelete (deleted $deletedCount row(s))" }

            message {
                """
                ✅ Группа успешно удалена!
                
                Группа "$groupToDelete" была удалена из вашего списка уведомлений.
                Вы больше не будете получать уведомления о днях рождения людей в этой группе.
                
                Используйте /listusers, чтобы увидеть обновленный список пользователей.
                """.trimIndent()
            }.send(chat, bot)
        }
    }
}