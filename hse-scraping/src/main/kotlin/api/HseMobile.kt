package api

import data.TokenResponse
import data.User
import data.UserDetail
import data.UserDump
import io.github.kroune.Env
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.jsoup.Jsoup

class HseMobile(
    private val client: HttpClient
) {

    private val authorityUrl = "https://saml.hse.ru/realms/hse"
    private val clientId = "app-x-android"
    private val redirectUri = "ru.hse.hseappx://saml.hse.ru/authorize_callback"
    private val meEndpoint = "https://api.hseapp.ru/v3/dump/me"
    private val searchEndpoint = "https://api.hseapp.ru/v3/dump/search"
    private val emailEndpoint = "https://api.hseapp.ru/v3/dump/email"


    private val authorizationEndpoint = "$authorityUrl/protocol/openid-connect/auth"
    private val tokenEndpoint = "$authorityUrl/protocol/openid-connect/token"

    private val username = Env.require("HSE_USERNAME")
    private val password = Env.require("HSE_PASSWORD")

    private var tokenResponse: TokenResponse? = null

    suspend fun login() {
        // Step 1: Start Authorization and Get Login Form
        val authResponse = client.get(authorizationEndpoint) {
            parameter("client_id", clientId)
            parameter("response_type", "code")
            parameter("redirect_uri", redirectUri)
        }

        val responseBody = authResponse.bodyAsText()
        val document = Jsoup.parse(responseBody)
        val form = document.select("form").first()!!
        val actionUrl = form.attr("action")

        // Step 2: Submit Credentials
        val loginResponse = client.post(actionUrl) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(FormDataContent(Parameters.build {
                append("username", username)
                append("password", password)
                append("credentialId", "")
            }))
        }

        val location = loginResponse.headers[HttpHeaders.Location]
        val code = location?.substringAfter("code=")?.substringBefore("&")

        if (code != null) {
            // Step 3: Exchange Code for Tokens
            val response: TokenResponse = client.post(tokenEndpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(FormDataContent(Parameters.build {
                    append("grant_type", "authorization_code")
                    append("client_id", clientId)
                    append("redirect_uri", redirectUri)
                    append("code", code)
                }))
            }.body()
            tokenResponse = response
        } else {
            error("Could not get authorization code")
        }
    }

    private suspend fun refreshToken() {
        val refreshToken = tokenResponse?.refreshToken ?: throw Exception("No refresh token available")
        val refreshedTokenResponse: TokenResponse = client.post(tokenEndpoint) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(FormDataContent(Parameters.build {
                append("grant_type", "refresh_token")
                append("client_id", clientId)
                append("refresh_token", refreshToken)
            }))
        }.body()
        tokenResponse = refreshedTokenResponse
    }

    suspend fun getMe(): UserDump {
        if (tokenResponse == null) {
            login()
        }

        return client.get(meEndpoint) {
            headers {
                append(HttpHeaders.Authorization, "Bearer ${tokenResponse!!.accessToken}")
            }
        }.also { response ->
            require(response.status.isSuccess())
        }.body()
    }

    suspend fun searchUser(name: String): List<User> {
        if (tokenResponse == null) {
            login()
        }

        return client.get(searchEndpoint) {
            parameter("q", name)
            headers {
                append(HttpHeaders.Authorization, "Bearer ${tokenResponse!!.accessToken}")
            }
        }.also { response ->
            require(response.status.isSuccess())
        }.body()
    }

    suspend fun getUserByEmail(email: String): UserDetail {
        if (tokenResponse == null) {
            login()
        }

        return client.get("$emailEndpoint/$email") {
            headers {
                append(HttpHeaders.Authorization, "Bearer ${tokenResponse!!.accessToken}")
            }
        }.also { response ->
            require(response.status.isSuccess())
        }.body()
    }
}
