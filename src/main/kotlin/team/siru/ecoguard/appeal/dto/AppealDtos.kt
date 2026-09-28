package team.siru.ecoguard.appeal.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import team.siru.ecoguard.appeal.Appeal
import team.siru.ecoguard.appeal.AppealStatus

data class CreateAppealRequest(
    @field:NotBlank
    val content: String,
)

data class CreateAppealResponse(
    val appealId: Long,
    val status: AppealStatus,
)

data class AppealDecisionRequest(
    @field:NotNull
    val decision: AppealStatus,
)

data class AppealResponse(
    val appealId: Long,
    val studentName: String,
    val content: String,
    val photoUrl: String,
    val status: AppealStatus,
) {
    companion object {
        fun from(appeal: Appeal) = AppealResponse(
            appealId = appeal.id,
            studentName = appeal.student.name,
            content = appeal.content,
            photoUrl = appeal.verification.photoUrl,
            status = appeal.status,
        )
    }
}
