package team.siru.ecoguard.common.exception

class BusinessException(
    val errorCode: ErrorCode,
    message: String = errorCode.defaultMessage,
) : RuntimeException(message)
