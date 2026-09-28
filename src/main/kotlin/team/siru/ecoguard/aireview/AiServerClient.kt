package team.siru.ecoguard.aireview

import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient

sealed interface AiEvaluateOutcome {
    data class Success(val response: AiEvaluateResponse) : AiEvaluateOutcome
    data class NeedsManualReview(val reason: ManualReviewReason) : AiEvaluateOutcome
}

@Component
class AiServerClient(
    private val aiServerRestClient: RestClient,
    private val properties: AiServerProperties,
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
            val response = aiServerRestClient.post()
                .uri(properties.evaluatePath)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(AiEvaluateResponse::class.java)
                ?: return AiEvaluateOutcome.NeedsManualReview(ManualReviewReason.TIMEOUT)
            AiEvaluateOutcome.Success(response)
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
