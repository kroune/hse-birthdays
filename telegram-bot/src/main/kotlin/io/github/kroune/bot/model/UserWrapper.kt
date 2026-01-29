package io.github.kroune.bot.model

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

    val fullName: String get() = resultsRow[Users.fullName]
    val email: String get() = resultsRow[Users.email]
    val type: String get() = resultsRow[Users.type]
    val description: String get() = resultsRow[Users.description]
    val birthDate: String? get() = resultsRow[Users.birthDate]
}