package team.siru.ecoguard.recruitment.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import team.siru.ecoguard.recruitment.ApplicationStatus
import team.siru.ecoguard.recruitment.Recruitment
import team.siru.ecoguard.recruitment.RecruitmentApplication
import team.siru.ecoguard.recruitment.RecruitmentPeriodStatus
import java.time.LocalDateTime

data class CreateRecruitmentRequest(
    @field:NotBlank
    val semester: String,
    @field:NotNull
    val grade: Int,
    @field:NotNull
    val classNo: Int,
    /** 범위(1~6명) 검사는 명세의 INVALID_MAX_COUNT 응답을 위해 서비스에서 한다. */
    @field:NotNull
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
    /** 신청 순서 (N번째) */
    val order: Long,
    val studentNumber: String?,
    val name: String,
    val appliedAt: LocalDateTime,
)

data class ApplicationStatusResponse(
    val status: ApplicationStatus,
    /** 신청 순서 (N번째) */
    val order: Long,
    val appliedAt: LocalDateTime,
    /** 승인되었지만 아직 청소구역이 배정되지 않은 상태 */
    val waitingForAssignment: Boolean,
)

data class RecruitmentPeriod(
    val start: LocalDateTime,
    val end: LocalDateTime,
)

data class CurrentRecruitmentResponse(
    val recruitmentId: Long,
    val semester: String,
    val grade: Int,
    val classNo: Int,
    val period: RecruitmentPeriod,
    val periodStatus: RecruitmentPeriodStatus,
    val maxCount: Int,
    val currentApplicants: Long,
    val isFull: Boolean,
    val alreadyApplied: Boolean,
) {
    companion object {
        fun of(recruitment: Recruitment, currentApplicants: Long, alreadyApplied: Boolean, now: LocalDateTime) =
            CurrentRecruitmentResponse(
                recruitmentId = recruitment.id,
                semester = recruitment.semester,
                grade = recruitment.grade,
                classNo = recruitment.classNo,
                period = RecruitmentPeriod(recruitment.startDate, recruitment.endDate),
                periodStatus = recruitment.periodStatus(now),
                maxCount = recruitment.maxCount,
                currentApplicants = currentApplicants,
                isFull = currentApplicants >= recruitment.maxCount,
                alreadyApplied = alreadyApplied,
            )
    }
}

data class RecruitmentSummaryResponse(
    val recruitmentId: Long,
    val semester: String,
    val grade: Int,
    val classNo: Int,
    val period: RecruitmentPeriod,
    val periodStatus: RecruitmentPeriodStatus,
    val maxCount: Int,
    val applicantCount: Long,
    val isFull: Boolean,
) {
    companion object {
        fun of(recruitment: Recruitment, applicantCount: Long, now: LocalDateTime) = RecruitmentSummaryResponse(
            recruitmentId = recruitment.id,
            semester = recruitment.semester,
            grade = recruitment.grade,
            classNo = recruitment.classNo,
            period = RecruitmentPeriod(recruitment.startDate, recruitment.endDate),
            periodStatus = recruitment.periodStatus(now),
            maxCount = recruitment.maxCount,
            applicantCount = applicantCount,
            isFull = applicantCount >= recruitment.maxCount,
        )
    }
}

data class ApplicantResponse(
    val applicationId: Long,
    /** 신청 순서 (N번째) */
    val order: Long,
    val studentNumber: String?,
    val name: String,
    val motivation: String,
    val status: ApplicationStatus,
    val appliedAt: LocalDateTime,
) {
    companion object {
        fun from(application: RecruitmentApplication, order: Long) = ApplicantResponse(
            applicationId = application.id,
            order = order,
            studentNumber = application.student.studentNumber,
            name = application.student.name,
            motivation = application.motivation,
            status = application.status,
            appliedAt = application.createdAt,
        )
    }
}
