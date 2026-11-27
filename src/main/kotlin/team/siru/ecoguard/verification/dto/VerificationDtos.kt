package team.siru.ecoguard.verification.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.siru.ecoguard.verification.Verification
import team.siru.ecoguard.verification.VerificationStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Schema(description = "인증 접수 결과")
data class SubmitVerificationResponse(
    @field:Schema(description = "인증 ID. 결과 조회(`GET /verifications/{id}/review`)와 이의신청에 쓴다", example = "101")
    val verificationId: Long,
    @field:Schema(description = "접수 직후에는 보통 `PROCESSING`. 같은 Idempotency-Key 재전송이면 그 시점의 상태", example = "PROCESSING")
    val status: VerificationStatus,
    @field:Schema(description = "접수 시각 (KST)", example = "2026-10-07T07:31:12")
    val submittedAt: LocalDateTime,
)

@Schema(
    description = "오늘 제출할 수 없는 이유 · `ALREADY_SUBMITTED`: 오늘 이미 제출 · `WEEKEND`: 주말 · `VACATION`: 방학 · " +
        "`BEFORE_START`: 인증 시작 전 · `AFTER_END`: 인증 시간 종료. 여러 개가 겹치면 이 순서대로 앞의 것",
)
enum class TodayUnavailableReason {
    ALREADY_SUBMITTED,
    WEEKEND,
    VACATION,
    BEFORE_START,
    AFTER_END,
}

@Schema(description = "오늘 인증 화면 정보")
data class TodayVerificationResponse(
    /** 앱 기기 시계 대신 이 시각을 기준으로 인증 가능 여부를 표시한다. */
    @field:Schema(description = "서버 현재 시각(KST). 기기 시계 대신 이 값으로 남은 시간을 계산한다", example = "2026-10-07T07:45:03")
    val serverTime: LocalDateTime,
    @field:Schema(description = "내 배정 구역 ID", example = "3")
    val areaId: Long,
    @field:Schema(description = "내 배정 구역 이름", example = "본관 계단 A")
    val areaName: String,
    @field:Schema(description = "구역에 설정된 인증 시간 원문 `HH:mm~HH:mm`", example = "07:20~08:10", nullable = true)
    val cleanTime: String?,
    @field:Schema(description = "인증 시작 시각 (이 시각 포함)", type = "string", example = "07:20:00")
    val startTime: LocalTime,
    @field:Schema(description = "인증 마감 시각 (이 시각 포함)", type = "string", example = "08:10:00")
    val endTime: LocalTime,
    @field:Schema(description = "지금 제출할 수 있는지. true 면 `unavailableReason` 은 null", example = "true")
    val canSubmit: Boolean,
    /** canSubmit 이 false 일 때만 값이 있다. */
    @field:Schema(description = "제출할 수 없는 이유. `canSubmit=false` 일 때만 있고 아니면 null", nullable = true)
    val unavailableReason: TodayUnavailableReason?,
    @field:Schema(description = "오늘 이미 제출했는지", example = "false")
    val submitted: Boolean,
    @field:Schema(description = "오늘 제출한 인증 ID. 미제출이면 null", example = "101", nullable = true)
    val verificationId: Long?,
    @field:Schema(description = "오늘 제출한 인증의 상태. 미제출이면 null", nullable = true)
    val status: VerificationStatus?,
    @field:Schema(description = "오늘 제출 시각. 미제출이면 null", example = "2026-10-07T07:31:12", nullable = true)
    val submittedAt: LocalDateTime?,
)

@Schema(description = "내 인증 기록 한 건")
data class MyVerificationResponse(
    @field:Schema(description = "인증 ID", example = "101")
    val verificationId: Long,
    @field:Schema(description = "제출한 사진의 상대 경로. 서버 주소를 앞에 붙여 쓴다(인증 불필요)", example = "/files/verifications/5b0d2c8e-1f3a-4c55-9a7e-2d6f0b9c1e44.jpg")
    val photoUrl: String,
    @field:Schema(description = "인증 날짜", example = "2026-10-07")
    val date: LocalDate,
    @field:Schema(description = "제출 시각 (KST)", example = "2026-10-07T07:31:12")
    val submittedAt: LocalDateTime,
    @field:Schema(description = "구역 이름", example = "본관 계단 A")
    val areaName: String,
    @field:Schema(description = "인증 상태")
    val reviewStatus: VerificationStatus,
    @field:Schema(
        description = "반려 사유. `reviewStatus=REJECTED` 일 때만 있고 그 외에는 null. 교사가 사유를 적었으면 그 한 줄, 아니면 AI 가 지적한 사유들(빈 배열일 수 있음)",
        example = "[\"계단 난간에 쓰레기가 남아 있어요.\"]",
        nullable = true,
    )
    val failReasons: List<String>?,
) {
    companion object {
        fun from(verification: Verification) = MyVerificationResponse(
            verificationId = verification.id,
            photoUrl = verification.photoUrl,
            date = verification.verificationDate,
            submittedAt = verification.createdAt,
            areaName = verification.area.name,
            reviewStatus = verification.status,
            failReasons = if (verification.status == VerificationStatus.REJECTED) verification.failReasons else null,
        )
    }
}
