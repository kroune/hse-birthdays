package io.github.kroune.bot.command.start

import db.Users
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.annotations.InputHandler
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.component.ProcessedUpdate
import io.github.kroune.bot.getInternalChatId
import io.github.kroune.bot.guard.BotStartedGuard
import io.github.kroune.bot.ilike
import io.github.kroune.bot.table.BirthdayChatTargetGroups
import io.github.kroune.common.logging.Loggers
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val logger = Loggers.command

object GroupRegistrationChain {
    const val GroupName = "start:group_registration_group_name"
}

@Guard(BotStartedGuard::class)
@InputHandler([GroupRegistrationChain.GroupName])
suspend fun handleGroupRegistrationGroupName(update: ProcessedUpdate, user: User, bot: TelegramBot) {
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

        bot.inputListener[user] = GroupRegistrationChain.GroupName
        return
    }

    logger.info { "User ${user.id} entered group name: $text" }

    transaction {
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
