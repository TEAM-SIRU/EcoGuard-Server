package team.siru.ecoguard.appeal.dto

import jakarta.validation.constraints.NotBlank
import team.siru.ecoguard.appeal.Appeal
import team.siru.ecoguard.appeal.AppealStatus
import java.time.LocalDate
import java.time.LocalDateTime

data class CreateAppealRequest(
    @field:NotBlank
    val content: String,
)

data class CreateAppealResponse(
    val appealId: Long,
    val status: AppealStatus,
    val round: Int,
)

data class AppealDecisionRequest(
    /** APPROVED 또는 REJECTED. 그 외 값은 INVALID_DECISION 으로 응답하기 위해 문자열로 받는다. */
    val decision: String? = null,
    /** 교사 답변. 반려 시 학생에게 사유로 보여진다. */
    val reply: String? = null,
)

data class AppealResponse(
    val appealId: Long,
    val verificationId: Long,
    val round: Int,
    val studentName: String,
    val studentNumber: String?,
    val areaName: String,
    val verificationDate: LocalDate,
    val photoUrl: String,
    val failReasons: List<String>,
    val content: String,
    val status: AppealStatus,
    val reply: String?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(appeal: Appeal) = AppealResponse(
            appealId = appeal.id,
            verificationId = appeal.verification.id,
            round = appeal.round,
            studentName = appeal.student.name,
            studentNumber = appeal.student.studentNumber,
            areaName = appeal.verification.area.name,
            verificationDate = appeal.verification.verificationDate,
            photoUrl = appeal.verification.photoUrl,
            failReasons = appeal.verification.failReasons.toList(),
            content = appeal.content,
            status = appeal.status,
            reply = appeal.reply,
            createdAt = appeal.createdAt,
        )
    }
}

data class MyAppealResponse(
    val appealId: Long,
    val verificationId: Long,
    /** N차 이의신청 */
    val round: Int,
    val areaName: String,
    val verificationDate: LocalDate,
    val content: String,
    /** PENDING(검토 중) / APPROVED(승인, +10분) / REJECTED(반려) */
    val status: AppealStatus,
    val reply: String?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(appeal: Appeal) = MyAppealResponse(
            appealId = appeal.id,
            verificationId = appeal.verification.id,
            round = appeal.round,
            areaName = appeal.verification.area.name,
            verificationDate = appeal.verification.verificationDate,
            content = appeal.content,
            status = appeal.status,
            reply = appeal.reply,
            createdAt = appeal.createdAt,
        )
    }
}
