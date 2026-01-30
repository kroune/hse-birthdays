@file:OptIn(ExperimentalAtomicApi::class)

package io.github.kroune.bot.command

import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.annotations.InputHandler
import eu.vendeli.tgbot.api.message.deleteMessage
import eu.vendeli.tgbot.api.message.editText
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chat.Chat
import eu.vendeli.tgbot.types.component.ProcessedUpdate
import eu.vendeli.tgbot.types.component.getOrNull
import io.github.kroune.bot.cache.TTLCache
import io.github.kroune.bot.guard.BotStartedGuard
import io.github.kroune.bot.scheduler.checkBirthdayForNewUser
import io.github.kroune.bot.service.SearchResult
import io.github.kroune.bot.service.SearchService
import io.github.kroune.bot.table.BirthdayChatAdditionalUsers
import io.github.kroune.bot.table.BirthdayChatTargetGroups
import io.github.kroune.bot.table.BirthdayChats
import io.github.kroune.common.logging.Loggers
import io.github.kroune.common.util.DateUtils
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private val logger = Loggers.search
private const val RESULTS_PER_PAGE = 5

data class SearchSession(
    val searchCriteria: MutableMap<String, String> = mutableMapOf(),
    val searchResults: List<SearchResult> = emptyList(),
    val currentPage: Int = 0,
    val lastMenuMessageId: Long? = null,
    val lastResultMessageId: Long? = null,
)

val searchSessions: TTLCache<Long, SearchSession> = TTLCache(TimeUnit.HOURS.toMillis(1))

object UserSearchState {
    const val FirstNameInput = "adduser:first_name"
    const val LastNameInput = "adduser:last_name"
    const val PatronymicInput = "adduser:patronymic"
    const val EmailInput = "adduser:email"
    const val GroupNameInput = "adduser:group"
    const val ResultSelection = "adduser:result_selection"
}

@Guard(BotStartedGuard::class)
@CommandHandler(["/adduser"])
suspend fun handleAddUser(user: User, bot: TelegramBot, chat: Chat) {
    logger.info { "User ${user.id} started user search" }
    displaySearchMenu(chat.id, bot)
}

@Guard(BotStartedGuard::class)
@InputHandler([UserSearchState.FirstNameInput])
suspend fun handleFirstNameInput(update: ProcessedUpdate, user: User, bot: TelegramBot) {
    val chatId = update.origin.message?.chat?.id ?: user.id
    val session = searchSessions.getOrPut(chatId) { SearchSession() }
    session.searchCriteria["Имя"] = update.text.trim()
    displaySearchMenu(chatId, bot)
}

@Guard(BotStartedGuard::class)
@InputHandler([UserSearchState.LastNameInput])
suspend fun handleLastNameInput(update: ProcessedUpdate, user: User, bot: TelegramBot) {
    val chatId = update.origin.message?.chat?.id ?: user.id
    val session = searchSessions.getOrPut(chatId) { SearchSession() }
    session.searchCriteria["Фамилия"] = update.text.trim()
    displaySearchMenu(chatId, bot)
}

@Guard(BotStartedGuard::class)
@InputHandler([UserSearchState.PatronymicInput])
suspend fun handlePatronymicInput(update: ProcessedUpdate, user: User, bot: TelegramBot) {
    val chatId = update.origin.message?.chat?.id ?: user.id
    val session = searchSessions.getOrPut(chatId) { SearchSession() }
    session.searchCriteria["Отчество"] = update.text.trim()
    displaySearchMenu(chatId, bot)
}

@Guard(BotStartedGuard::class)
@InputHandler([UserSearchState.EmailInput])
suspend fun handleEmailInput(update: ProcessedUpdate, user: User, bot: TelegramBot) {
    val chatId = update.origin.message?.chat?.id ?: user.id
    val session = searchSessions.getOrPut(chatId) { SearchSession() }
    session.searchCriteria["Email"] = update.text.trim()
    displaySearchMenu(chatId, bot)
}

@Guard(BotStartedGuard::class)
@InputHandler([UserSearchState.GroupNameInput])
suspend fun handleGroupNameInput(update: ProcessedUpdate, user: User, bot: TelegramBot) {
    val chatId = update.origin.message?.chat?.id ?: user.id
    val session = searchSessions.getOrPut(chatId) { SearchSession() }
    session.searchCriteria["Группа"] = update.text.trim()
    displaySearchMenu(chatId, bot)
}

@Guard(BotStartedGuard::class)
@InputHandler([UserSearchState.ResultSelection])
suspend fun handleResultSelection(update: ProcessedUpdate, user: User, bot: TelegramBot) {
    val input = update.text.trim()
    val chatId = update.origin.message?.chat?.id ?: user.id

    val session = searchSessions[chatId] ?: run {
        message { "❌ Сессия истекла. Начните с /adduser" }.send(chatId, bot)
        return
    }

    val index = input.toIntOrNull()
    if (index != null && index > 0) {
        val globalIndex = (session.currentPage * RESULTS_PER_PAGE) + index - 1
        if (globalIndex in session.searchResults.indices) {
            addUserToChat(chatId, session.searchResults[globalIndex], bot)
            searchSessions.remove(chatId)
            return
        }
    }

    message { "❌ Введите корректный номер." }.send(chatId, bot)
    displayResults(user, chatId, bot, session)
}

@Guard(BotStartedGuard::class)
@CommandHandler.CallbackQuery(
    [
        "firstname", "lastname", "patronymic", "email", "group", "type_student", "type_staff",
        "search", "cancel", "result_prev", "result_next", "result_back", "result_cancel",
    ],
    autoAnswer = true,
)
suspend fun handleSearchCallback(user: User, update: ProcessedUpdate, bot: TelegramBot) {
    val chatId = update.origin.callbackQuery?.message?.chat?.id ?: user.id
    val messageId = update.origin.callbackQuery?.message?.messageId
    val session = searchSessions.getOrPut(chatId) { SearchSession() }

    when (update.text) {
        "firstname" -> promptInput(chatId, bot, user, "Введите имя:", UserSearchState.FirstNameInput)
        "lastname" -> promptInput(chatId, bot, user, "Введите фамилию:", UserSearchState.LastNameInput)
        "patronymic" -> promptInput(chatId, bot, user, "Введите отчество:", UserSearchState.PatronymicInput)
        "email" -> promptInput(chatId, bot, user, "Введите email:", UserSearchState.EmailInput)
        "group" -> promptInput(chatId, bot, user, "Введите группу:", UserSearchState.GroupNameInput)

        "type_student" -> {
            session.searchCriteria["Тип"] = "STUDENT"
            displaySearchMenu(chatId, bot)
        }

        "type_staff" -> {
            session.searchCriteria["Тип"] = "STAFF"
            displaySearchMenu(chatId, bot)
        }

        "search" -> if (session.searchCriteria.isEmpty()) {
            message { "❌ Добавьте критерии!" }.send(chatId, bot)
        } else {
            performSearch(user, chatId, bot)
        }

        "cancel", "result_cancel" -> {
            searchSessions.remove(chatId)
            message { "Поиск отменен." }.send(chatId, bot)
        }

        "result_prev" -> navigatePage(user, chatId, bot, session, -1, messageId)
        "result_next" -> navigatePage(user, chatId, bot, session, 1, messageId)

        "result_back" -> {
            searchSessions[chatId] = SearchSession(
                searchCriteria = session.searchCriteria.toMutableMap(),
            )
            displaySearchMenu(chatId, bot)
        }
    }
}

private suspend fun promptInput(
    chatId: Long,
    bot: TelegramBot,
    user: User,
    prompt: String,
    state: String,
) {
    message { prompt }.send(chatId, bot)
    bot.inputListener[user] = state
}

private suspend fun navigatePage(
    user: User,
    chatId: Long,
    bot: TelegramBot,
    session: SearchSession,
    delta: Int,
    messageId: Long? = null,
) {
    val totalPages = (session.searchResults.size + RESULTS_PER_PAGE - 1) / RESULTS_PER_PAGE
    val newPage = (session.currentPage + delta).coerceIn(0, totalPages - 1)

    if (newPage != session.currentPage) {
        val newSession = session.copy(currentPage = newPage, lastResultMessageId = messageId)
        searchSessions[chatId] = newSession
        displayResults(user, chatId, bot, newSession, messageId)
    }
}

private suspend fun performSearch(user: User, chatId: Long, bot: TelegramBot) {
    val session = searchSessions[chatId] ?: return

    message { "🔍 Поиск..." }.send(chatId, bot)

    runCatching {
        val results = SearchService.search(session.searchCriteria)

        if (results.isEmpty()) {
            message { "😕 Ничего не найдено." }.inlineKeyboardMarkup {
                "🔙 Назад" callback "result_back"
            }.send(chatId, bot)
        } else {
            val newSession = session.copy(searchResults = results, currentPage = 0)
            searchSessions[chatId] = newSession
            displayResults(user, chatId, bot, newSession)
        }
    }.onFailure { e ->
        logger.error(e) { "Search error" }
        message { "❌ Ошибка поиска." }.send(chatId, bot)
    }
}

private suspend fun displayResults(
    user: User,
    chatId: Long,
    bot: TelegramBot,
    session: SearchSession,
    messageId: Long? = null,
) {
    val totalPages = (session.searchResults.size + RESULTS_PER_PAGE - 1) / RESULTS_PER_PAGE
    val startIdx = session.currentPage * RESULTS_PER_PAGE
    val endIdx = minOf(startIdx + RESULTS_PER_PAGE, session.searchResults.size)
    val pageResults = session.searchResults.subList(startIdx, endIdx)

    val text = buildString {
        appendLine("Найдено: ${session.searchResults.size}")
        appendLine()

        pageResults.forEachIndexed { idx, r ->
            val emoji = if (r.type == "STUDENT") "🎓" else "👔"
            val birth = r.birthDate
                ?.let { DateUtils.formatBirthDate(it) }
                ?.let { " 🎂$it" }
                ?: ""

            appendLine("${idx + 1}. $emoji ${r.fullName}")
            appendLine(" 📧 ${r.email}$birth")
            appendLine()
        }

        if (totalPages > 1) {
            appendLine("📄 ${session.currentPage + 1}/$totalPages")
        }
        appendLine("Введите номер:")
    }

    if (messageId != null) {
        editText(messageId) { text }.inlineKeyboardMarkup {
            if (totalPages > 1) {
                if (session.currentPage > 0) "⬅️" callback "result_prev"
                if (session.currentPage < totalPages - 1) "➡️" callback "result_next"
                br()
            }

            "🔙 Назад" callback "result_back"
            "❌ Отмена" callback "result_cancel"
        }.send(chatId, bot)
    } else {
        message { text }.inlineKeyboardMarkup {
            if (totalPages > 1) {
                if (session.currentPage > 0) "⬅️" callback "result_prev"
                if (session.currentPage < totalPages - 1) "➡️" callback "result_next"
                br()
            }

            "🔙 Назад" callback "result_back"
            "❌ Отмена" callback "result_cancel"
        }.send(chatId, bot)
    }

    bot.inputListener[user] = UserSearchState.ResultSelection
}

private suspend fun addUserToChat(chatId: Long, selectedUser: SearchResult, bot: TelegramBot) {
    runCatching {
        val result = transaction {
            val internalChatId = BirthdayChats.select(BirthdayChats.id)
                .where { BirthdayChats.telegramChatId eq chatId }
                .singleOrNull()
                ?.get(BirthdayChats.id)
                ?: return@transaction "not_registered" to null

            val alreadyAdded = BirthdayChatAdditionalUsers.selectAll()
                .where {
                    (BirthdayChatAdditionalUsers.birthdayChat eq internalChatId) and
                            (BirthdayChatAdditionalUsers.user eq selectedUser.userId)
                }
                .any()

            if (alreadyAdded) return@transaction "already_added" to null

            val matchingGroup = BirthdayChatTargetGroups.select(BirthdayChatTargetGroups.targetGroup)
                .where { BirthdayChatTargetGroups.birthdayChat eq internalChatId }
                .map { it[BirthdayChatTargetGroups.targetGroup] }
                .find { selectedUser.description.contains(it, ignoreCase = true) }

            if (matchingGroup != null) return@transaction "in_group" to matchingGroup

            BirthdayChatAdditionalUsers.insert {
                it[birthdayChat] = internalChatId
                it[user] = selectedUser.userId
            }

            "added" to null
        }

        when (result.first) {
            "not_registered" -> message { "❌ Используйте /start" }.send(chatId, bot)
            "already_added" -> message { "ℹ️ Уже добавлен." }.send(chatId, bot)
            "in_group" -> message { "ℹ️ Уже в группе ${result.second}" }.send(chatId, bot)
            "added" -> {
                val birth = selectedUser.birthDate
                    ?.let { DateUtils.formatBirthDate(it) }
                    ?.let { "\n🎂 $it" }
                    ?: ""
                message { "✅ ${selectedUser.fullName} добавлен!$birth" }.send(chatId, bot)
                checkBirthdayForNewUser(bot, chatId, selectedUser.userId)
            }
        }
    }.onFailure { e ->
        logger.error(e) { "Add user error" }
        message { "❌ Ошибка." }.send(chatId, bot)
    }
}

internal suspend fun displaySearchMenu(chatId: Long, bot: TelegramBot) {
    ensureSearchSessionsCleanupStarted()

    val session = searchSessions.getOrPut(chatId) { SearchSession() }

    session.lastMenuMessageId?.let { lastId ->
        runCatching { deleteMessage(lastId).send(chatId, bot) }
    }

    val criteriaText = if (session.searchCriteria.isEmpty()) {
        "Критерии не выбраны."
    } else {
        "Критерии:\n" + session.searchCriteria.entries.joinToString("\n") {
            "• ${it.key}: ${it.value}"
        }
    }

    val sent = message { "🔍 Поиск пользователя\n\n$criteriaText" }.inlineKeyboardMarkup {
        "👤 Имя" callback "firstname"; "👥 Фамилия" callback "lastname"; br()
        "📝 Отчество" callback "patronymic"; "📧 Email" callback "email"; br()
        "👨🎓 Группа" callback "group"; br()
        "🎓 Студент" callback "type_student"; "👔 Сотрудник" callback "type_staff"

        if (session.searchCriteria.isNotEmpty()) {
            br(); "🔎 Поиск" callback "search"
        }
        br(); "❌ Отмена" callback "cancel"
    }.sendReturning(chatId, bot)

    sent.getOrNull()?.messageId?.let { msgId ->
        searchSessions[chatId] = session.copy(lastMenuMessageId = msgId)
    }
}

private val cleanupStarted = AtomicBoolean(false)

private fun ensureSearchSessionsCleanupStarted() {
    if (cleanupStarted.compareAndSet(false, true)) {
        searchSessions.startCleanupTask()
    }
}
