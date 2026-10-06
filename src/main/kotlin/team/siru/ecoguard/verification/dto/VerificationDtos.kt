package team.siru.ecoguard.verification.dto

import team.siru.ecoguard.verification.Verification
import team.siru.ecoguard.verification.VerificationStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class SubmitVerificationResponse(
    val verificationId: Long,
    val status: VerificationStatus,
    val submittedAt: LocalDateTime,
)

enum class TodayUnavailableReason {
    ALREADY_SUBMITTED,
    WEEKEND,
    VACATION,
    BEFORE_START,
    AFTER_END,
}

data class TodayVerificationResponse(
    /** 앱 기기 시계 대신 이 시각을 기준으로 인증 가능 여부를 표시한다. */
    val serverTime: LocalDateTime,
    val areaId: Long,
    val areaName: String,
    val cleanTime: String?,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val canSubmit: Boolean,
    /** canSubmit 이 false 일 때만 값이 있다. */
    val unavailableReason: TodayUnavailableReason?,
    val submitted: Boolean,
    val verificationId: Long?,
    val status: VerificationStatus?,
    val submittedAt: LocalDateTime?,
)

data class MyVerificationResponse(
    val verificationId: Long,
    val photoUrl: String,
    val date: LocalDate,
    val submittedAt: LocalDateTime,
    val areaName: String,
    val reviewStatus: VerificationStatus,
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
