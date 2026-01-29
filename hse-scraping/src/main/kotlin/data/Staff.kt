package data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StaffPosition(
    @SerialName("unit_name") val unitName: String,
    @SerialName("unit_id") val unitId: Int,
    @SerialName("is_main") val isMain: Boolean,
    @SerialName("position_name") val positionName: String,
    val chief: Chief? = null
)

@Serializable
data class StaffAddress(
    val label: String,
    @SerialName("room_code") val roomCode: String,
    @SerialName("is_main") val isMain: Boolean,
    @SerialName("presence_type") val presenceType: String? = null,
    @SerialName("presence_time") val presenceTime: String? = null,
    @SerialName("phone_internal_ext") val phoneInternalExt: String? = null,
    @SerialName("phone_internal_full") val phoneInternalFull: String? = null,
    @SerialName("phone_work") val phoneWork: String? = null,
    val navigation: Navigation? = null,
    val campus: String
)

@Serializable
data class Navigation(
    val room: Int,
    val floor: Int
)

@Serializable
data class Chief(
    val id: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("birth_date") val birthDate: String? = null,
    val email: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val description: String,
    @SerialName("has_phone") val hasPhone: Boolean,
    val type: String
)