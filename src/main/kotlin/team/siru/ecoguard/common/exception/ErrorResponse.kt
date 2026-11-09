package team.siru.ecoguard.common.exception

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val code: String,
    val message: String,
    /** ALREADY_SUBMITTED_TODAY 일 때만 채워지는, 이미 접수된 인증의 제출 시각 */
    // REVIEW: Spring Boot 4 이상이라 이게 이미 기본 동작일수도 있긴 한데 이러한 선택적 필드는 null이라면 필드가 생성되지 않도록 JSON 직렬화 옵션을 걸어주는게 좋을 것 같아요
    val submittedAt: LocalDateTime? = null,
)
