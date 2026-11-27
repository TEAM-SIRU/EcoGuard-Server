package team.siru.ecoguard.activity.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(
    description = "그날의 청소 결과 · `APPROVED`: 승인 · `REJECTED`: 반려 · `PROCESSING`: AI 검수 중 · `MANUAL_REVIEW`: 교사 확인 중 · " +
        "`NOT_SUBMITTED`: 인증 시간이 지났는데 제출하지 않음 · `UPCOMING`: 아직 인증 시간이 지나지 않은 날(주간 현황에만 나옴)",
)
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

@Schema(description = "하루 기록")
data class ActivityRecord(
    @field:Schema(description = "날짜 (평일)", example = "2026-10-07")
    val date: LocalDate,
    @field:Schema(description = "구역 이름. 배정 정보가 없으면 null", example = "본관 계단 A", nullable = true)
    val area: String?,
    @field:Schema(description = "그날의 결과")
    val result: ActivityResult,
    @field:Schema(description = "그날 제출한 인증 ID. 미제출이면 null", example = "101", nullable = true)
    val verificationId: Long?,
    @field:Schema(description = "그날 제출한 사진의 상대 경로. 미제출이면 null", example = "/files/verifications/5b0d2c8e-1f3a-4c55-9a7e-2d6f0b9c1e44.jpg", nullable = true)
    val photoUrl: String?,
    @field:Schema(description = "그날 적립된 봉사시간(분). 인증 승인·이의신청 승인 적립 합계, 없으면 0", example = "10")
    val minutes: Int,
)

@Schema(description = "월간 집계")
data class ActivitySummary(
    /** 이번 달 인증 승인된 날 수 */
    @field:Schema(description = "인증이 승인된 날 수", example = "12")
    val completedDays: Int,
    /** 이번 달 청소해야 하는 날 수 (배정 이후 평일) */
    @field:Schema(description = "그달 청소해야 하는 날 수 (배정 이후 평일, 방학 제외, 아직 오지 않은 날 포함)", example = "20")
    val requiredDays: Int,
    @field:Schema(description = "승인된 날 수", example = "12")
    val approvedCount: Int,
    @field:Schema(description = "반려된 날 수", example = "1")
    val rejectedCount: Int,
    @field:Schema(description = "미제출 날 수", example = "2")
    val notSubmittedCount: Int,
)

@Schema(description = "월별 활동 기록")
data class MyActivityResponse(
    /** 전체 누적 봉사시간(분) */
    @field:Schema(description = "지금까지 전체 누적 봉사시간(분). 시간 표시는 앱에서 60으로 나눈다", example = "250")
    val totalMinutes: Long,
    @field:Schema(description = "조회한 연도", example = "2026")
    val year: Int,
    @field:Schema(description = "조회한 월 (1~12)", example = "10")
    val month: Int,
    @field:Schema(description = "그달 적립된 봉사시간(분)", example = "120")
    val monthlyMinutes: Int,
    @field:Schema(description = "그달 집계")
    val summary: ActivitySummary,
    @field:Schema(description = "날짜별 기록 (평일, 최신순, UPCOMING 제외). 없으면 []")
    val records: List<ActivityRecord>,
)

@Schema(description = "요일 하나의 결과")
data class WeeklyDay(
    @field:Schema(description = "날짜", example = "2026-10-05")
    val date: LocalDate,
    @field:Schema(description = "그날의 결과")
    val result: ActivityResult,
)

@Schema(description = "이번 주 청소 현황")
data class WeeklyActivityResponse(
    @field:Schema(description = "이번 주 월요일", example = "2026-10-05")
    val weekStart: LocalDate,
    @field:Schema(description = "이번 주 금요일", example = "2026-10-09")
    val weekEnd: LocalDate,
    /** 이번 주 인증 승인된 날 수 (N/5일의 N) */
    @field:Schema(description = "이번 주 인증 승인된 날 수 (N/5일의 N)", example = "3")
    val completedDays: Int,
    @field:Schema(description = "이번 주 청소해야 하는 날 수 (5 - 방학인 평일 수)", example = "5")
    val requiredDays: Int,
    @field:Schema(description = "요일별 결과 (월→금 순). 배정 이전 날·방학인 날은 빠져 5개보다 적을 수 있다")
    val days: List<WeeklyDay>,
)

@Schema(description = "요일 하나의 참여 현황")
data class WeeklyParticipationDay(
    @field:Schema(description = "날짜", example = "2026-10-05")
    val date: LocalDate,
    /** 인증을 제출한 배정 학생 수 (승인/반려/검수 중 모두 포함) */
    @field:Schema(description = "인증을 제출한 배정 학생 수 (승인/반려/검수 중 모두 포함)", example = "18")
    val submittedCount: Int,
    /** 인증이 승인된 배정 학생 수 */
    @field:Schema(description = "인증이 승인된 배정 학생 수", example = "15")
    val approvedCount: Int,
    /** 제출 / 배정 인원 (0~100, 반올림) */
    @field:Schema(description = "참여율 % = 제출 / 배정 인원 (0~100 정수, 반올림)", example = "86")
    val participationRate: Int,
)

@Schema(description = "이번 주 요일별 참여율")
data class WeeklyParticipationResponse(
    @field:Schema(description = "이번 주 월요일", example = "2026-10-05")
    val weekStart: LocalDate,
    @field:Schema(description = "이번 주 금요일", example = "2026-10-09")
    val weekEnd: LocalDate,
    /** 청소구역에 배정된 학생 수 */
    @field:Schema(description = "청소 구역에 배정된 학생 수", example = "21")
    val assignedCount: Int,
    /** 월~금 요일별 참여 현황 */
    @field:Schema(description = "월~금 요일별 참여 현황 (항상 5개)")
    val days: List<WeeklyParticipationDay>,
)

@Schema(description = "학생 활동 요약 (교사 검색 결과)")
data class StudentActivityResponse(
    @field:Schema(description = "학생의 사용자 ID", example = "12")
    val studentId: Long,
    @field:Schema(description = "학번", example = "2104", nullable = true)
    val studentNumber: String?,
    @field:Schema(description = "이름", example = "홍길동")
    val name: String,
    @field:Schema(description = "현재 배정 구역 이름. 미배정이면 null", example = "본관 계단 A", nullable = true)
    val area: String?,
    @field:Schema(description = "가장 최근 제출 사진의 상대 경로. 제출 기록이 없으면 null", example = "/files/verifications/5b0d2c8e-1f3a-4c55-9a7e-2d6f0b9c1e44.jpg", nullable = true)
    val photoUrl: String?,
    /** 출석(인증 승인) 일수 */
    @field:Schema(description = "출석(인증 승인) 일수", example = "25")
    val attendanceDays: Long,
    @field:Schema(description = "누적 봉사시간(분)", example = "250")
    val totalMinutes: Long,
)
