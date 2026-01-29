package io.github.kroune.scraping

import api.HseMobile
import api.HseWeb
import api.UserNameResult
import db.Educations
import db.ErrorLogs
import db.MobileSearches
import db.StaffAddresses
import db.StaffPositions
import db.Users
import db.WebRequests
import db.initDatabase
import io.github.kroune.common.logging.Loggers
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val logger = Loggers.scraping

var errorsCount = 0

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
suspend fun main() {
    logger.info { "starting database" }
    initDatabase()

    val client = createHttpClient()
    val auth = HseMobile(client)

    logger.info { "initiating login" }
    auth.login()

    val hseWeb = HseWeb(client)

    val lastProcessedId = getLastProcessedId()
    logger.info { "Last processed ID: $lastProcessedId" }

    logger.info { "starting flow" }
    processUsers(lastProcessedId, auth, hseWeb)

    client.close()
}

private fun createHttpClient(): HttpClient {
    return HttpClient(CIO) {
        install(ContentEncoding) {
            deflate(1.0F)
            gzip(0.9F)
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            retryOnException(maxRetries = 3, retryOnTimeout = true)
            exponentialDelay()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 5000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }
}

private fun getLastProcessedId(): Int {
    return transaction {
        Users.selectAll()
            .orderBy(Users.moodleId, SortOrder.DESC)
            .firstOrNull()
            ?.get(Users.moodleId) ?: 0
    }
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
private suspend fun processUsers(lastProcessedId: Int, auth: HseMobile, hseWeb: HseWeb) {
    ((lastProcessedId + 1) - 20..400000).asFlow()
        .flatMapMerge(concurrency = 5) { id ->
            flow {
                runCatching {
                    processUser(id, auth, hseWeb)
                    emit(Unit)
                }.onFailure { e ->
                    handleError(id, e)
                }
            }
        }.collect()
}

@OptIn(ExperimentalTime::class)
private suspend fun processUser(id: Int, auth: HseMobile, hseWeb: HseWeb) {
    val userNameResult = hseWeb.getUserName(id)
    logWebRequest(id, userNameResult)

    if (userNameResult is UserNameResult.Success) {
        val name = userNameResult.name
        logger.info { "$id - $name" }

        val users = auth.searchUser(name)
        logMobileSearch(name, users)

        if (users.isEmpty()) {
            logger.warn { "User with name '$name' not found in mobile search" }
            return
        }
        if (users.size > 1) {
            logger.warn { "Found more than one user for name '$name': ${users.map { it.email }}" }
        }

        val user = users.first()
        logger.info { "Found user: ${user.fullName} (${user.email})" }

        val userDetail = auth.getUserByEmail(user.email)
        logger.info { "User details: $userDetail" }
        userDetail.birthDate?.let { logger.info { "Birth Date: $it" } }

        saveUserToDatabase(id, userDetail)
    }
}

@OptIn(ExperimentalTime::class)
private fun logWebRequest(id: Int, userNameResult: UserNameResult) {
    transaction {
        WebRequests.insert {
            it[moodleId] = id
            it[timestamp] = Instant.now().toEpochMilli()
            it[result] = when (userNameResult) {
                is UserNameResult.Success -> userNameResult.name
                is UserNameResult.PermissionDenied -> "PermissionDenied"
                is UserNameResult.UserDeleted -> "UserDeleted"
                is UserNameResult.InvalidUser -> "InvalidUser"
                is UserNameResult.NotFound -> "NotFound"
                is UserNameResult.OtherError -> userNameResult.message
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun logMobileSearch(name: String, users: List<data.User>) {
    transaction {
        MobileSearches.insert {
            it[MobileSearches.name] = name
            it[timestamp] = Clock.System.now().toEpochMilliseconds()
            it[result] = users.joinToString()
        }
    }
}

private fun saveUserToDatabase(moodleId: Int, userDetail: data.UserDetail) {
    transaction {
        val existingUser = Users.selectAll().where { Users.moodleId eq moodleId }.count() > 0
        val existingUserLkId = Users.selectAll().where { Users.lkId eq userDetail.id }.count() > 0

        if (!existingUser && !existingUserLkId) {
            val userId = Users.insert {
                it[Users.moodleId] = moodleId
                it[lkId] = userDetail.id
                it[fullName] = userDetail.fullName
                it[email] = userDetail.email
                it[description] = userDetail.description
                it[hasPhone] = userDetail.hasPhone
                it[type] = userDetail.type
                it[lastName] = userDetail.names.lastName
                it[firstName] = userDetail.names.firstName
                it[middleName] = userDetail.names.middleName
                it[isTimetableAvailable] = userDetail.isTimetableAvailable
                it[isSubordinatesAvailable] = userDetail.isSubordinatesAvailable
                it[birthDate] = userDetail.birthDate
                it[sourceId] = userDetail.sourceId
                it[internalId] = userDetail.internalId
                it[campus] = userDetail.campus
            }[Users.id]

            saveStaffPositions(userId, userDetail.staffPositions)
            saveStaffAddresses(userId, userDetail.staffAddress)
            saveEducations(userId, userDetail.education)
        } else {
            logger.warn { "User with moodleId=$moodleId was already stored" }
        }
    }
}

private fun saveStaffPositions(userId: org.jetbrains.exposed.v1.core.dao.id.EntityID<Int>, positions: List<data.StaffPosition>?) {
    positions?.forEach { staffPosition ->
        StaffPositions.insert {
            it[user] = userId
            it[unitName] = staffPosition.unitName
            it[unitId] = staffPosition.unitId
            it[isMain] = staffPosition.isMain
            it[positionName] = staffPosition.positionName
            staffPosition.chief?.let { chief ->
                it[chiefId] = chief.id
                it[chiefFullName] = chief.fullName
                it[chiefBirthDate] = chief.birthDate
                it[chiefEmail] = chief.email
                it[chiefAvatarUrl] = chief.avatarUrl
                it[chiefDescription] = chief.description
                it[chiefHasPhone] = chief.hasPhone
                it[chiefType] = chief.type
            }
        }
    }
}

private fun saveStaffAddresses(userId: org.jetbrains.exposed.v1.core.dao.id.EntityID<Int>, addresses: List<data.StaffAddress>?) {
    addresses?.forEach { staffAddress ->
        StaffAddresses.insert {
            it[user] = userId
            it[label] = staffAddress.label
            it[roomCode] = staffAddress.roomCode
            it[isMain] = staffAddress.isMain
            it[presenceType] = staffAddress.presenceType
            it[presenceTime] = staffAddress.presenceTime
            it[phoneInternalExt] = staffAddress.phoneInternalExt
            it[phoneInternalFull] = staffAddress.phoneInternalFull
            it[phoneWork] = staffAddress.phoneWork
            staffAddress.navigation?.let { nav ->
                it[navigationRoom] = nav.room
                it[navigationFloor] = nav.floor
            }
            it[campus] = staffAddress.campus
        }
    }
}

private fun saveEducations(userId: org.jetbrains.exposed.v1.core.dao.id.EntityID<Int>, educations: List<data.Education>?) {
    educations?.forEach { education ->
        Educations.insert {
            it[user] = userId
            it[universityTitle] = education.universityTitle
            it[startYear] = education.startYear
            it[degreeLevel] = education.degreeLevel
            it[programId] = education.programId
            it[programTitle] = education.programTitle
            it[facultyTitle] = education.facultyTitle
            it[campus] = education.campus
            it[groupId] = education.groupId
            it[groupTitle] = education.groupTitle
            it[smartPlanProgramId] = education.smartPlanProgramId
            it[degree] = education.degree
        }
    }
}

private fun handleError(id: Int, e: Throwable) {
    logger.error(e) { "An error occurred for id $id" }
    errorsCount++

    if (errorsCount >= 50) {
        logger.error(e) { "too many errors occurred, waiting for manual fix" }
        exitProcess(399)
    }

    transaction {
        ErrorLogs.insert {
            it[moodleId] = id
            it[timestamp] = Instant.now().toEpochMilli()
            it[errorType] = e.javaClass.simpleName
            it[message] = e.message ?: ""
            it[stackTrace] = e.stackTraceToString()
        }
    }
}