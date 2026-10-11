package team.siru.ecoguard.common.config

import io.swagger.v3.oas.models.Operation
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
private val SUCCESS_DESCRIPTIONS = mapOf("200" to "성공", "201" to "생성 성공", "204" to "삭제 성공")

/** [ApiErrors]에 적힌 에러 코드를 상태 코드별로 묶어 Swagger 응답 목록에 넣는다. */
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

        responses.forEach { (key, response) ->
            if (response.description == DEFAULT_SUCCESS_DESCRIPTION) {
                response.description = SUCCESS_DESCRIPTIONS[key] ?: "성공"
            }
        }

        codes.groupBy { it.status.value() }
            .toSortedMap()
            .forEach { (status, group) ->
                val key = status.toString()
                val lines = group.joinToString("\n") { "- `${it.name}` – ${it.defaultMessage}" }
                val existing = responses[key]
                if (existing == null) {
                    responses.addApiResponse(key, ApiResponse().description(lines))
                } else {
                    existing.description = "${existing.description}\n$lines"
                }
            }
        return operation
    }
}
