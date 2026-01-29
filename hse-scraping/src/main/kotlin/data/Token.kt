package data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("refresh_expires_in") val refreshExpiresIn: Int,
    @SerialName("token_type") val tokenType: String,
    val scope: String
)