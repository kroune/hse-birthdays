import db.Users
import eu.vendeli.tgbot.types.chat.Chat
import org.jetbrains.exposed.v1.core.ComparisonOp
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate

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

fun parseBirthDate(birthDate: String): LocalDate? {
    return try {
        // Handle both formats: yyyy-MM-dd and 0000-MM-dd
        if (birthDate.length >= 10) {
            val parts = birthDate.split("-")
            if (parts.size == 3) {
                val year = parts[0].toIntOrNull() ?: 2000 // Use 2000 as default year for 0000
                val month = parts[1].toIntOrNull() ?: return null
                val day = parts[2].toIntOrNull() ?: return null

                if (month in 1..12 && day in 1..31) {
                    return LocalDate.of(if (year == 0) 2000 else year, month, day)
                }
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

/**
 * Format birth date for display, handling the case when year is not set (0000-MM-DD)
 * Returns formatted string in DD.MM or DD.MM.YYYY format depending on whether year is known
 */
fun formatBirthDate(birthDateStr: String?): String? {
    if (birthDateStr == null) return null

    return try {
        val date = parseBirthDate(birthDateStr) ?: return null
        val month = String.format("%02d", date.monthValue)
        val day = String.format("%02d", date.dayOfMonth)

        // If original string starts with "0000-", don't include year in display
        if (birthDateStr.startsWith("0000-")) {
            "$day.$month"
        } else {
            "$day.$month.${date.year}"
        }
    } catch (_: Exception) {
        null
    }
}

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