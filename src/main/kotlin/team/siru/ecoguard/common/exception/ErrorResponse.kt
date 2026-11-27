package team.siru.ecoguard.common.exception

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "401 을 제외한 모든 에러 응답의 바디")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    @field:Schema(description = "에러 코드. 앱은 이 값으로 화면을 분기한다. 각 API 응답 목록 참고", example = "ALREADY_SUBMITTED_TODAY")
    val code: String,
    @field:Schema(description = "사용자에게 그대로 보여 줘도 되는 한국어 메시지", example = "오늘 이미 제출했습니다.")
    val message: String,
    /** ALREADY_SUBMITTED_TODAY 일 때만 채워지는, 이미 접수된 인증의 제출 시각 */
    @field:Schema(
        description = "`ALREADY_SUBMITTED_TODAY` 일 때만 오는, 이미 접수된 인증의 제출 시각(KST). 다른 에러에서는 필드 자체가 없다",
        example = "2026-10-07T07:31:12",
        nullable = true,
    )
    val submittedAt: LocalDateTime? = null,
)
