package team.siru.ecoguard.common.exception

import java.time.LocalDateTime

class BusinessException(
    val errorCode: ErrorCode,
    message: String = errorCode.defaultMessage,
    /** ALREADY_SUBMITTED_TODAY 응답에 실어 보내는 기존 제출 시각 */
    val submittedAt: LocalDateTime? = null,
) : RuntimeException(message)
