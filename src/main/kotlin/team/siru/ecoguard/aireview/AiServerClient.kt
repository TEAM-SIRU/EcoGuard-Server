package team.siru.ecoguard.aireview

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper

sealed interface AiEvaluateOutcome {
    /** [rawResponse]는 AI 서버가 보낸 응답 원문(JSON)으로, AI_REVIEW.raw_response에 그대로 저장한다. */
    data class Success(val response: AiEvaluateResponse, val rawResponse: String) : AiEvaluateOutcome

    /** AI 가 판정은 했지만 자동 반려하지 않는 경우, [rawResponse] 와 [failReasons] 를 함께 남겨 교사가 참고하게 한다. */
    data class NeedsManualReview(
        val reason: ManualReviewReason,
        val rawResponse: String? = null,
        val failReasons: List<String> = emptyList(),
    ) : AiEvaluateOutcome
}

/** 자체 AI 서버 호출. `ai-review.provider=ai-server` 일 때만 사용한다. */
@Component
@ConditionalOnProperty(prefix = "ai-review", name = ["provider"], havingValue = "ai-server")
class AiServerClient(
    private val aiServerRestClient: RestClient,
    private val properties: AiServerProperties,
    private val objectMapper: ObjectMapper,
) : AiEvaluator {

    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        // 존재하지 않는 구역 / 처리할 수 없는 요청으로 보는 상태 코드. 그 외 4xx(인증, 용량 등)는 구역 문제가 아니다.
        val UNKNOWN_ZONE_STATUSES = setOf(400, 404, 422)
    }

    override fun evaluate(request: EvaluateRequest): AiEvaluateOutcome {
        val imageBytes = request.imageBytes
        val zoneId = request.zoneId
        val userId = request.userId
        val body = MultipartBodyBuilder().apply {
            val isPng = imageBytes.size > 4 && imageBytes[0] == 0x89.toByte() && imageBytes[1] == 0x50.toByte()
            part("image", object : ByteArrayResource(imageBytes) {
                override fun getFilename() = if (isPng) "photo.png" else "photo.jpg"
            }, if (isPng) MediaType.IMAGE_PNG else MediaType.IMAGE_JPEG)
            part("zone_id", zoneId)
            userId?.let { part("user_id", it) }
        }.build()

        return try {
            val rawResponse = aiServerRestClient.post()
                .uri(properties.evaluatePath)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String::class.java)
            if (rawResponse.isNullOrBlank()) {
                return AiEvaluateOutcome.NeedsManualReview(ManualReviewReason.TIMEOUT)
            }
            AiEvaluateOutcome.Success(objectMapper.readValue(rawResponse, AiEvaluateResponse::class.java), rawResponse)
        } catch (e: HttpServerErrorException) {
            log.warn("AI server unavailable (status={})", e.statusCode, e)
            AiEvaluateOutcome.NeedsManualReview(ManualReviewReason.MODEL_NOT_READY)
        } catch (e: HttpClientErrorException) {
            log.warn("AI server rejected request (status={})", e.statusCode, e)
            AiEvaluateOutcome.NeedsManualReview(
                if (e.statusCode.value() in UNKNOWN_ZONE_STATUSES) ManualReviewReason.UNKNOWN_ZONE else ManualReviewReason.TIMEOUT,
            )
        } catch (e: Exception) {
            log.warn("AI server call failed", e)
            AiEvaluateOutcome.NeedsManualReview(ManualReviewReason.TIMEOUT)
        }
    }
}
