package team.siru.ecoguard.aireview.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import team.siru.ecoguard.verification.Verification
import team.siru.ecoguard.verification.VerificationStatus
import java.time.LocalDateTime

enum class ManualDecision {
    APPROVED,
    REJECTED,
}

data class ManualReviewDecisionRequest(
    @field:NotNull
    val decision: ManualDecision,
    /** 반려할 때 학생에게 보여줄 사유(선택). 승인이면 무시한다. 있으면 AI 가 지적한 사유 대신 이 값이 학생에게 보인다. */
    @field:Size(max = 200)
    val reason: String? = null,
)

data class ManualReviewItemResponse(
    val verificationId: Long,
    val studentName: String,
    val areaName: String,
    val photoUrl: String,
    val submittedAt: LocalDateTime,
    val reason: String?,
    /** AI 가 지적한 사유. 검수가 오류로 넘어온 경우에는 비어 있다. */
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

data class ReviewResultResponse(
    val status: VerificationStatus,
    val failReasons: List<String>?,
) {
    companion object {
        fun from(verification: Verification) = ReviewResultResponse(
            status = verification.status,
            failReasons = if (verification.status == VerificationStatus.REJECTED) verification.failReasons else null,
        )
    }
}
