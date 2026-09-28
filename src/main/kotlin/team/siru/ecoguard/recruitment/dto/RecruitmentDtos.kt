package team.siru.ecoguard.recruitment.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import team.siru.ecoguard.recruitment.ApplicationStatus
import team.siru.ecoguard.recruitment.Recruitment
import team.siru.ecoguard.recruitment.RecruitmentApplication
import java.time.LocalDate
import java.time.LocalDateTime

data class CreateRecruitmentRequest(
    @field:NotBlank
    val semester: String,
    @field:NotNull
    val grade: Int,
    @field:NotNull
    val classNo: Int,
    @field:NotNull @field:Min(1) @field:Max(6)
    val maxCount: Int,
    @field:NotNull
    val startDate: LocalDateTime,
    @field:NotNull
    val endDate: LocalDateTime,
)

data class CreateRecruitmentResponse(
    val recruitmentId: Long,
)

data class UpdateRecruitmentRequest(
    val maxCount: Int? = null,
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
)

data class ApplyRequest(
    @field:NotBlank
    val motivation: String,
)

data class ApplyResponse(
    val applicationId: Long,
    val status: ApplicationStatus,
)

data class ApplicationStatusResponse(
    val status: ApplicationStatus,
)

data class RecruitmentPeriod(
    val start: LocalDate,
    val end: LocalDate,
)

data class ClassCapacity(
    val grade: Int,
    val classNo: Int,
    val maxCount: Int,
)

data class CurrentRecruitmentResponse(
    val recruitmentId: Long,
    val period: RecruitmentPeriod,
    val capacityByClass: List<ClassCapacity>,
    val currentApplicants: Long,
) {
    companion object {
        fun of(recruitment: Recruitment, currentApplicants: Long) = CurrentRecruitmentResponse(
            recruitmentId = recruitment.id,
            period = RecruitmentPeriod(recruitment.startDate.toLocalDate(), recruitment.endDate.toLocalDate()),
            capacityByClass = listOf(ClassCapacity(recruitment.grade, recruitment.classNo, recruitment.maxCount)),
            currentApplicants = currentApplicants,
        )
    }
}

data class ApplicantResponse(
    val applicationId: Long,
    val studentNumber: String?,
    val name: String,
    val motivation: String,
    val appliedAt: LocalDateTime,
) {
    companion object {
        fun from(application: RecruitmentApplication) = ApplicantResponse(
            applicationId = application.id,
            studentNumber = application.student.studentNumber,
            name = application.student.name,
            motivation = application.motivation,
            appliedAt = application.createdAt,
        )
    }
}
