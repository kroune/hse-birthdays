package commands

import model.UserInfo
import model.UserWrapper
import db.Users
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.annotations.Guard
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chat.Chat
import getAdditionalUsers
import getInternalChatId
import ilike
import io.github.kroune.logger
import model.toUserInfo
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import parseBirthDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit


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
        val today = LocalDate.now()
        val upcomingList = upcomingBirthdays.mapNotNull { userInfo ->
            val birthDate = userInfo.birthDate?.let { parseBirthDate(it) } ?: return@mapNotNull null
            val nextBirthday = birthDate.withYear(today.year)
            val daysUntil = if (nextBirthday.isBefore(today) || nextBirthday.isEqual(today)) {
                ChronoUnit.DAYS.between(today, nextBirthday.plusYears(1))
            } else {
                ChronoUnit.DAYS.between(today, nextBirthday)
            }
            Triple(userInfo, nextBirthday, daysUntil)
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
            val month = String.format("%02d", nextBirthday.monthValue)
            val day = String.format("%02d", nextBirthday.dayOfMonth)
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