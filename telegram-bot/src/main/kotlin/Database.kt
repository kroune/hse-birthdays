import db.Users
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

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


fun initTelegramDatabase() {
    // this is valid credentials
    Database.connect(
        url = "jdbc:postgresql://localhost:5432/postgres",  // Use 'postgres' (default DB)
        driver = "org.postgresql.Driver",
        user = "postgres",  // Default user is 'postgres'
        password = "mysecretpassword"
    )

    transaction {
        addLogger(StdOutSqlLogger)
//        SchemaUtils.drop(BirthdayChats, BirthdayChatTargetGroups, BirthdayChatAdditionalUsers)
        SchemaUtils.create(
            BirthdayChats,
            BirthdayChatTargetGroups,
            BirthdayChatAdditionalUsers,
            BirthdayCheckLog
        )
    }
}