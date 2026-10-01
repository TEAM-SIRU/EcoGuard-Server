package team.siru.ecoguard.aireview

import org.slf4j.LoggerFactory
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
    data class NeedsManualReview(val reason: ManualReviewReason) : AiEvaluateOutcome
}

@Component
class AiServerClient(
    private val aiServerRestClient: RestClient,
    private val properties: AiServerProperties,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun evaluate(imageBytes: ByteArray, zoneId: String, userId: String?): AiEvaluateOutcome {
        val body = MultipartBodyBuilder().apply {
            part("image", object : ByteArrayResource(imageBytes) {
                override fun getFilename() = "photo.jpg"
            }, MediaType.IMAGE_JPEG)
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
            AiEvaluateOutcome.NeedsManualReview(ManualReviewReason.UNKNOWN_ZONE)
        } catch (e: Exception) {
            log.warn("AI server call failed", e)
            AiEvaluateOutcome.NeedsManualReview(ManualReviewReason.TIMEOUT)
        }
    }
}
