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
import java.time.Clock
import java.time.Duration
import java.util.Base64

/**
 * 자체 AI 모델이 준비되기 전까지 Gemini API 로 청소 인증 사진을 검수한다.
 * 통과(PASS)만 자동 승인하고, 그 외(FAIL, 오류, 한도 초과)는 학생이 억울하게 반려되지 않도록 교사 수동 검토로 보낸다.
 *
 * 여러 모델을 등록할 수 있다([GeminiProperties.models]). 요청마다 [GeminiModelPool] 이 지금 여유 있는 모델을 골라 주고,
 * 429/503/장애가 나면 그 모델을 잠시 제외한 채 다음 모델로 다시 시도한다. 쓸 수 있는 모델이 없으면 자리가 날 때까지 기다리되,
 * 제출 후 [AiReviewQueueProperties.maxWaitSeconds] 를 넘기면 수동 검토로 보낸다.
 */
@Component
@ConditionalOnProperty(prefix = "ai-review", name = ["provider"], havingValue = "gemini", matchIfMissing = true)
class GeminiClient(
    private val geminiRestClient: RestClient,
    private val properties: GeminiProperties,
    private val queueProperties: AiReviewQueueProperties,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : AiEvaluator {

    private val log = LoggerFactory.getLogger(javaClass)
    private val pool = GeminiModelPool(properties.modelSpecs(), clock)

    init {
        log.info(
            "Gemini 검수 모델(우선순위 순): {} / 건당 최대 {}회 시도",
            properties.modelSpecs().joinToString { if (it.rpmLimit > 0) "${it.name}(분당 ${it.rpmLimit})" else it.name },
            properties.maxAttempts,
        )
    }

    override fun evaluate(request: EvaluateRequest): AiEvaluateOutcome {
        if (properties.apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY 가 설정되지 않아 수동 검토로 넘깁니다")
            return manualReview(ManualReviewReason.MODEL_NOT_READY)
        }
        val deadline = (request.queuedAt ?: clock.instant()).plusSeconds(queueProperties.maxWaitSeconds)
        val body = buildBody(request)
        val maxAttempts = properties.maxAttempts.coerceAtLeast(1)
        var attempts = 0
        var lastFailure: Failure? = null

        while (true) {
            if (Thread.currentThread().isInterrupted) return manualReview(ManualReviewReason.TIMEOUT)
            if (clock.instant().isAfter(deadline)) {
                return manualReview(lastFailure?.reason ?: ManualReviewReason.TIMEOUT, lastFailure?.rawResponse)
            }
            when (val pick = pool.acquire()) {
                is GeminiModelPool.Pick.WaitFor -> {
                    // 쓸 수 있는 모델이 없다(모두 한도에 닿았거나 잠시 제외 중). 기다려도 상한을 넘기면 포기하고 수동 검토로 보낸다.
                    if (clock.instant().plus(pick.duration).isAfter(deadline)) {
                        return manualReview(lastFailure?.reason ?: ManualReviewReason.RATE_LIMITED, lastFailure?.rawResponse)
                    }
                    pause(pick.duration.toMillis())
                }

                is GeminiModelPool.Pick.Use -> when (val result = attempt(pick.model, body)) {
                    is Attempt.Done -> return result.outcome
                    is Attempt.Failed -> {
                        lastFailure = result.failure
                        attempts++
                        if (attempts >= maxAttempts) return manualReview(result.failure.reason, result.failure.rawResponse)
                        log.info("Gemini 모델 {} 실패({}), 다른 모델로 다시 시도합니다 ({}/{})", pick.model, result.failure.reason, attempts, maxAttempts)
                    }
                }
            }
        }
    }

    /** 대기 구현. 테스트에서 실제로 쉬지 않도록 열어 둔다. */
    protected open fun pause(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private sealed interface Attempt {
        /** 더 시도할 필요가 없는 결과(성공이거나, 다른 모델로 바꿔도 소용없는 실패) */
        data class Done(val outcome: AiEvaluateOutcome) : Attempt

        /** 이 모델로는 실패했으므로 다른 모델로 다시 시도해 볼 수 있다. */
        data class Failed(val failure: Failure) : Attempt
    }

    private data class Failure(val reason: ManualReviewReason, val rawResponse: String?)

    private fun attempt(model: String, body: String): Attempt {
        try {
            return Attempt.Done(parse(call(model, body)))
        } catch (e: HttpClientErrorException) {
            val status = e.statusCode.value()
            val raw = errorBody(e.responseBodyAsString)
            when (status) {
                429 -> {
                    val cooldown = retryAfter(e) ?: Duration.ofSeconds(properties.rateLimitCooldownSeconds)
                    pool.cooldown(model, cooldown)
                    log.warn("Gemini 모델 {} 한도 초과(429), {}초 동안 제외합니다", model, cooldown.seconds)
                    return Attempt.Failed(Failure(ManualReviewReason.RATE_LIMITED, raw))
                }

                404 -> {
                    pool.cooldown(model, MISSING_MODEL_COOLDOWN)
                    log.warn("Gemini 모델 {} 을(를) 찾을 수 없습니다. 모델 이름을 확인하세요", model)
                    return Attempt.Failed(Failure(ManualReviewReason.AI_ERROR, raw))
                }

                else -> {
                    // 키가 잘못됐거나 요청이 거부된 경우라 다른 모델로 바꿔도 같은 결과다.
                    log.warn("Gemini rejected request (model={}, status={})", model, status)
                    return Attempt.Done(manualReview(ManualReviewReason.AI_ERROR, raw))
                }
            }
        } catch (e: HttpServerErrorException) {
            pool.cooldown(model, Duration.ofSeconds(properties.unavailableCooldownSeconds))
            log.warn("Gemini unavailable (model={}, status={})", model, e.statusCode.value())
            return Attempt.Failed(Failure(ManualReviewReason.MODEL_NOT_READY, errorBody(e.responseBodyAsString)))
        } catch (e: Exception) {
            pool.cooldown(model, Duration.ofSeconds(properties.unavailableCooldownSeconds))
            log.warn("Gemini call failed (model={})", model, e)
            return Attempt.Failed(Failure(ManualReviewReason.TIMEOUT, null))
        }
    }

    /** 429 응답의 `Retry-After`(초)가 있으면 그만큼, 너무 짧거나 길면 1~300초로 맞춰 제외한다. */
    private fun retryAfter(e: HttpClientErrorException): Duration? =
        e.responseHeaders?.getFirst("Retry-After")?.trim()?.toLongOrNull()?.coerceIn(1, 300)?.let(Duration::ofSeconds)

    private fun call(model: String, body: String): String? = geminiRestClient.post()
        .uri("/v1beta/models/{model}:generateContent", model)
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
        const val MAX_ERROR_BODY_CHARS = 2000

        /** 없는 모델(404)은 설정 오류일 가능성이 커서 오래 제외한다. */
        val MISSING_MODEL_COOLDOWN: Duration = Duration.ofMinutes(10)

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
