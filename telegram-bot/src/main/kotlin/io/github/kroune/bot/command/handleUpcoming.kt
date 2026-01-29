package io.github.kroune.bot.command

import db.Users
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chat.Chat
import io.github.kroune.bot.getAdditionalUsers
import io.github.kroune.bot.getInternalChatId
import io.github.kroune.bot.guard.BotStartedGuard
import io.github.kroune.bot.ilike
import io.github.kroune.bot.model.UserInfo
import io.github.kroune.bot.model.UserWrapper
import io.github.kroune.bot.model.toUserInfo
import io.github.kroune.bot.table.BirthdayChatTargetGroups
import io.github.kroune.common.logging.Loggers
import io.github.kroune.common.util.DateUtils
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val logger = Loggers.command

@Guard(BotStartedGuard::class)
@CommandHandler(["/upcoming"])
suspend fun handleUpcoming(user: User, bot: TelegramBot, chat: Chat) {
    logger.info { "User ${user.id} requested upcoming birthdays" }
    val chatDbId = getInternalChatId(chat)

    runCatching {
        val upcomingBirthdays = transaction {

            // Get target groups
            val targetGroups = BirthdayChatTargetGroups.select(BirthdayChatTargetGroups.targetGroup)
                .where { BirthdayChatTargetGroups.birthdayChat eq chatDbId }
                .map { it[BirthdayChatTargetGroups.targetGroup] }

            // Get users from target groups
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
                            source = "Группа: ${targetGroups.find { row[Users.description].contains(it, true) }!!}"
                        )
                    }
            } else {
                emptyList()
            }

            // Get additional users
            val additionalUsers = getAdditionalUsers(chatDbId).map { userId ->
                UserWrapper(userId).toUserInfo("Дополнительный")
            }

            (groupUsers + additionalUsers).toSet()
        }

        if (upcomingBirthdays.isEmpty()) {
            message {
                """
                📋 Ваш список уведомлений пуст.
                
                Используйте /addgroup, чтобы добавить группу, или /adduser, чтобы добавить отдельных пользователей.
                """.trimIndent()
            }.send(chat, bot)
            return
        }

        // Calculate days until birthday and sort
        val today = DateUtils.today()
        val upcomingList = upcomingBirthdays.mapNotNull { userInfo ->
            val birthDate = userInfo.birthDate?.let { DateUtils.parseBirthDate(it) } ?: return@mapNotNull null
            val daysUntil = DateUtils.daysUntilBirthday(userInfo.birthDate) ?: return@mapNotNull null
            Triple(userInfo, birthDate, daysUntil)
        }.sortedBy { it.third }.take(10)

        if (upcomingList.isEmpty()) {
            message {
                """
                📅 Ближайшие дни рождения не найдены.
                
                У некоторых пользователей могут быть неверные даты рождения.
                """.trimIndent()
            }.send(chat, bot)
            return
        }

        val upcomingText = upcomingList.mapIndexed { index, (userInfo, nextBirthday, daysUntil) ->
            val typeEmoji = if (userInfo.type == "STUDENT") "🎓" else "👔"
            val month = nextBirthday.monthValue.toString().padStart(2, '0')
            val day = nextBirthday.dayOfMonth.toString().padStart(2, '0')
            val daysText = when (daysUntil) {
                0L -> "🎉 СЕГОДНЯ!"
                1L -> "⏰ Завтра"
                else -> "📅 Через $daysUntil дней"
            }

            """
            ${index + 1}. $typeEmoji ${userInfo.fullName}
               🎂 $day.$month - $daysText
               📍 ${userInfo.source}
            """.trimIndent()
        }.joinToString("\n\n")

        message {
            """
📅 Следующие 10 ближайших дней рождения:
            
$upcomingText
            """.trimIndent()
        }.send(chat, bot)
    }.onFailure { e ->
        logger.error(e) { "Error getting upcoming birthdays: ${e.message}" }
        message {
            "❌ Произошла ошибка при получении ближайших дней рождения. Пожалуйста, попробуйте еще раз."
        }.send(chat, bot)
    }
}