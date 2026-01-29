package model

import db.Users
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class UserWrapper(
    val userId: Int
) {
    private val resultsRow by lazy {
        transaction {
            Users.selectAll()
                .where { Users.id eq userId }
                .single()
        }
    }

    val fullName = resultsRow[Users.fullName]
    val email = resultsRow[Users.email]
    val type = resultsRow[Users.type]
    val description = resultsRow[Users.description]
    val birthDate = resultsRow[Users.birthDate]
}