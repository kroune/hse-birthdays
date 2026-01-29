package data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Education(
    val id: String,
    @SerialName("university_title") val universityTitle: String,
    @SerialName("start_year") val startYear: String,
    @SerialName("degree_level") val degreeLevel: String,
    @SerialName("program_id") val programId: String,
    @SerialName("program_title") val programTitle: String,
    @SerialName("faculty_title") val facultyTitle: String,
    val campus: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("group_title") val groupTitle: String,
    @SerialName("smart_plan_program_id") val smartPlanProgramId: String,
    val degree: String
)