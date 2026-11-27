package team.siru.ecoguard.appeal.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import team.siru.ecoguard.appeal.Appeal
import team.siru.ecoguard.appeal.AppealStatus
import java.time.LocalDate
import java.time.LocalDateTime

private const val PHOTO_URLS_DESCRIPTION = "이의신청 때 첨부한 사진의 상대 경로 목록(최대 3장, 없으면 []). 서버 주소를 앞에 붙여 쓴다"

@Schema(description = "이의신청 요청 (사진 없이 보낼 때, application/json)")
data class CreateAppealRequest(
    @field:Schema(description = "이의신청 내용 (공백만 있으면 안 됨)", example = "난간까지 닦았는데 사진 각도 때문에 안 보였어요.")
    @field:NotBlank
    val content: String,
)

@Schema(description = "접수된 이의신청")
data class CreateAppealResponse(
    @field:Schema(description = "이의신청 ID", example = "15")
    val appealId: Long,
    @field:Schema(description = "접수 직후에는 항상 PENDING", example = "PENDING")
    val status: AppealStatus,
    @field:Schema(description = "이 인증에 대한 N차 이의신청 (1부터)", example = "1")
    val round: Int,
)

@Schema(description = "이의신청 처리 요청 (교사)")
data class AppealDecisionRequest(
    /** APPROVED 또는 REJECTED. 그 외 값은 INVALID_DECISION 으로 응답하기 위해 문자열로 받는다. */
    @field:Schema(
        description = "`APPROVED` 또는 `REJECTED`. 그 외 값·누락은 `400 INVALID_DECISION`",
        allowableValues = ["APPROVED", "REJECTED"],
        example = "APPROVED",
        nullable = true,
    )
    val decision: String? = null,
    /** 교사 답변 본문. 반려 시 학생에게 사유로 보여진다. */
    @field:Schema(description = "선택. 교사 답변 본문. 반려 시 학생에게 사유로 보인다", example = "다시 찍은 사진에서 깨끗한 게 확인돼 승인합니다.", nullable = true)
    val reply: String? = null,
    /** 교사 답변 제목 (선택) */
    @field:Schema(description = "선택. 교사 답변 제목", example = "확인했어요", nullable = true)
    val replyTitle: String? = null,
)

@Schema(description = "이의신청 (교사 화면)")
data class AppealResponse(
    @field:Schema(description = "이의신청 ID", example = "15")
    val appealId: Long,
    @field:Schema(description = "대상 인증 ID", example = "101")
    val verificationId: Long,
    @field:Schema(description = "N차 이의신청", example = "1")
    val round: Int,
    @field:Schema(description = "학생 이름", example = "홍길동")
    val studentName: String,
    @field:Schema(description = "학번", example = "2104", nullable = true)
    val studentNumber: String?,
    @field:Schema(description = "구역 이름", example = "본관 계단 A")
    val areaName: String,
    @field:Schema(description = "인증 날짜", example = "2026-10-07")
    val verificationDate: LocalDate,
    @field:Schema(description = "원래 제출했던 인증 사진의 상대 경로", example = "/files/verifications/5b0d2c8e-1f3a-4c55-9a7e-2d6f0b9c1e44.jpg")
    val photoUrl: String,
    @field:Schema(description = "인증이 반려된 사유 (교사가 적은 사유 또는 AI 사유)", example = "[\"계단 난간에 쓰레기가 남아 있어요.\"]")
    val failReasons: List<String>,
    @field:Schema(description = "학생이 쓴 이의신청 내용", example = "난간까지 닦았는데 사진 각도 때문에 안 보였어요.")
    val content: String,
    /** 이의신청 때 첨부한 사진 */
    @field:Schema(description = PHOTO_URLS_DESCRIPTION, example = "[\"/files/appeals/0a1b2c3d-4e5f-6789-abcd-ef0123456789.jpg\"]")
    val photoUrls: List<String>,
    @field:Schema(description = "처리 상태")
    val status: AppealStatus,
    @field:Schema(description = "교사 답변 제목. 처리 전이거나 적지 않았으면 null", example = "확인했어요", nullable = true)
    val replyTitle: String?,
    @field:Schema(description = "교사 답변 본문. 처리 전이거나 적지 않았으면 null", example = "다시 찍은 사진에서 깨끗한 게 확인돼 승인합니다.", nullable = true)
    val reply: String?,
    @field:Schema(description = "접수 시각 (KST)")
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
            photoUrls = appeal.photoUrls.toList(),
            status = appeal.status,
            replyTitle = appeal.replyTitle,
            reply = appeal.reply,
            createdAt = appeal.createdAt,
        )
    }
}

@Schema(description = "내 이의신청 (학생 화면)")
data class MyAppealResponse(
    @field:Schema(description = "이의신청 ID", example = "15")
    val appealId: Long,
    @field:Schema(description = "대상 인증 ID", example = "101")
    val verificationId: Long,
    /** N차 이의신청 */
    @field:Schema(description = "N차 이의신청", example = "1")
    val round: Int,
    @field:Schema(description = "구역 이름", example = "본관 계단 A")
    val areaName: String,
    @field:Schema(description = "인증 날짜", example = "2026-10-07")
    val verificationDate: LocalDate,
    @field:Schema(description = "내가 쓴 이의신청 내용", example = "난간까지 닦았는데 사진 각도 때문에 안 보였어요.")
    val content: String,
    @field:Schema(description = PHOTO_URLS_DESCRIPTION, example = "[\"/files/appeals/0a1b2c3d-4e5f-6789-abcd-ef0123456789.jpg\"]")
    val photoUrls: List<String>,
    /** PENDING(검토 중) / APPROVED(승인, +10분) / REJECTED(반려) */
    @field:Schema(description = "처리 상태 · PENDING(검토 중) / APPROVED(승인, +10분) / REJECTED(반려)")
    val status: AppealStatus,
    /** 실제로 적립된 시간(분). 승인 전·반려이거나, 이미 승인된 인증이라 적립이 없었으면 null */
    @field:Schema(description = "실제로 적립된 봉사시간(분). 승인 전·반려이거나 적립이 없었으면 null", example = "10", nullable = true)
    val awardedMinutes: Int?,
    @field:Schema(description = "교사 답변 제목. 처리 전이거나 적지 않았으면 null", example = "확인했어요", nullable = true)
    val replyTitle: String?,
    @field:Schema(description = "교사 답변 본문. 반려면 반려 사유. 처리 전이거나 적지 않았으면 null", example = "다시 찍은 사진에서 깨끗한 게 확인돼 승인합니다.", nullable = true)
    val reply: String?,
    @field:Schema(description = "접수 시각 (KST)")
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
            photoUrls = appeal.photoUrls.toList(),
            status = appeal.status,
            awardedMinutes = appeal.awardedMinutes,
            replyTitle = appeal.replyTitle,
            reply = appeal.reply,
            createdAt = appeal.createdAt,
        )
    }
}
