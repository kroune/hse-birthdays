package io.github.kroune.bot.command.addGroup

import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.annotations.InputHandler
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.component.ProcessedUpdate
import io.github.kroune.bot.checkGroupExistence
import io.github.kroune.bot.guard.BotStartedGuard
import io.github.kroune.bot.scheduler.checkBirthdaysForNewGroup
import io.github.kroune.bot.table.BirthdayChatTargetGroups
import io.github.kroune.bot.table.BirthdayChats
import io.github.kroune.common.logging.Loggers
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val logger = Loggers.command

object AddGroupChain {
    const val GroupName = "addgroup:group_name"
}

@Guard(BotStartedGuard::class)
@InputHandler([AddGroupChain.GroupName])
suspend fun handleAddGroupGroupName(update: ProcessedUpdate, user: User, bot: TelegramBot) {
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

        bot.inputListener[user] = AddGroupChain.GroupName
        return
    }

    logger.info { "User ${user.id} adding group: $text" }

    val alreadyExists = transaction {
        val chatDbId = BirthdayChats.selectAll()
            .where { BirthdayChats.telegramChatId eq chatId }
            .single()[BirthdayChats.id]

        val existingGroup = BirthdayChatTargetGroups.selectAll()
            .where {
                (BirthdayChatTargetGroups.birthdayChat eq chatDbId) and
                        (BirthdayChatTargetGroups.targetGroup eq text)
            }
            .singleOrNull()

        if (existingGroup == null) {
            BirthdayChatTargetGroups.insert {
                it[birthdayChat] = chatDbId
                it[targetGroup] = text
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
        return
    }

    logger.info { "User ${user.id} successfully added group: $text" }
    message {
        """
        ✅ Группа успешно добавлена!

        Группа "$text" была добавлена в ваш чат.
        Теперь вы будете получать уведомления о днях рождения людей в этой группе.

        Используйте /listusers, чтобы увидеть всех отслеживаемых пользователей.
        """.trimIndent()
    }.send(chatId, bot)

    checkBirthdaysForNewGroup(bot, chatId, text)
}
