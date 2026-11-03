package team.siru.ecoguard.activity.dto

import java.time.LocalDate

enum class ActivityResult {
    APPROVED,
    REJECTED,
    PROCESSING,
    MANUAL_REVIEW,

    /** 인증 시간이 지났는데 제출하지 않은 날 */
    NOT_SUBMITTED,

    /** 아직 인증 시간이 지나지 않은 날 */
    UPCOMING,
}

data class ActivityRecord(
    val date: LocalDate,
    val area: String?,
    val result: ActivityResult,
    val verificationId: Long?,
    val photoUrl: String?,
    val minutes: Int,
)

data class ActivitySummary(
    /** 이번 달 인증 승인된 날 수 */
    val completedDays: Int,
    /** 이번 달 청소해야 하는 날 수 (배정 이후 평일) */
    val requiredDays: Int,
    val approvedCount: Int,
    val rejectedCount: Int,
    val notSubmittedCount: Int,
)

data class MyActivityResponse(
    /** 전체 누적 봉사시간(분) */
    val totalMinutes: Long,
    val year: Int,
    val month: Int,
    val monthlyMinutes: Int,
    val summary: ActivitySummary,
    val records: List<ActivityRecord>,
)

data class WeeklyDay(
    val date: LocalDate,
    val result: ActivityResult,
)

data class WeeklyActivityResponse(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    /** 이번 주 인증 승인된 날 수 (N/5일의 N) */
    val completedDays: Int,
    val requiredDays: Int,
    val days: List<WeeklyDay>,
)

data class WeeklyParticipationDay(
    val date: LocalDate,
    /** 인증을 제출한 배정 학생 수 (승인/반려/검수 중 모두 포함) */
    val submittedCount: Int,
    /** 인증이 승인된 배정 학생 수 */
    val approvedCount: Int,
    /** 제출 / 배정 인원 (0~100, 반올림) */
    val participationRate: Int,
)

data class WeeklyParticipationResponse(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    /** 청소구역에 배정된 학생 수 */
    val assignedCount: Int,
    /** 월~금 요일별 참여 현황 */
    val days: List<WeeklyParticipationDay>,
)

data class StudentActivityResponse(
    val studentId: Long,
    val studentNumber: String?,
    val name: String,
    val area: String?,
    val photoUrl: String?,
    /** 출석(인증 승인) 일수 */
    val attendanceDays: Long,
    val totalMinutes: Long,
)
