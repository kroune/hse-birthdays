package data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val email: String,
    @SerialName("has_phone") val hasPhone: Boolean,
    val type: String,
    val description: String
)

@Serializable
data class UserDetail(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val email: String,
    val description: String,
    @SerialName("has_phone") val hasPhone: Boolean,
    val type: String,
    val names: Names,
    @SerialName("is_timetable_available") val isTimetableAvailable: Boolean,
    @SerialName("is_subordinates_available") val isSubordinatesAvailable: Boolean,
    @SerialName("staff_positions") val staffPositions: List<StaffPosition>? = null,
    @SerialName("staff_address") val staffAddress: List<StaffAddress>? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    val sourceId: String? = null,
    val education: List<Education>? = null,
    @SerialName("_id") val internalId: String? = null,
    val campus: String? = null
)

@Serializable
data class UserDump(
    val id: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("birth_date") val birthDate: String,
    val email: String,
    @SerialName("avatar_url") val avatarUrl: String,
    val description: String,
    @SerialName("has_phone") val hasPhone: Boolean,
    val type: String,
    @SerialName("lk_roles") val lkRoles: List<String>,
    val names: Names,
    val sourceId: String,
    val education: List<Education>,
    @SerialName("_id") val internalId: String,
    val campus: String,
    @SerialName("is_timetable_available") val isTimetableAvailable: Boolean,
    @SerialName("is_subordinates_available") val isSubordinatesAvailable: Boolean,
    @SerialName("staff_phone_mobile") val staffPhoneMobile: String? = null
)

@Serializable
data class Names(
    @SerialName("last_name") val lastName: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("middle_name") val middleName: String
)
