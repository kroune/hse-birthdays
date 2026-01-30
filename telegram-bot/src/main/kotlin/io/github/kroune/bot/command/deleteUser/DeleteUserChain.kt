package io.github.kroune.bot.command.deleteUser

import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.annotations.InputHandler
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.component.ProcessedUpdate
import eu.vendeli.tgbot.types.component.getChat
import io.github.kroune.bot.getInternalChatId
import io.github.kroune.bot.guard.BotStartedGuard
import io.github.kroune.bot.model.UserWrapper
import io.github.kroune.bot.table.BirthdayChatAdditionalUsers
import io.github.kroune.common.logging.Loggers
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val logger = Loggers.command

object DeleteUserChain {
    const val UserSelection = "deleteuser:user_selection"
}

@Guard(BotStartedGuard::class)
@InputHandler([DeleteUserChain.UserSelection])
suspend fun handleDeleteUserSelection(update: ProcessedUpdate, user: User, bot: TelegramBot) {
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

    val additionalUsers: List<Pair<String, EntityID<Int>>> = transaction {
        BirthdayChatAdditionalUsers.selectAll()
            .where { BirthdayChatAdditionalUsers.birthdayChat eq chatDbId }
            .mapNotNull { additionalRow ->
                val userId = additionalRow[BirthdayChatAdditionalUsers.user].value
                UserWrapper(userId).fullName to additionalRow[BirthdayChatAdditionalUsers.id]
            }
    }

    val userToDelete = try {
        val index = text.toInt() - 1
        if (index in additionalUsers.indices) additionalUsers[index] else null
    } catch (_: NumberFormatException) {
        additionalUsers.find { it.first.equals(text, ignoreCase = true) }
    }

    if (userToDelete == null) {
        logger.warn { "User ${user.id} entered invalid user selection: $text" }
        message {
            """
            ❌ Пользователь не найден. Пожалуйста, введите корректный номер или имя пользователя.

            Доступные пользователи:
            ${additionalUsers.mapIndexed { idx, (fullName, _) -> "${idx + 1}. $fullName" }.joinToString("\n")}

            Введите номер или имя, или введите "отмена" для отмены.
            """.trimIndent()
        }.send(chat, bot)

        bot.inputListener[user] = DeleteUserChain.UserSelection
        return
    }

    logger.info { "User ${user.id} deleting additional user: ${userToDelete.first}" }

    transaction {
        BirthdayChatAdditionalUsers.deleteWhere { id eq userToDelete.second }
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
