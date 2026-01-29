package db

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object Users : IntIdTable() {
    val moodleId = integer("moodle_id").uniqueIndex()
    val lkId = varchar("lk_id", 255).uniqueIndex()
    val fullName = varchar("full_name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val description = text("description")
    val hasPhone = bool("has_phone")
    val type = varchar("type", 255)
    val lastName = varchar("last_name", 255)
    val firstName = varchar("first_name", 255)
    val middleName = varchar("middle_name", 255)
    val isTimetableAvailable = bool("is_timetable_available")
    val isSubordinatesAvailable = bool("is_subordinates_available")
    val birthDate = varchar("birth_date", 255).nullable()
    val sourceId = varchar("source_id", 255).nullable()
    val internalId = varchar("internal_id", 255).nullable()
    val campus = varchar("campus", 255).nullable()
}

object StaffPositions : IntIdTable() {
    val user = reference("user", Users)
    val unitName = text("unit_name")
    val unitId = integer("unit_id")
    val isMain = bool("is_main")
    val positionName = varchar("position_name", 255)
    val chiefId = varchar("chief_id", 255).nullable()
    val chiefFullName = varchar("chief_full_name", 255).nullable()
    val chiefBirthDate = varchar("chief_birth_date", 255).nullable()
    val chiefEmail = varchar("chief_email", 255).nullable()
    val chiefAvatarUrl = text("chief_avatar_url").nullable()
    val chiefDescription = text("chief_description").nullable()
    val chiefHasPhone = bool("chief_has_phone").nullable()
    val chiefType = varchar("chief_type", 255).nullable()
}

object StaffAddresses : IntIdTable() {
    val user = reference("user", Users)
    val label = text("label")
    val roomCode = varchar("room_code", 255)
    val isMain = bool("is_main")
    val presenceType = varchar("presence_type", 255).nullable()
    val presenceTime = varchar("presence_time", 255).nullable()
    val phoneInternalExt = varchar("phone_internal_ext", 255).nullable()
    val phoneInternalFull = varchar("phone_internal_full", 255).nullable()
    val phoneWork = varchar("phone_work", 255).nullable()
    val navigationRoom = integer("navigation_room").nullable()
    val navigationFloor = integer("navigation_floor").nullable()
    val campus = varchar("campus", 255)
}

object Educations : IntIdTable() {
    val user = reference("user", Users)
    val universityTitle = varchar("university_title", 255)
    val startYear = varchar("start_year", 255)
    val degreeLevel = varchar("degree_level", 255)
    val programId = varchar("program_id", 255)
    val programTitle = text("program_title")
    val facultyTitle = text("faculty_title")
    val campus = varchar("campus", 255)
    val groupId = varchar("group_id", 255)
    val groupTitle = varchar("group_title", 255)
    val smartPlanProgramId = varchar("smart_plan_program_id", 255)
    val degree = varchar("degree", 255)
}

object WebRequests : IntIdTable() {
    val moodleId = integer("moodle_id")
    val timestamp = long("timestamp")
    val result = text("result")
}

object MobileSearches : IntIdTable() {
    val name = varchar("name", 255)
    val timestamp = long("timestamp")
    val result = text("result")
}

object ErrorLogs : IntIdTable() {
    val moodleId = integer("moodle_id").nullable()
    val timestamp = long("timestamp")
    val errorType = varchar("error_type", 255)
    val message = text("message")
    val stackTrace = text("stack_trace")
}

fun initDatabase() {
    // this is valid credentials
    Database.connect(
        url = "jdbc:postgresql://localhost:5432/postgres",  // Use 'postgres' (default DB)
        driver = "org.postgresql.Driver",
        user = "postgres",  // Default user is 'postgres'
        password = "mysecretpassword"
    )

    transaction {
        addLogger(StdOutSqlLogger)
//        SchemaUtils.drop(Users, StaffPositions, StaffAddresses, Educations, WebRequests, MobileSearches, ErrorLogs)
        SchemaUtils.create(
            Users,
            StaffPositions,
            StaffAddresses,
            Educations,
            WebRequests,
            MobileSearches,
            ErrorLogs,
        )
    }
}