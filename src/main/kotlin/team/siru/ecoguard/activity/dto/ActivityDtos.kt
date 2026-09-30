package team.siru.ecoguard.activity.dto

import java.time.LocalDate
import java.time.YearMonth

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
    val approvedCount: Int,
    val rejectedCount: Int,
    val notSubmittedCount: Int,
)

data class MyActivityResponse(
    /** 전체 누적 봉사시간(분) */
    val totalMinutes: Long,
    val month: YearMonth,
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
    val totalDays: Int,
    val days: List<WeeklyDay>,
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
