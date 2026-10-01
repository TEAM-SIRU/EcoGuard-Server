package team.siru.ecoguard.common.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.support.MissingServletRequestPartException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ErrorResponse> {
        // 명세: 401 인증 실패는 응답 바디 없음 (OAUTH_FAILED 처럼 별도 코드가 있는 경우는 제외)
        if (e.errorCode == ErrorCode.UNAUTHORIZED) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        return ResponseEntity.status(e.errorCode.status)
            .body(ErrorResponse(e.errorCode.name, e.message ?: e.errorCode.defaultMessage))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { ErrorCode.VALIDATION_ERROR.defaultMessage }
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status)
            .body(ErrorResponse(ErrorCode.VALIDATION_ERROR.name, message))
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingPart(e: MissingServletRequestPartException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status)
            .body(ErrorResponse(ErrorCode.VALIDATION_ERROR.name, "필수 값이 누락되었습니다: ${e.requestPartName}"))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun handleUnreadableRequest(e: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status)
            .body(ErrorResponse(ErrorCode.VALIDATION_ERROR.name, ErrorCode.VALIDATION_ERROR.defaultMessage))
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(e: AuthenticationException): ResponseEntity<Void> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status)
            .body(ErrorResponse(ErrorCode.FORBIDDEN.name, ErrorCode.FORBIDDEN.defaultMessage))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR.name, ErrorCode.INTERNAL_SERVER_ERROR.defaultMessage))
    }
}
