package team.siru.ecoguard.common.config

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import jakarta.validation.Valid
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import team.siru.ecoguard.common.exception.ApiErrors
import team.siru.ecoguard.common.exception.ErrorCode

private const val DEFAULT_SUCCESS_DESCRIPTION = "OK"

/** 성공 응답의 기본 설명(OK) 대신 보여 줄 한글 설명. 여기 없는 상태 코드는 "성공"으로 표시한다. */
private val SUCCESS_DESCRIPTIONS = mapOf("200" to "성공", "201" to "생성 성공", "204" to "성공 (응답 바디 없음)")

private const val ERROR_RESPONSE_REF = "#/components/schemas/ErrorResponse"

/** 토큰이 필요한 모든 API 에 붙는 401 설명. 서버는 이때 바디를 보내지 않는다. */
private const val UNAUTHORIZED_DESCRIPTION =
    "**인증 실패 (응답 바디 없음)** – `Authorization` 헤더가 없거나, 토큰이 만료·위조됐거나, 로그아웃/탈퇴로 무효가 된 토큰입니다. " +
        "`POST /api/v1/auth/refresh` 로 재발급한 뒤 한 번 재시도하고, 그래도 401 이면 다시 로그인하세요."

/** 토큰 없이 부르는 API(재발급)의 401 설명. */
private const val INVALID_REFRESH_DESCRIPTION =
    "**refresh token 무효 (응답 바디 없음)** – 만료·위조됐거나, accessToken 을 넣었거나, 로그아웃/탈퇴 이후의 토큰입니다. " +
        "저장된 토큰을 지우고 다시 로그인하세요."

/**
 * [ApiErrors]에 적힌 에러 코드를 상태 코드별로 묶어 Swagger 응답 목록에 넣는다.
 * 각 코드는 설명 목록과 함께 실제 응답 바디 예시(Examples)로도 보여 준다.
 * 토큰이 필요한 API(전역 보안 요구사항을 그대로 쓰는 API)에는 바디 없는 401 을 자동으로 붙인다.
 */
@Component
class ApiErrorsCustomizer : OperationCustomizer {

    override fun customize(operation: Operation, handlerMethod: HandlerMethod): Operation {
        val annotation = handlerMethod.getMethodAnnotation(ApiErrors::class.java)

        val codes = LinkedHashSet<ErrorCode>()
        if (handlerMethod.hasMethodAnnotation(PreAuthorize::class.java)) codes += ErrorCode.FORBIDDEN
        if (handlerMethod.methodParameters.any { it.hasParameterAnnotation(Valid::class.java) }) {
            codes += ErrorCode.VALIDATION_ERROR
        }
        if (annotation != null) codes += annotation.value

        val responses = operation.responses ?: ApiResponses().also { operation.responses = it }

        if (annotation != null && annotation.success != 0 && annotation.success != 200) {
            val ok = responses.remove("200")
            responses.addApiResponse(annotation.success.toString(), ok ?: ApiResponse().description("OK"))
        }
        // 204 는 바디가 없으므로 springdoc 이 붙인 빈 스키마를 지운다.
        responses["204"]?.content = null

        responses.forEach { (key, response) ->
            if (response.description == DEFAULT_SUCCESS_DESCRIPTION) {
                response.description = SUCCESS_DESCRIPTIONS[key] ?: "성공"
            }
        }

        // `@SecurityRequirements()` 로 보안을 비운 공개 API(로그인 등)는 security 가 빈 목록이다.
        val requiresToken = operation.security == null || operation.security.isNotEmpty()
        if (requiresToken) codes += ErrorCode.UNAUTHORIZED

        codes.groupBy { it.status.value() }
            .toSortedMap()
            .forEach { (status, group) -> responses.addApiResponse(status.toString(), errorResponse(group, requiresToken)) }
        return operation
    }

    private fun errorResponse(group: List<ErrorCode>, requiresToken: Boolean): ApiResponse {
        val withBody = group.filter { it != ErrorCode.UNAUTHORIZED }
        val lines = group.joinToString("\n") { code ->
            if (code == ErrorCode.UNAUTHORIZED) "- ${if (requiresToken) UNAUTHORIZED_DESCRIPTION else INVALID_REFRESH_DESCRIPTION}" else "- `${code.name}` – ${code.defaultMessage}"
        }
        val response = ApiResponse().description(lines)
        if (withBody.isEmpty()) return response

        val mediaType = MediaType().schema(Schema<Any>().`$ref`(ERROR_RESPONSE_REF))
        withBody.forEach { code ->
            mediaType.addExamples(
                code.name,
                Example().summary(code.defaultMessage).value(exampleBody(code)),
            )
        }
        return response.content(Content().addMediaType("application/json", mediaType))
    }

    private fun exampleBody(code: ErrorCode): Map<String, String> = buildMap {
        put("code", code.name)
        put("message", code.defaultMessage)
        if (code == ErrorCode.ALREADY_SUBMITTED_TODAY) put("submittedAt", "2026-10-07T07:31:12")
    }
}
