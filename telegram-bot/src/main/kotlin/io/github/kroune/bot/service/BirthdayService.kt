package io.github.kroune.bot.service

import db.Users
import io.github.kroune.bot.ilike
import io.github.kroune.bot.table.BirthdayChatAdditionalUsers
import io.github.kroune.bot.table.BirthdayChatTargetGroups
import io.github.kroune.bot.table.BirthdayChats
import io.github.kroune.bot.model.UserWrapper
import io.github.kroune.common.logging.Loggers
import io.github.kroune.common.util.DateUtils
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val logger = Loggers.scheduler

/**
 * Data class to hold birthday person information
 */
data class BirthdayPerson(
    val fullName: String,
    val birthDate: String,
    val groupName: String
)

/**
 * Service for birthday-related business logic
 */
object BirthdayService {

    /**
     * Find all users with birthdays today for a specific chat
     */
    fun findBirthdayUsersForChat(chatId: Long): List<BirthdayPerson> {
        return transaction {
            val chatRow = BirthdayChats.selectAll()
                .where { BirthdayChats.telegramChatId eq chatId }
                .singleOrNull() ?: return@transaction emptyList()

            val chatDbId = chatRow[BirthdayChats.id]

            // Get target groups for this chat
            val groups = BirthdayChatTargetGroups.selectAll()
                .where { BirthdayChatTargetGroups.birthdayChat eq chatDbId }
                .map { it[BirthdayChatTargetGroups.targetGroup] }

            // Get users from target groups with birthday today
            val groupUsers = if (groups.isNotEmpty()) {
                groups.flatMap { groupName ->
                    Users.selectAll()
                        .where { Users.description ilike "%$groupName%" }
                        .filter { DateUtils.isBirthdayToday(it[Users.birthDate]) }
                        .map {
                            BirthdayPerson(
                                fullName = it[Users.fullName],
                                birthDate = it[Users.birthDate] ?: "",
                                groupName = groupName
                            )
                        }
                }
            } else {
                emptyList()
            }

            // Get manually added users with birthday today
            val additionalUsers = BirthdayChatAdditionalUsers.selectAll()
                .where { BirthdayChatAdditionalUsers.birthdayChat eq chatDbId }
                .mapNotNull { addUserRow ->
                    val userId = addUserRow[BirthdayChatAdditionalUsers.user]
                    UserWrapper(userId.value)
                }
                .filter { DateUtils.isBirthdayToday(it.birthDate) }
                .map { userWrapper ->
                    BirthdayPerson(
                        fullName = userWrapper.fullName,
                        birthDate = userWrapper.birthDate ?: "",
                        groupName = "Дополнительный"
                    )
                }

            // Combine and remove duplicates
            (groupUsers + additionalUsers).distinctBy { it.fullName }
        }
    }

    /**
     * Get all active chats with their groups
     */
    fun getActiveChatsWithGroups(): List<Triple<Long, List<String>, Boolean>> {
        return transaction {
            BirthdayChats.selectAll()
                .where { BirthdayChats.isActive eq true }
                .map { chatRow ->
                    val chatId = chatRow[BirthdayChats.telegramChatId]
                    val chatDbId = chatRow[BirthdayChats.id]
                    val isActive = chatRow[BirthdayChats.isActive]

                    val groups = BirthdayChatTargetGroups.selectAll()
                        .where { BirthdayChatTargetGroups.birthdayChat eq chatDbId }
                        .map { it[BirthdayChatTargetGroups.targetGroup] }

                    Triple(chatId, groups, isActive)
                }
        }
    }

    /**
     * Get a specific chat with its groups
     */
    fun getChatWithGroups(chatId: Long): Triple<Long, List<String>, Boolean>? {
        return transaction {
            BirthdayChats.selectAll()
                .where { BirthdayChats.telegramChatId eq chatId }
                .map { chatRow ->
                    val telegramChatId = chatRow[BirthdayChats.telegramChatId]
                    val chatDbId = chatRow[BirthdayChats.id]
                    val isActive = chatRow[BirthdayChats.isActive]

                    val groups = BirthdayChatTargetGroups.selectAll()
                        .where { BirthdayChatTargetGroups.birthdayChat eq chatDbId }
                        .map { it[BirthdayChatTargetGroups.targetGroup] }

                    Triple(telegramChatId, groups, isActive)
                }
                .singleOrNull()
        }
    }

    /**
     * Check if a user has birthday today by their database ID
     */
    fun checkUserBirthdayToday(userId: Int): BirthdayPerson? {
        return transaction {
            Users.selectAll()
                .where { Users.id eq userId }
                .singleOrNull()
                ?.let { userRow ->
                    if (DateUtils.isBirthdayToday(userRow[Users.birthDate])) {
                        BirthdayPerson(
                            fullName = userRow[Users.fullName],
                            birthDate = userRow[Users.birthDate] ?: "",
                            groupName = "Дополнительный"
                        )
                    } else null
                }
        }
    }

    /**
     * Find users in a group with birthday today
     */
    fun findGroupBirthdaysToday(groupName: String): List<BirthdayPerson> {
        return transaction {
            Users.selectAll()
                .where {
                    (Users.description ilike "%$groupName%")
                }
                .filter { DateUtils.isBirthdayToday(it[Users.birthDate]) }
                .map { userRow ->
                    BirthdayPerson(
                        fullName = userRow[Users.fullName],
                        birthDate = userRow[Users.birthDate] ?: "",
                        groupName = groupName
                    )
                }
        }
    }
}
