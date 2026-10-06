package team.siru.ecoguard.common.exception

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val code: String,
    val message: String,
    /** ALREADY_SUBMITTED_TODAY 일 때만 채워지는, 이미 접수된 인증의 제출 시각 */
    val submittedAt: LocalDateTime? = null,
)
