package io.github.kroune.bot.model


data class UserInfo(
    val fullName: String,
    val email: String,
    val type: String,
    val description: String,
    val birthDate: String?,
    val source: String
)

fun UserWrapper.toUserInfo(source: String): UserInfo {
    return UserInfo(
        fullName = this.fullName,
        email = this.email,
        type = this.type,
        description = this.description,
        birthDate = this.birthDate,
        source = source
    )
}


