package team.siru.ecoguard.aireview

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.util.Base64
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * 자체 AI 모델이 준비되기 전까지 Gemini API 로 청소 인증 사진을 검수한다.
 * 통과(PASS)만 자동 승인하고, 그 외(FAIL, 오류, 한도 초과)는 학생이 억울하게 반려되지 않도록 교사 수동 검토로 보낸다.
 */
@Component
@ConditionalOnProperty(prefix = "ai-review", name = ["provider"], havingValue = "gemini", matchIfMissing = true)
class GeminiClient(
    private val geminiRestClient: RestClient,
    private val properties: GeminiProperties,
    private val objectMapper: ObjectMapper,
) : AiEvaluator {

    private val log = LoggerFactory.getLogger(javaClass)
    private val slots = Semaphore(properties.maxConcurrency.coerceAtLeast(1))

    override fun evaluate(request: EvaluateRequest): AiEvaluateOutcome {
        if (properties.apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY 가 설정되지 않아 수동 검토로 넘깁니다")
            return manualReview(ManualReviewReason.MODEL_NOT_READY)
        }
        if (!slots.tryAcquire(SLOT_WAIT_SECONDS, TimeUnit.SECONDS)) {
            return manualReview(ManualReviewReason.TIMEOUT)
        }
        try {
            return callWithRetry(buildBody(request))
        } finally {
            slots.release()
        }
    }

    private fun callWithRetry(body: String): AiEvaluateOutcome {
        var retried = false
        while (true) {
            try {
                return parse(call(body))
            } catch (e: HttpClientErrorException) {
                val status = e.statusCode.value()
                if (status == 429 && !retried) {
                    retried = true
                    pauseBeforeRetry()
                    continue
                }
                log.warn("Gemini rejected request (status={})", status)
                return manualReview(
                    if (status == 429) ManualReviewReason.RATE_LIMITED else ManualReviewReason.AI_ERROR,
                    errorBody(e.responseBodyAsString),
                )
            } catch (e: HttpServerErrorException) {
                val status = e.statusCode.value()
                if (status == 503 && !retried) {
                    retried = true
                    pauseBeforeRetry()
                    continue
                }
                log.warn("Gemini unavailable (status={})", status)
                return manualReview(ManualReviewReason.MODEL_NOT_READY, errorBody(e.responseBodyAsString))
            } catch (e: Exception) {
                log.warn("Gemini call failed", e)
                return manualReview(ManualReviewReason.TIMEOUT)
            }
        }
    }

    private fun pauseBeforeRetry() {
        if (properties.retryDelayMillis > 0) Thread.sleep(properties.retryDelayMillis)
    }

    private fun call(body: String): String? = geminiRestClient.post()
        .uri("/v1beta/models/{model}:generateContent", properties.model)
        .header("x-goog-api-key", properties.apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(String::class.java)

    private fun parse(rawResponse: String?): AiEvaluateOutcome {
        if (rawResponse.isNullOrBlank()) return manualReview(ManualReviewReason.TIMEOUT)
        val verdict = runCatching {
            val text = objectMapper.readValue(rawResponse, GeminiResponse::class.java)
                .candidates?.firstOrNull()?.content?.parts?.firstNotNullOfOrNull { it.text }
            text?.let { objectMapper.readValue(it, GeminiVerdict::class.java) }
        }.getOrNull()
        if (verdict?.isPassed == null) {
            log.warn("Gemini 응답을 판정으로 해석하지 못했습니다")
            // 원인을 분석할 수 있게 해석하지 못한 응답의 원문을 남긴다. AI 가 판정을 주지 않았으므로 판정값은 비워 둔다.
            return manualReview(ManualReviewReason.AI_ERROR, rawResponse)
        }

        // 통과 여부는 걸러내기 전의 사유로 판단한다. 알 수 없는 사유가 섞인 "통과"를 사유가 없는 통과로 착각하지 않도록,
        // 사유가 하나라도 있으면(모르는 값이어도) 수동 검토로 보낸다. 저장하는 사유는 정해진 코드만 남긴다.
        val reportedReasons = verdict.failReasons ?: emptyList()
        val failReasons = reportedReasons.filter { it in FAIL_REASONS }
        if (verdict.isPassed && reportedReasons.isEmpty()) {
            return AiEvaluateOutcome.Success(
                AiEvaluateResponse(decision = "PASS", isPassed = true, failReasons = emptyList()),
                rawResponse,
            )
        }
        // AI 가 실제로 말한 판정(통과인데 사유가 있는 모순된 응답이면 PASS)을 그대로 남기고, 서버가 수동 검토로 보낸 이유는 AI_FAILED 로 따로 남긴다.
        return AiEvaluateOutcome.NeedsManualReview(
            ManualReviewReason.AI_FAILED,
            rawResponse,
            failReasons,
            decision = if (verdict.isPassed) "PASS" else "FAIL",
            isPassed = verdict.isPassed,
        )
    }

    /** 오류 응답 본문은 길 수 있으므로 앞부분만 남긴다. 비어 있으면 저장할 것이 없다. */
    private fun errorBody(body: String?): String? = body?.takeIf { it.isNotBlank() }?.take(MAX_ERROR_BODY_CHARS)

    private fun buildBody(request: EvaluateRequest): String {
        val isPng = request.imageBytes.size > 4 && request.imageBytes[0] == 0x89.toByte() && request.imageBytes[1] == 0x50.toByte()
        val body = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("text" to buildPrompt(request)),
                        mapOf(
                            "inline_data" to mapOf(
                                "mime_type" to if (isPng) "image/png" else "image/jpeg",
                                "data" to Base64.getEncoder().encodeToString(request.imageBytes),
                            ),
                        ),
                    ),
                ),
            ),
            "generationConfig" to mapOf(
                "temperature" to 0,
                "responseMimeType" to "application/json",
                "responseSchema" to RESPONSE_SCHEMA,
            ),
        )
        return objectMapper.writeValueAsString(body)
    }

    private fun buildPrompt(request: EvaluateRequest): String = """
        당신은 학교 청소 인증 사진을 검수합니다. 사진은 학교 건물 안의 청소 구역입니다. 사람은 평가하지 않습니다.

        요청된 구역: ${request.zoneName}
        구역 설명: ${request.zoneDescription ?: "(없음)"}

        다음을 모두 확인하세요.
        1. 사진이 요청된 구역으로 보이는가? 아니면 ZONE_NOT_RECOGNIZED
        2. 쓰레받이가 보이는가? 아니면 DUSTPAN_NOT_FOUND
        3. 쓰레받이 안에 쓰레기가 담겨 있는가? 아니면 TRASH_NOT_FOUND_IN_DUSTPAN
        4. 쓰레기가 쓰레받이 밖에 보이는가? 그렇다면 TRASH_OUTSIDE_DUSTPAN
        5. 바닥이나 계단에 쓰레기나 오염이 남아 있는가? 그렇다면 ZONE_ANOMALY_DETECTED

        모두 문제없을 때만 is_passed 를 true 로 하고 fail_reasons 는 빈 배열로 둡니다.
        판단이 불확실하면 is_passed 를 false 로 하고 해당하는 fail_reasons 를 넣습니다.
        reason 에는 판단 근거를 한 문장으로 적습니다.
    """.trimIndent()

    private fun manualReview(reason: ManualReviewReason, rawResponse: String? = null) =
        AiEvaluateOutcome.NeedsManualReview(reason, rawResponse)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GeminiResponse(val candidates: List<Candidate>? = null) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Candidate(val content: Content? = null)

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Content(val parts: List<Part>? = null)

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Part(val text: String? = null)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GeminiVerdict(
        @JsonProperty("is_passed")
        val isPassed: Boolean? = null,
        @JsonProperty("fail_reasons")
        val failReasons: List<String>? = null,
        val reason: String? = null,
    )

    private companion object {
        const val SLOT_WAIT_SECONDS = 30L
        const val MAX_ERROR_BODY_CHARS = 2000

        val FAIL_REASONS = listOf(
            "ZONE_NOT_RECOGNIZED",
            "DUSTPAN_NOT_FOUND",
            "TRASH_NOT_FOUND_IN_DUSTPAN",
            "TRASH_OUTSIDE_DUSTPAN",
            "ZONE_ANOMALY_DETECTED",
        )

        val RESPONSE_SCHEMA = mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "is_passed" to mapOf("type" to "BOOLEAN"),
                "fail_reasons" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf("type" to "STRING", "enum" to FAIL_REASONS),
                ),
                "reason" to mapOf("type" to "STRING"),
            ),
            "required" to listOf("is_passed", "fail_reasons"),
        )
    }
}
