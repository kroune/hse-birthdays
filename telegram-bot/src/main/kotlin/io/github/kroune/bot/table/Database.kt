package io.github.kroune.bot.table

import db.Users
import io.github.kroune.common.logging.Loggers
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val logger = Loggers.database

object BirthdayChats : IntIdTable() {
    val telegramChatId = long("telegram_chat_id").uniqueIndex()
    val isActive = bool("is_active").default(true)
}

object BirthdayChatTargetGroups : IntIdTable() {
    val birthdayChat = reference("birthday_chat", BirthdayChats)
    val targetGroup = varchar("target_group", 255)
}

object BirthdayChatAdditionalUsers : IntIdTable() {
    val birthdayChat = reference("birthday_chat", BirthdayChats)
    val user = reference("user", Users)
}

object BirthdayCheckLog : IntIdTable() {
    val checkDate = varchar("check_date", 10) // Format: yyyy-MM-dd
    val checkTimestamp = long("check_timestamp") // Unix timestamp in milliseconds
}

/**
 * Initialize telegram bot specific tables
 * Note: Database connection must be established before calling this
 */
fun initTelegramTables() {
    logger.info { "Creating telegram bot tables..." }

    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(
            BirthdayChats,
            BirthdayChatTargetGroups,
            BirthdayChatAdditionalUsers,
            BirthdayCheckLog
        )
    }

    logger.info { "Telegram bot tables created successfully" }
}

