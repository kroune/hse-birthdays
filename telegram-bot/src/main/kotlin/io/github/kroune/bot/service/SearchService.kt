package io.github.kroune.bot.service

import db.Users
import io.github.kroune.bot.ilike
import io.github.kroune.common.logging.Loggers
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val logger = Loggers.search

/**
 * Search criteria for user lookup
 */
data class SearchCriteria(
    val firstName: String? = null,
    val lastName: String? = null,
    val patronymic: String? = null,
    val email: String? = null,
    val groupName: String? = null,
    val userType: String? = null
)

/**
 * Search result containing user information
 */
data class SearchResult(
    val userId: Int,
    val fullName: String,
    val email: String,
    val type: String,
    val description: String,
    val birthDate: String?
)

/**
 * Service for searching users
 */
object SearchService {

    private const val MAX_RESULTS = 100

    /**
     * Search users based on criteria
     */
    fun search(criteria: Map<String, String>): List<SearchResult> {
        return transaction {
            var query = Users.selectAll()

            val conditions = mutableListOf<Op<Boolean>>()

            criteria["Имя"]?.let { firstName ->
                conditions.add(Users.firstName ilike "%$firstName%")
            }

            criteria["Фамилия"]?.let { lastName ->
                conditions.add(Users.lastName ilike "%$lastName%")
            }

            criteria["Отчество"]?.let { patronymic ->
                conditions.add(Users.middleName ilike "%$patronymic%")
            }

            criteria["Email"]?.let { email ->
                conditions.add(Users.email ilike "%$email%")
            }

            criteria["Тип"]?.let { type ->
                conditions.add(Users.type eq type)
            }

            criteria["Группа"]?.let { group ->
                conditions.add(Users.description ilike "%$group%")
            }

            if (conditions.isNotEmpty()) {
                query = query.where { conditions.reduce { acc, condition -> acc and condition } }
            }

            query.limit(MAX_RESULTS).map { row ->
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
    }
}
