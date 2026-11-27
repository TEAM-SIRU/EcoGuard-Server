package team.siru.ecoguard.recruitment.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import team.siru.ecoguard.recruitment.ApplicationStatus
import team.siru.ecoguard.recruitment.Recruitment
import team.siru.ecoguard.recruitment.RecruitmentApplication
import team.siru.ecoguard.recruitment.RecruitmentPeriodStatus
import java.time.LocalDateTime
import java.time.LocalTime

private const val SEMESTER_DESCRIPTION = "학기 표시 문자열(자유 형식). 화면 표시·구분용"
private const val ACTIVITY_TIME_NOTE = "모집 안내용 청소 시간이며, 실제 인증 가능 시간은 구역의 cleanTime 을 따른다"

@Schema(description = "모집 공고 생성 요청")
data class CreateRecruitmentRequest(
    @field:Schema(description = SEMESTER_DESCRIPTION, example = "2026-2")
    @field:NotBlank
    val semester: String,
    @field:Schema(description = "대상 학년", example = "2")
    @field:NotNull
    val grade: Int,
    @field:Schema(description = "대상 반", example = "1")
    @field:NotNull
    val classNo: Int,
    /** 범위(1~6명) 검사는 명세의 INVALID_MAX_COUNT 응답을 위해 서비스에서 한다. */
    @field:Schema(description = "모집 인원 (1~6). 범위 밖이면 `400 INVALID_MAX_COUNT`", example = "4", minimum = "1", maximum = "6")
    @field:NotNull
    val maxCount: Int,
    @field:Schema(description = "모집 시작 시각 (KST, 이 시각 포함)", example = "2026-10-01T09:00:00")
    @field:NotNull
    val startDate: LocalDateTime,
    @field:Schema(description = "모집 마감 시각 (KST, 이 시각 포함). startDate 보다 뒤여야 한다", example = "2026-10-03T18:00:00")
    @field:NotNull
    val endDate: LocalDateTime,
    /** 청소 활동 시작 시각(예: "07:20"). 생략하면 07:20 */
    @field:Schema(description = "선택. 청소 활동 시작 시각 `HH:mm`. 생략하면 07:20. " + ACTIVITY_TIME_NOTE, type = "string", example = "07:20", nullable = true)
    val activityStartTime: LocalTime? = null,
    /** 청소 활동 종료 시각(예: "08:10"). 생략하면 08:10 */
    @field:Schema(description = "선택. 청소 활동 종료 시각 `HH:mm`. 생략하면 08:10", type = "string", example = "08:10", nullable = true)
    val activityEndTime: LocalTime? = null,
)

@Schema(description = "생성된 모집 공고")
data class CreateRecruitmentResponse(
    @field:Schema(description = "모집 공고 ID", example = "7")
    val recruitmentId: Long,
)

@Schema(description = "모집 공고 수정 요청. 보낸 필드만 바뀐다(생략·null 은 그대로)")
data class UpdateRecruitmentRequest(
    @field:Schema(description = "모집 인원 (1~6, 현재 신청 인원 이상)", example = "5", nullable = true)
    val maxCount: Int? = null,
    @field:Schema(description = "모집 시작 시각 (KST)", example = "2026-10-01T09:00:00", nullable = true)
    val startDate: LocalDateTime? = null,
    @field:Schema(description = "모집 마감 시각 (KST)", example = "2026-10-04T18:00:00", nullable = true)
    val endDate: LocalDateTime? = null,
    @field:Schema(description = "청소 활동 시작 시각 `HH:mm`", type = "string", example = "07:20", nullable = true)
    val activityStartTime: LocalTime? = null,
    @field:Schema(description = "청소 활동 종료 시각 `HH:mm`", type = "string", example = "08:10", nullable = true)
    val activityEndTime: LocalTime? = null,
)

@Schema(description = "모집 신청 요청")
data class ApplyRequest(
    @field:Schema(description = "지원 동기 (공백만 있으면 안 됨)", example = "깨끗한 학교를 만들고 싶어요.")
    @field:NotBlank
    val motivation: String,
)

@Schema(description = "모집 신청 결과")
data class ApplyResponse(
    @field:Schema(description = "신청 ID", example = "31")
    val applicationId: Long,
    /** 신청은 선착순으로 즉시 확정되어 보통 APPROVED 이다. */
    @field:Schema(description = "신청 상태. 선착순으로 즉시 확정되어 항상 APPROVED", example = "APPROVED")
    val status: ApplicationStatus,
    /** 신청 순서 (N번째) */
    @field:Schema(description = "신청 순서 (N번째, 1부터)", example = "3")
    val order: Long,
    @field:Schema(description = "학번", example = "2104", nullable = true)
    val studentNumber: String?,
    @field:Schema(description = "이름", example = "홍길동")
    val name: String,
    @field:Schema(description = "신청 시각 (KST)")
    val appliedAt: LocalDateTime,
)

@Schema(description = "내 신청 상태")
data class ApplicationStatusResponse(
    /** 이 신청이 속한 모집 공고 (학기 구분용) */
    @field:Schema(description = "이 신청이 속한 모집 공고 ID", example = "7")
    val recruitmentId: Long,
    /** 신청은 선착순으로 즉시 확정되어 보통 APPROVED 이다. */
    @field:Schema(description = "신청 상태. 선착순으로 즉시 확정되어 보통 APPROVED", example = "APPROVED")
    val status: ApplicationStatus,
    /** 신청 순서 (N번째) */
    @field:Schema(description = "신청 순서 (N번째, 1부터)", example = "3")
    val order: Long,
    @field:Schema(description = "신청 시각 (KST)")
    val appliedAt: LocalDateTime,
    /** 승인되었지만 아직 청소구역이 배정되지 않은 상태 */
    @field:Schema(description = "true: 신청은 확정됐지만 아직 청소 구역이 배정되지 않음(배정 대기)", example = "true")
    val waitingForAssignment: Boolean,
)

@Schema(description = "하루 중 청소 활동 시간")
data class ActivityTime(
    @field:Schema(description = "시작 시각", type = "string", example = "07:20:00")
    val start: LocalTime,
    @field:Schema(description = "종료 시각", type = "string", example = "08:10:00")
    val end: LocalTime,
)

@Schema(description = "모집 기간")
data class RecruitmentPeriod(
    @field:Schema(description = "모집 시작 (KST)", example = "2026-10-01T09:00:00")
    val start: LocalDateTime,
    @field:Schema(description = "모집 마감 (KST)", example = "2026-10-03T18:00:00")
    val end: LocalDateTime,
)

@Schema(description = "내 반 모집 공고")
data class CurrentRecruitmentResponse(
    @field:Schema(description = "모집 공고 ID. 신청 시 path 에 쓴다", example = "7")
    val recruitmentId: Long,
    @field:Schema(description = SEMESTER_DESCRIPTION, example = "2026-2")
    val semester: String,
    @field:Schema(description = "대상 학년", example = "2")
    val grade: Int,
    @field:Schema(description = "대상 반", example = "1")
    val classNo: Int,
    @field:Schema(description = "모집 기간")
    val period: RecruitmentPeriod,
    /** 청소 활동 시간 (하루 중 시작~끝). 설정하지 않은 모집은 07:20~08:10 */
    @field:Schema(description = "청소 활동 시간. 설정하지 않은 모집은 07:20~08:10")
    val activityTime: ActivityTime,
    @field:Schema(description = "지금 기준 모집 진행 상태")
    val periodStatus: RecruitmentPeriodStatus,
    @field:Schema(description = "모집 인원", example = "4")
    val maxCount: Int,
    @field:Schema(description = "현재 신청 인원", example = "3")
    val currentApplicants: Long,
    @field:Schema(description = "정원이 찼는지 (currentApplicants >= maxCount)", example = "false")
    val isFull: Boolean,
    @field:Schema(description = "내가 이미 신청했는지", example = "false")
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
                activityTime = ActivityTime(recruitment.effectiveActivityStart(), recruitment.effectiveActivityEnd()),
                periodStatus = recruitment.periodStatus(now),
                maxCount = recruitment.maxCount,
                currentApplicants = currentApplicants,
                isFull = currentApplicants >= recruitment.maxCount,
                alreadyApplied = alreadyApplied,
            )
    }
}

@Schema(description = "모집 공고 요약 (교사)")
data class RecruitmentSummaryResponse(
    @field:Schema(description = "모집 공고 ID", example = "7")
    val recruitmentId: Long,
    @field:Schema(description = SEMESTER_DESCRIPTION, example = "2026-2")
    val semester: String,
    @field:Schema(description = "대상 학년", example = "2")
    val grade: Int,
    @field:Schema(description = "대상 반", example = "1")
    val classNo: Int,
    @field:Schema(description = "모집 기간")
    val period: RecruitmentPeriod,
    /** 청소 활동 시간 (하루 중 시작~끝). 설정하지 않은 모집은 07:20~08:10 */
    @field:Schema(description = "청소 활동 시간. 설정하지 않은 모집은 07:20~08:10")
    val activityTime: ActivityTime,
    @field:Schema(description = "지금 기준 모집 진행 상태")
    val periodStatus: RecruitmentPeriodStatus,
    @field:Schema(description = "모집 인원", example = "4")
    val maxCount: Int,
    @field:Schema(description = "신청 인원", example = "3")
    val applicantCount: Long,
    @field:Schema(description = "정원이 찼는지", example = "false")
    val isFull: Boolean,
) {
    companion object {
        fun of(recruitment: Recruitment, applicantCount: Long, now: LocalDateTime) = RecruitmentSummaryResponse(
            recruitmentId = recruitment.id,
            semester = recruitment.semester,
            grade = recruitment.grade,
            classNo = recruitment.classNo,
            period = RecruitmentPeriod(recruitment.startDate, recruitment.endDate),
            activityTime = ActivityTime(recruitment.effectiveActivityStart(), recruitment.effectiveActivityEnd()),
            periodStatus = recruitment.periodStatus(now),
            maxCount = recruitment.maxCount,
            applicantCount = applicantCount,
            isFull = applicantCount >= recruitment.maxCount,
        )
    }
}

@Schema(description = "모집 신청자 (교사)")
data class ApplicantResponse(
    @field:Schema(description = "신청 ID", example = "31")
    val applicationId: Long,
    /** 신청 순서 (N번째) */
    @field:Schema(description = "신청 순서 (N번째, 1부터)", example = "1")
    val order: Long,
    @field:Schema(description = "학번", example = "2104", nullable = true)
    val studentNumber: String?,
    @field:Schema(description = "이름", example = "홍길동")
    val name: String,
    @field:Schema(description = "지원 동기", example = "깨끗한 학교를 만들고 싶어요.")
    val motivation: String,
    /** 신청은 선착순으로 즉시 확정되어 보통 APPROVED 이다. */
    @field:Schema(description = "신청 상태. 선착순으로 즉시 확정되어 보통 APPROVED", example = "APPROVED")
    val status: ApplicationStatus,
    @field:Schema(description = "신청 시각 (KST)")
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
