package io.github.kroune.bot

import db.Users
import eu.vendeli.tgbot.types.chat.Chat
import io.github.kroune.bot.table.BirthdayChatAdditionalUsers
import io.github.kroune.bot.table.BirthdayChatTargetGroups
import io.github.kroune.bot.table.BirthdayChats
import io.github.kroune.common.util.DateUtils
import org.jetbrains.exposed.v1.core.ComparisonOp
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class InsensitiveLikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "ILIKE")

infix fun <T : String?> ExpressionWithColumnType<T>.ilike(pattern: T): Op<Boolean> =
    InsensitiveLikeOp(this, QueryParameter(pattern, columnType))

fun getInternalChatId(chat: Chat): Int = getInternalChatId(chat.id)

fun getInternalChatId(chatId: Long): Int = transaction {
    BirthdayChats.select(BirthdayChats.id)
        .where { BirthdayChats.telegramChatId eq chatId }
        .single()[BirthdayChats.id]
        .value
}

fun getTargetGroups(chatDbId: Int): List<String> = transaction {
    BirthdayChatTargetGroups.selectAll()
        .where { BirthdayChatTargetGroups.birthdayChat eq chatDbId }
        .map { it[BirthdayChatTargetGroups.targetGroup] }
}

/**
 * @deprecated Use DateUtils.parseBirthDate instead
 */
fun parseBirthDate(birthDate: String) = DateUtils.parseBirthDate(birthDate)

/**
 * @deprecated Use DateUtils.formatBirthDate instead
 */
fun formatBirthDate(birthDateStr: String?) = DateUtils.formatBirthDate(birthDateStr)

fun checkGroupExistence(text: String): Boolean {
    return transaction {
        Users.selectAll()
            .where { Users.description ilike "%$text%" }
            .count() > 0
    }
}

fun getAdditionalUsers(chatDbId: Int): List<Int> = transaction {
    BirthdayChatAdditionalUsers.selectAll()
        .where { BirthdayChatAdditionalUsers.birthdayChat eq chatDbId }
        .map {
            it[BirthdayChatAdditionalUsers.user].value
        }
}