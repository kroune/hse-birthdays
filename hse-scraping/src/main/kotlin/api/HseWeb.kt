package api

import io.github.kroune.logger
import io.github.kroune.Env
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import org.jsoup.Jsoup
import kotlin.system.measureTimeMillis

sealed interface UserNameResult {
    data class Success(val name: String) : UserNameResult
    object PermissionDenied : UserNameResult
    object UserDeleted : UserNameResult
    object InvalidUser : UserNameResult
    data class OtherError(val message: String) : UserNameResult
    object NotFound : UserNameResult
}

class HseWeb(
    private val client: HttpClient
) {

    private val moodleSessionCookie = Env.require("HSE_MOODLE_SESSION")

    suspend fun getUserName(id: Int): UserNameResult {
        runCatching {
            val response: HttpResponse
            val requestTime = measureTimeMillis {
                response = client.get("https://edu.hse.ru/user/profile.php?id=$id") {
                    headers {
                        append("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:145.0) Gecko/20100101 Firefox/145.0")
                        append("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        append("Accept-Language", "ru,en-US;q=0.7,en;q=0.3")
                        append("Accept-Encoding", "gzip, deflate, br, zstd")
                        append("DNT", "1")
                        append("Sec-GPC", "1")
                        append("Connection", "keep-alive")
                        append("Cookie", "MoodleSession=$moodleSessionCookie")
                        append("Upgrade-Insecure-Requests", "1")
                        append("Sec-Fetch-Dest", "document")
                        append("Sec-Fetch-Mode", "navigate")
                        append("Sec-Fetch-Site", "none")
                        append("Sec-Fetch-User", "?1")
                        append("Priority", "u=0, i")
                    }
                }
            }
            logger.info { "request $id: ${response.status} in ${requestTime}ms" }

            if (!response.status.isSuccess()) {
                return UserNameResult.OtherError("HTTP status: ${response.status.value}")
            }

            val html = response.bodyAsText()
            val doc = Jsoup.parse(html)
            val errorAlert = doc.selectFirst("div.alert.alert-danger")
            if (errorAlert != null) {
                val errorText = errorAlert.text()
                return when {
                    errorText.contains("Информация о данном пользователе Вам не доступна.") -> {
                        logger.info { "User info for id=$id is not available." }
                        UserNameResult.PermissionDenied
                    }

                    errorText.contains("Учетная запись пользователя была удалена") -> {
                        logger.info { "User account for id=$id was deleted." }
                        UserNameResult.UserDeleted
                    }

                    errorText.contains("Некорректный пользователь") -> {
                        logger.info { "Invalid user for id=$id." }
                        UserNameResult.InvalidUser
                    }

                    else -> {
                        UserNameResult.OtherError(errorText)
                    }
                }
            }
            val name = doc.select("div.card-profile h3").text()
            return if (name.isNotEmpty()) {
                UserNameResult.Success(name)
            } else {
                UserNameResult.NotFound
            }
        }.getOrElse { e ->
            return UserNameResult.OtherError(e.message ?: "Unknown exception")
        }
    }
}
