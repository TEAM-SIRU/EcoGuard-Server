package team.siru.ecoguard.common.exception

/**
 * 이 API가 돌려줄 수 있는 에러 코드를 Swagger 명세에 올린다. 서비스에서 던지는 `ErrorCode`를 그대로 적는다.
 * 상태 코드와 메시지는 [ErrorCode]에서 가져오므로 따로 쓰지 않는다.
 *
 * `@PreAuthorize`가 있으면 `FORBIDDEN`, `@Valid` 요청 바디가 있으면 `VALIDATION_ERROR`는 자동으로 붙는다.
 *
 * @param success 성공 상태 코드가 200이 아닐 때 적는다(예: 201, 204).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiErrors(
    vararg val value: ErrorCode,
    val success: Int = 0,
)
