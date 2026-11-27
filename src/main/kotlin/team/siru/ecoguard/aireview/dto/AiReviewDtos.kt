package team.siru.ecoguard.aireview.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import team.siru.ecoguard.verification.Verification
import team.siru.ecoguard.verification.VerificationStatus
import java.time.LocalDateTime

@Schema(description = "교사 수동 검토 결정 · `APPROVED`: 승인 · `REJECTED`: 반려")
enum class ManualDecision {
    APPROVED,
    REJECTED,
}

@Schema(description = "수동 검토 결정 요청")
data class ManualReviewDecisionRequest(
    @field:Schema(description = "승인 또는 반려", example = "REJECTED")
    @field:NotNull
    val decision: ManualDecision,
    /** 반려할 때 학생에게 보여줄 사유(선택). 승인이면 무시한다. 있으면 AI 가 지적한 사유 대신 이 값이 학생에게 보인다. */
    @field:Schema(
        description = "선택, 최대 200자. 반려할 때 학생에게 보여 줄 사유. 적으면 AI 사유 대신 이 문장이 보이고, 비우면 AI 사유가 보인다. 승인이면 무시",
        example = "계단 난간에 쓰레기가 남아 있어요.",
        nullable = true,
        maxLength = 200,
    )
    @field:Size(max = 200)
    val reason: String? = null,
)

@Schema(description = "수동 검토 대기 인증")
data class ManualReviewItemResponse(
    @field:Schema(description = "인증 ID", example = "101")
    val verificationId: Long,
    @field:Schema(description = "학생 이름", example = "홍길동")
    val studentName: String,
    @field:Schema(description = "구역 이름", example = "본관 계단 A")
    val areaName: String,
    @field:Schema(description = "제출 사진의 상대 경로 (서버 주소를 앞에 붙여 사용)", example = "/files/verifications/5b0d2c8e-1f3a-4c55-9a7e-2d6f0b9c1e44.jpg")
    val photoUrl: String,
    @field:Schema(description = "제출 시각 (KST)", example = "2026-10-07T07:31:12")
    val submittedAt: LocalDateTime,
    @field:Schema(
        description = "수동 검토로 넘어온 이유 · `AI_FAILED`: AI 가 통과시키지 않음 · `AI_ERROR`: AI 오류/해석 불가 응답 · " +
            "`RATE_LIMITED`: AI 호출 한도 초과 · `QUEUE_FULL`: 검수 대기열 가득 참 · `TIMEOUT`: 검수 대기 시간 초과 · " +
            "`MODEL_NOT_READY` / `ZONE_MODEL_NOT_READY` / `UNKNOWN_ZONE`: 자체 AI 서버에 해당 구역 모델이 없음",
        example = "AI_FAILED",
        nullable = true,
    )
    val reason: String?,
    /** AI 가 지적한 사유. 검수가 오류로 넘어온 경우에는 비어 있다. */
    @field:Schema(description = "AI 가 지적한 사유. 검수 오류로 넘어온 경우 []", example = "[\"계단 난간에 쓰레기가 남아 있어요.\"]")
    val failReasons: List<String>,
) {
    companion object {
        fun from(verification: Verification) = ManualReviewItemResponse(
            verificationId = verification.id,
            studentName = verification.student.name,
            areaName = verification.area.name,
            photoUrl = verification.photoUrl,
            submittedAt = verification.createdAt,
            reason = verification.manualReviewReason,
            failReasons = verification.failReasons.toList(),
        )
    }
}

@Schema(description = "검수 결과")
data class ReviewResultResponse(
    @field:Schema(description = "현재 인증 상태", example = "MANUAL_REVIEW")
    val status: VerificationStatus,
    @field:Schema(
        description = "반려 사유. `status=REJECTED` 일 때만 있고 그 외에는 null",
        example = "[\"계단 난간에 쓰레기가 남아 있어요.\"]",
        nullable = true,
    )
    val failReasons: List<String>?,
) {
    companion object {
        fun from(verification: Verification) = ReviewResultResponse(
            status = verification.status,
            failReasons = if (verification.status == VerificationStatus.REJECTED) verification.failReasons else null,
        )
    }
}
