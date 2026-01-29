@file:OptIn(ExperimentalAtomicApi::class)

package commands

import BirthdayChatAdditionalUsers
import BirthdayChatTargetGroups
import BirthdayChats
import commands.BotStartedGuard
import TTLCache
import checkBirthdayForNewUser
import db.Users
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.annotations.InputChain
import eu.vendeli.tgbot.api.message.deleteMessage
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chain.ChainLink
import eu.vendeli.tgbot.types.chat.Chat
import eu.vendeli.tgbot.types.component.ProcessedUpdate
import eu.vendeli.tgbot.types.component.getChat
import eu.vendeli.tgbot.types.component.getOrNull
import eu.vendeli.tgbot.utils.common.setChain
import formatBirthDate
import ilike
import io.github.kroune.logger
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val RESULTS_PER_PAGE = 5

// Storage for search sessions - TTL-based cache with 1 hour expiration
val searchSessions = TTLCache<Long, SearchSession>(TimeUnit.HOURS.toMillis(1))

data class SearchSession(
    val searchCriteria: MutableMap<String, String> = mutableMapOf(),
    val searchResults: List<SearchResult> = emptyList(),
    val currentPage: Int = 0,
    val lastMenuMessageId: Long? = null
)

data class SearchResult(
    val userId: Int,
    val fullName: String,
    val email: String,
    val type: String,
    val description: String,
    val birthDate: String?
)

@Guard(BotStartedGuard::class)
@CommandHandler(["/adduser"])
suspend fun handleAddUser(user: User, bot: TelegramBot, chat: Chat) {
    logger.info { "User ${user.id} started user search" }

    // Directly display the search menu - the inline keyboard will handle the rest
    displaySearchMenu(chat.id, bot)
}

@InputChain
object UserSearchChain {
    object SearchCriteriaMenu : ChainLink() {
        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            val chatId = update.origin.message?.chat?.id ?: user.id
            displaySearchMenu(chatId, bot)
        }
    }

    object SearchCriteriaInput : ChainLink() {
        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            val input = update.text.trim()
            val chatId = update.getChat().id

            when (input) {
                "firstname" -> {
                    message { "Введите имя:" }.send(chatId, bot)
                    bot.inputListener.setChain(user, FirstNameInput)
                }

                "lastname" -> {
                    message { "Введите фамилию:" }.send(chatId, bot)
                    bot.inputListener.setChain(user, LastNameInput)
                }

                "patronymic" -> {
                    message { "Введите отчество:" }.send(chatId, bot)
                    bot.inputListener.setChain(user, PatronymicInput)
                }

                "email" -> {
                    message { "Введите email:" }.send(chatId, bot)
                    bot.inputListener.setChain(user, EmailInput)
                }

                "group" -> {
                    message { "Введите название группы (например, БДРИП251 или БПМИ25 для всех групп ПМИ 25 года):" }.send(
                        chatId,
                        bot
                    )
                    bot.inputListener.setChain(user, GroupNameInput)
                }

                "type_student" -> {
                    val session = searchSessions.getOrPut(chatId) { SearchSession() }
                    session.searchCriteria["Тип"] = "STUDENT"
                    displaySearchMenu(chatId, bot)
                }

                "type_staff" -> {
                    val session = searchSessions.getOrPut(chatId) { SearchSession() }
                    session.searchCriteria["Тип"] = "STAFF"
                    displaySearchMenu(chatId, bot)
                }

                "search" -> {
                    val session = searchSessions[chatId]
                    if (session == null || session.searchCriteria.isEmpty()) {
                        message { "❌ Пожалуйста, добавьте хотя бы один критерий поиска!" }.send(chatId, bot)
                        displaySearchMenu(chatId, bot)
                    } else {
                        performSearch(user, chatId, bot)
                    }
                }

                "cancel" -> {
                    searchSessions.remove(chatId)
                    message { "Поиск отменен." }.send(chatId, bot)
                }

                else -> {
                    message { "Неверный вариант. Пожалуйста, используйте кнопки." }.send(chatId, bot)
                    displaySearchMenu(chatId, bot)
                }
            }
        }
    }

    object FirstNameInput : ChainLink() {
        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            val chatId = update.origin.message?.chat?.id ?: user.id
            val session = searchSessions.getOrPut(chatId) { SearchSession() }
            session.searchCriteria["Имя"] = update.text.trim()
            displaySearchMenu(chatId, bot)
        }
    }

    object LastNameInput : ChainLink() {
        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            val chatId = update.origin.message?.chat?.id ?: user.id
            val session = searchSessions.getOrPut(chatId) { SearchSession() }
            session.searchCriteria["Фамилия"] = update.text.trim()
            displaySearchMenu(chatId, bot)
        }
    }

    object PatronymicInput : ChainLink() {
        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            val chatId = update.origin.message?.chat?.id ?: user.id
            val session = searchSessions.getOrPut(chatId) { SearchSession() }
            session.searchCriteria["Отчество"] = update.text.trim()
            displaySearchMenu(chatId, bot)
        }
    }

    object EmailInput : ChainLink() {
        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            val chatId = update.origin.message?.chat?.id ?: user.id
            val session = searchSessions.getOrPut(chatId) { SearchSession() }
            session.searchCriteria["Email"] = update.text.trim()
            displaySearchMenu(chatId, bot)
        }
    }

    object GroupNameInput : ChainLink() {
        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            val chatId = update.origin.message?.chat?.id ?: user.id
            val session = searchSessions.getOrPut(chatId) { SearchSession() }
            session.searchCriteria["Группа"] = update.text.trim()
            displaySearchMenu(chatId, bot)
        }
    }

    object TypeInput : ChainLink() {
        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            val input = update.text.trim()
            val chatId = update.origin.message?.chat?.id ?: user.id
            val session = searchSessions.getOrPut(chatId) { SearchSession() }

            when (input) {
                "1" -> {
                    session.searchCriteria["Тип"] = "STUDENT"
                    bot.inputListener.setChain(user, SearchCriteriaMenu)
                }

                "2" -> {
                    session.searchCriteria["Тип"] = "STAFF"
                    bot.inputListener.setChain(user, SearchCriteriaMenu)
                }

                else -> {
                    message { "Неверный вариант. Пожалуйста, введите 1 или 2." }.send(chatId, bot)
                    bot.inputListener.setChain(user, TypeInput)
                }
            }
        }
    }

    object ResultSelection : ChainLink() {
        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            val input = update.text.trim()
            val chatId = update.origin.message?.chat?.id ?: user.id
            val session = searchSessions[chatId] ?: run {
                message { "❌ Сессия истекла. Пожалуйста, начните сначала с /adduser" }.send(chatId, bot)
                return
            }

            val index = input.toIntOrNull()
            if (index != null && index > 0) {
                val globalIndex = (session.currentPage * RESULTS_PER_PAGE) + index - 1
                if (globalIndex < session.searchResults.size) {
                    val selected = session.searchResults[globalIndex]
                    addUserToChat(chatId, selected, bot)
                    searchSessions.remove(chatId)
                } else {
                    message { "❌ Неверный выбор. Пожалуйста, попробуйте еще раз." }.send(chatId, bot)
                    displayResults(user, chatId, bot, session)
                }
            } else {
                message { "❌ Пожалуйста, введите действительный номер, чтобы выбрать человека." }.send(chatId, bot)
                displayResults(user, chatId, bot, session)
            }
        }
    }
}

// Callback handler for inline keyboard buttons
@CommandHandler.CallbackQuery(
    [
        "firstname",
        "lastname",
        "patronymic",
        "email",
        "group",
        "type_student",
        "type_staff",
        "search",
        "cancel",
        "result_prev",
        "result_next",
        "result_back",
        "result_cancel"
    ], autoAnswer = true
)
suspend fun handleSearchCallback(user: User, update: ProcessedUpdate, bot: TelegramBot) {
    val callbackData = update.text
    val chatId = update.origin.callbackQuery?.message?.chat?.id ?: user.id
    val session = searchSessions.getOrPut(chatId) { SearchSession() }

    when (callbackData) {
        "firstname" -> {
            message { "Введите имя:" }.send(chatId, bot)
            bot.inputListener.setChain(user, UserSearchChain.FirstNameInput)
        }

        "lastname" -> {
            message { "Введите фамилию:" }.send(chatId, bot)
            bot.inputListener.setChain(user, UserSearchChain.LastNameInput)
        }

        "patronymic" -> {
            message { "Введите отчество:" }.send(chatId, bot)
            bot.inputListener.setChain(user, UserSearchChain.PatronymicInput)
        }

        "email" -> {
            message { "Введите email:" }.send(chatId, bot)
            bot.inputListener.setChain(user, UserSearchChain.EmailInput)
        }

        "group" -> {
            message { "Введите название группы (например, БДРИП251 или БПМИ25 для всех групп ПМИ 25 года):" }.send(
                chatId,
                bot
            )
            bot.inputListener.setChain(user, UserSearchChain.GroupNameInput)
        }

        "type_student" -> {
            session.searchCriteria["Тип"] = "STUDENT"
            displaySearchMenu(chatId, bot)
        }

        "type_staff" -> {
            session.searchCriteria["Тип"] = "STAFF"
            displaySearchMenu(chatId, bot)
        }

        "search" -> {
            if (session.searchCriteria.isEmpty()) {
                message { "❌ Пожалуйста, добавьте хотя бы один критерий поиска!" }.send(chatId, bot)
                displaySearchMenu(chatId, bot)
            } else {
                performSearch(user, chatId, bot)
            }
        }

        "cancel" -> {
            searchSessions.remove(chatId)
            message { "Поиск отменен." }.send(chatId, bot)
        }

        "result_prev" -> {
            if (session.currentPage > 0) {
                val newSession = session.copy(currentPage = session.currentPage - 1)
                searchSessions[chatId] = newSession
                displayResults(user, chatId, bot, newSession)
            }
        }

        "result_next" -> {
            val totalPages = (session.searchResults.size + RESULTS_PER_PAGE - 1) / RESULTS_PER_PAGE
            if (session.currentPage < totalPages - 1) {
                val newSession = session.copy(currentPage = session.currentPage + 1)
                searchSessions[chatId] = newSession
                displayResults(user, chatId, bot, newSession)
            }
        }

        "result_back" -> {
            // Go back to search menu while preserving search criteria
            val preservedCriteria = session.searchCriteria
            searchSessions[chatId] = SearchSession(searchCriteria = preservedCriteria.toMutableMap())
            displaySearchMenu(chatId, bot)
        }

        "result_cancel" -> {
            searchSessions.remove(chatId)
            message { "Поиск отменен." }.send(chatId, bot)
        }
    }
}

private suspend fun performSearch(user: User, chatId: Long, bot: TelegramBot) {
    val session = searchSessions[chatId] ?: return

    message { "🔍 Поиск..." }.send(chatId, bot)

    runCatching {
        val results = transaction {
            var query = Users.selectAll()

            // Build WHERE conditions
            val conditions = mutableListOf<Op<Boolean>>()

            session.searchCriteria["Имя"]?.let { firstName ->
                conditions.add(Users.firstName ilike "%${firstName}%")
            }

            session.searchCriteria["Фамилия"]?.let { lastName ->
                conditions.add(Users.lastName ilike "%${lastName}%")
            }

            session.searchCriteria["Отчество"]?.let { patronymic ->
                conditions.add(Users.middleName ilike "%${patronymic}%")
            }

            session.searchCriteria["Email"]?.let { email ->
                conditions.add(Users.email ilike "%${email}%")
            }

            session.searchCriteria["Тип"]?.let { type ->
                conditions.add(Users.type eq type)
            }

            session.searchCriteria["Группа"]?.let { group ->
                // For group search, check description field
                conditions.add(Users.description ilike "%${group}%")
            }

            // Apply all conditions
            if (conditions.isNotEmpty()) {
                query = query.where { conditions.reduce { acc, condition -> acc and condition } }
            }

            // Limit results to prevent overwhelming output
            query.limit(100).map { row ->
                SearchResult(
                    userId = row[Users.id].value,
                    fullName = row[Users.fullName],
                    email = row[Users.email],
                    type = row[Users.type],
                    description = row[Users.description],
                    birthDate = row[Users.birthDate]
                )
            }
        }

        if (results.isEmpty()) {
            message {
                """
                😕 Ничего не найдено.
                
                Попробуйте изменить критерии поиска.
                """.trimIndent()
            }.inlineKeyboardMarkup {
                "🔙 Назад к поиску" callback "result_back"
                "❌ Отмена" callback "result_cancel"
            }.send(chatId, bot)
            // Keep the session so user can go back
            bot.inputListener.setChain(user, UserSearchChain.SearchCriteriaInput)
        } else {
            val newSession = session.copy(searchResults = results, currentPage = 0)
            searchSessions[chatId] = newSession
            logger.info { "Found ${results.size} results for chat $chatId" }
            displayResults(user, chatId, bot, newSession)
        }
    }.onFailure { e ->
        logger.error(e) { "Error during search: ${e.message}" }
        message {
            "❌ Во время поиска произошла ошибка. Пожалуйста, попробуйте еще раз."
        }.inlineKeyboardMarkup {
            "🔙 Назад к поиску" callback "result_back"
            "❌ Отмена" callback "result_cancel"
        }.send(chatId, bot)
        // Keep the session so user can go back
        bot.inputListener.setChain(user, UserSearchChain.SearchCriteriaInput)
    }
}

private suspend fun displayResults(user: User, chatId: Long, bot: TelegramBot, session: SearchSession) {
    val totalPages = (session.searchResults.size + RESULTS_PER_PAGE - 1) / RESULTS_PER_PAGE
    val startIdx = session.currentPage * RESULTS_PER_PAGE
    val endIdx = minOf(startIdx + RESULTS_PER_PAGE, session.searchResults.size)
    val pageResults = session.searchResults.subList(startIdx, endIdx)

    val resultsText = pageResults.mapIndexed { idx, result ->
        val typeEmoji = if (result.type == "STUDENT") "🎓" else "👔"
        val birthDateText = result.birthDate?.let {
            formatBirthDate(it)?.let { formatted -> " | 🎂 $formatted" }
        } ?: ""

        """
${idx + 1}. $typeEmoji ${result.fullName}
   📧 ${result.email}
   ${result.description.take(60)}${if (result.description.length > 60) "..." else ""}$birthDateText
        """.trimIndent()
    }.joinToString("\n\n")

    val pageInfo = if (totalPages > 1) "\n\n📄 Страница ${session.currentPage + 1} из $totalPages" else ""

    message {
        """
Найдено результатов: ${session.searchResults.size}

$resultsText
$pageInfo

Введите номер, чтобы выбрать человека.
        """.trimIndent()
    }.inlineKeyboardMarkup {
        // Navigation buttons
        if (totalPages > 1) {
            if (session.currentPage > 0) {
                "⬅️ Назад" callback "result_prev"
            }
            if (session.currentPage < totalPages - 1) {
                if (session.currentPage > 0) {
                    "Вперед ➡️" callback "result_next"
                } else {
                    "Вперед ➡️" callback "result_next"
                }
            }
            br()
        }
        // Back and cancel buttons
        "🔙 Назад к поиску" callback "result_back"
        "❌ Отмена" callback "result_cancel"
    }.send(chatId, bot)

    bot.inputListener.setChain(user, UserSearchChain.ResultSelection)
}

private suspend fun addUserToChat(chatId: Long, selectedUser: SearchResult, bot: TelegramBot) {
    runCatching {
        val result = transaction {
            val internalChatId = BirthdayChats.select(BirthdayChats.id)
                .where { BirthdayChats.telegramChatId eq chatId }
                .map { it[BirthdayChats.id] }
                .singleOrNull()

            if (internalChatId == null) {
                logger.warn { "Chat $chatId not found in database" }
                return@transaction "not_registered" to null
            }

            // Check if user is already in additional users
            val userAlreadyAdded = BirthdayChatAdditionalUsers.selectAll()
                .where {
                    (BirthdayChatAdditionalUsers.birthdayChat eq internalChatId) and
                            (BirthdayChatAdditionalUsers.user eq selectedUser.userId)
                }
                .empty().not()

            if (userAlreadyAdded) {
                return@transaction "already_added" to null
            }

            val targetGroupsOfChat = BirthdayChatTargetGroups.select(BirthdayChatTargetGroups.targetGroup)
                .where { BirthdayChatTargetGroups.birthdayChat eq internalChatId }
                .map { it[BirthdayChatTargetGroups.targetGroup] }

            // Check if user is already in target groups
            val userInGroup = targetGroupsOfChat.any { group ->
                selectedUser.description.contains(group, ignoreCase = true)
            }

            if (userInGroup) {
                val matchingGroup = targetGroupsOfChat.find { group ->
                    selectedUser.description.contains(group, ignoreCase = true)
                }
                return@transaction "in_target_group" to matchingGroup
            }

            // Add user to additional users
            BirthdayChatAdditionalUsers.insert {
                it[birthdayChat] = internalChatId
                it[user] = selectedUser.userId
            }

            Pair("added", null)
        }

        when (result.first) {
            "not_registered" -> {
                message {
                    "❌ Чат не зарегистрирован. Пожалуйста, сначала используйте /start."
                }.send(chatId, bot)
            }

            "already_added" -> {
                message {
                    """
                    ℹ️ ${selectedUser.fullName} уже есть в вашем списке дополнительных пользователей.
                    """.trimIndent()
                }.send(chatId, bot)
            }

            "in_target_group" -> {
                message {
                    """
                    ℹ️ ${selectedUser.fullName} уже есть в вашем списке уведомлений.
                    
                    Этот пользователь принадлежит к вашей целевой группе: ${result.second}
                    Нет необходимости добавлять его отдельно.
                    """.trimIndent()
                }.send(chatId, bot)
            }

            "added" -> {
                logger.info { "Added user ${selectedUser.userId} to chat $chatId" }
                message {
                    """
                    ✅ Успешно добавлено!
                    
                    ${selectedUser.fullName} был добавлен в ваш список уведомлений о днях рождения.
                    ${selectedUser.birthDate?.let { formatBirthDate(it)?.let { formatted -> "День рождения: $formatted 🎂" } } ?: ""}
                    """.trimIndent()
                }.send(chatId, bot)

                // Check if the newly added user has a birthday today
                checkBirthdayForNewUser(bot, chatId, selectedUser.userId)
            }
        }
    }.onFailure { e ->
        logger.error(e) { "Error adding user to chat: ${e.message}" }
        message {
            "❌ Произошла ошибка при добавлении пользователя. Пожалуйста, попробуйте еще раз."
        }.send(chatId, bot)
    }
}


internal suspend fun displaySearchMenu(chatId: Long, bot: TelegramBot) {
    // Ensure cleanup task is started on first use
    ensureSearchSessionsCleanupStarted()

    val session = searchSessions.getOrPut(chatId) { SearchSession() }

    // Delete old menu message if it exists
    session.lastMenuMessageId?.let { messageId ->
        try {
            deleteMessage(messageId).send(chatId, bot)
        } catch (e: Exception) {
            logger.warn { "Failed to deleteUser old menu message: ${e.message}" }
        }
    }

    val criteriaText = if (session.searchCriteria.isEmpty()) {
        "Критерии поиска не выбраны."
    } else {
        "Текущие критерии:\n" + session.searchCriteria.entries.joinToString("\n") { (k, v) ->
            "• $k: $v"
        }
    }

    val sentMessage = message {
        """
        🔍 Поиск человека для добавления
        
        $criteriaText
        
        Выберите, по какому критерию вы хотите искать:
        """.trimIndent()
    }.inlineKeyboardMarkup {
        "👤 Имя" callback "firstname"
        "👥 Фамилия" callback "lastname"
        br()
        "📝 Отчество" callback "patronymic"
        "📧 Email" callback "email"
        br()
        "👨‍🎓 Название группы" callback "group"
        br()
        "🎓 Студент" callback "type_student"
        "👔 Сотрудник" callback "type_staff"
        if (session.searchCriteria.isNotEmpty()) {
            br()
            "🔎 Поиск" callback "search"
        }
        br()
        "❌ Отмена" callback "cancel"
    }.sendReturning(chatId, bot)

    // Update session with new message ID
    sentMessage.getOrNull()?.messageId?.let { messageId ->
        searchSessions[chatId] = session.copy(lastMenuMessageId = messageId)
    }
}


// Flag to ensure cleanup task is started only once
private val searchSessionsCleanupStarted = AtomicBoolean(false)

private fun ensureSearchSessionsCleanupStarted() {
    if (searchSessionsCleanupStarted.compareAndSet(false, true)) {
        searchSessions.startCleanupTask()
    }
}