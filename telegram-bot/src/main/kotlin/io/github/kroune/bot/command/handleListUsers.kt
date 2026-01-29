package io.github.kroune.bot.command

import db.Users
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.api.message.editText
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chat.Chat
import eu.vendeli.tgbot.types.component.ProcessedUpdate
import io.github.kroune.bot.ListUserSession
import io.github.kroune.bot.getAdditionalUsers
import io.github.kroune.bot.getInternalChatId
import io.github.kroune.bot.getTargetGroups
import io.github.kroune.bot.guard.BotStartedGuard
import io.github.kroune.bot.ilike
import io.github.kroune.bot.listUserSessions
import io.github.kroune.bot.model.UserInfo
import io.github.kroune.bot.model.UserWrapper
import io.github.kroune.bot.model.toUserInfo
import io.github.kroune.common.logging.Loggers
import io.github.kroune.common.util.DateUtils
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val logger = Loggers.command
private const val USERS_PER_PAGE = 10

@Guard(BotStartedGuard::class)
@CommandHandler(["/listusers"])
suspend fun handleListUsers(user: User, bot: TelegramBot, chat: Chat) {
    logger.info { "User ${user.id} requested user list" }

    runCatching {
        val chatDbId = getInternalChatId(chat)
        val targetGroups = getTargetGroups(chatDbId)
        val userList = transaction {
            // ...existing code for getting users...
            val groupUsers = if (targetGroups.isNotEmpty()) {
                val groupConditions: Op<Boolean> = targetGroups
                    .map { group -> Users.description ilike "%$group%" }
                    .reduce { acc, op -> acc or op }

                Users.selectAll()
                    .where { groupConditions }
                    .map { row ->
                        UserInfo(
                            fullName = row[Users.fullName],
                            email = row[Users.email],
                            type = row[Users.type],
                            description = row[Users.description],
                            birthDate = row[Users.birthDate],
                            source = "Группа: ${
                                targetGroups.find {
                                    row[Users.description].contains(it, true)
                                } ?: "Неизвестно"
                            }"
                        )
                    }
            } else {
                emptyList()
            }

            val additionalUsers = getAdditionalUsers(chatDbId).map { userId ->
                UserWrapper(userId).toUserInfo("Дополнительный")
            }

            (groupUsers + additionalUsers).toSet()
        }

        if (userList.isEmpty()) {
            message {
                """
                📋 Ваш список уведомлений пуст.
                
                Используйте /addgroup, чтобы добавить группу, или /adduser, чтобы добавить отдельных пользователей.
                """.trimIndent()
            }.send(chat, bot)
            return
        }

        val sortedList = userList.sortedBy { it.birthDate }
        displayUserListPage(chat.id, bot, sortedList, 0, null)
        listUserSessions[chat.id] = ListUserSession(sortedList, 0, null)

    }.onFailure { e ->
        logger.error(e) { "Error listing users: ${e.message}" }
        message {
            "❌ Произошла ошибка при выводе списка пользователей. Пожалуйста, попробуйте еще раз."
        }.send(chat, bot)
    }
}

private suspend fun displayUserListPage(
    chatId: Long,
    bot: TelegramBot,
    userList: List<UserInfo>,
    page: Int,
    messageId: Long?
) {
    val totalPages = (userList.size + USERS_PER_PAGE - 1) / USERS_PER_PAGE
    val startIdx = page * USERS_PER_PAGE
    val endIdx = minOf(startIdx + USERS_PER_PAGE, userList.size)
    val pageUsers = userList.subList(startIdx, endIdx)

    val userListText = pageUsers.mapIndexed { idx, userInfo ->
        val globalIdx = startIdx + idx + 1
        val typeEmoji = if (userInfo.type == "STUDENT") "🎓" else "👔"
        val birthDateText = userInfo.birthDate?.let {
            DateUtils.formatBirthDate(it)?.let { formatted -> "🎂 $formatted" }
        } ?: ""

        """
        $globalIdx. $typeEmoji ${userInfo.fullName}
           📧 ${userInfo.email}
           📍 ${userInfo.source}
           $birthDateText
        """.trimIndent()
    }.joinToString("\n\n")

    val pageInfo = if (totalPages > 1) "\n\n📄 Страница ${page + 1} из $totalPages" else ""

    val text = """
📋 Пользователи в вашем списке уведомлений (${userList.size}):
        
$userListText
$pageInfo
        """.trimIndent()

    if (messageId != null) {
        editText(messageId) {
            text
        }.inlineKeyboardMarkup {
            if (totalPages > 1) {
                if (page > 0) {
                    "⬅️ Назад" callback "listusers_prev"
                }
                if (page < totalPages - 1) {
                    "Вперед ➡️" callback "listusers_next"
                }
            }
        }.send(chatId, bot)
    } else {
        message {
            text
        }.inlineKeyboardMarkup {
            if (totalPages > 1) {
                if (page > 0) {
                    "⬅️ Назад" callback "listusers_prev"
                }
                if (page < totalPages - 1) {
                    "Вперед ➡️" callback "listusers_next"
                }
            }
        }.send(chatId, bot)
    }
}

@CommandHandler.CallbackQuery(["listusers_prev", "listusers_next"], autoAnswer = true)
suspend fun handleListUsersPagination(update: ProcessedUpdate, bot: TelegramBot, chat: Chat) {
    val chatId = chat.id
    val session = listUserSessions[chatId]

    if (session == null) {
        message {
            "❌ Сессия истекла. Пожалуйста, используйте /listusers, чтобы снова просмотреть список."
        }.send(chat, bot)
        return
    }

    val messageId = update.origin.callbackQuery?.message?.messageId

    if (messageId == null) {
        message {
            "❌ Не удалось отредактировать сообщение. Пожалуйста, используйте /listusers, чтобы снова просмотреть список."
        }.send(chat, bot)
        return
    }

    val newPage = when (update.text) {
        "listusers_prev" -> {
            if (session.currentPage > 0) session.currentPage - 1 else session.currentPage
        }
        "listusers_next" -> {
            val totalPages = (session.userList.size + USERS_PER_PAGE - 1) / USERS_PER_PAGE
            if (session.currentPage < totalPages - 1) session.currentPage + 1 else session.currentPage
        }
        else -> session.currentPage
    }

    listUserSessions[chatId] = session.copy(currentPage = newPage, messageId = messageId)
    displayUserListPage(chatId, bot, session.userList, newPage, messageId)
}
