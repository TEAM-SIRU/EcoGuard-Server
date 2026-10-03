package team.siru.ecoguard

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import team.siru.ecoguard.aireview.AiEvaluateOutcome
import team.siru.ecoguard.aireview.EvaluateRequest
import team.siru.ecoguard.aireview.GeminiClient
import team.siru.ecoguard.aireview.GeminiProperties
import team.siru.ecoguard.aireview.ManualReviewReason
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@SpringBootTest
class GeminiClientTests @Autowired constructor(
    private val objectMapper: ObjectMapper,
) {

    private val request = EvaluateRequest(
        imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00),
        zoneId = "main_stair_a",
        zoneName = "본관 계단 A",
        zoneDescription = "본관 계단 A 1층→4층",
        userId = "10203",
    )

    private val url = "http://gemini.test/v1beta/models/test-model:generateContent"

    private fun newClient(apiKey: String = "test-key"): Pair<GeminiClient, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl("http://gemini.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val properties = GeminiProperties(apiKey = apiKey, model = "test-model", retryDelayMillis = 0)
        return GeminiClient(builder.build(), properties, objectMapper) to server
    }

    /** Gemini 가 구조화된 JSON 판정을 text 로 담아 돌려주는 응답 형태 */
    private fun geminiBody(verdictJson: String): String {
        val escaped = objectMapper.writeValueAsString(verdictJson)
        return """{"candidates":[{"content":{"parts":[{"text":$escaped}]}}]}"""
    }

    @Test
    fun `passed verdict is approved automatically and sends the key as a header`() {
        val (client, server) = newClient()
        server.expect(requestTo(url))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("x-goog-api-key", "test-key"))
            .andRespond(withSuccess(geminiBody("""{"is_passed":true,"fail_reasons":[],"reason":"ok"}"""), MediaType.APPLICATION_JSON))

        val outcome = client.evaluate(request)

        val success = assertIs<AiEvaluateOutcome.Success>(outcome)
        assertEquals(true, success.response.isPassed)
        assertEquals("PASS", success.response.decision)
        server.verify()
    }

    @Test
    fun `failed verdict goes to manual review with the AI reasons instead of being rejected`() {
        val (client, server) = newClient()
        server.expect(requestTo(url)).andRespond(
            withSuccess(
                geminiBody("""{"is_passed":false,"fail_reasons":["DUSTPAN_NOT_FOUND","SOMETHING_UNKNOWN"],"reason":"no dustpan"}"""),
                MediaType.APPLICATION_JSON,
            ),
        )

        val outcome = assertIs<AiEvaluateOutcome.NeedsManualReview>(client.evaluate(request))

        assertEquals(ManualReviewReason.AI_FAILED, outcome.reason)
        assertEquals(listOf("DUSTPAN_NOT_FOUND"), outcome.failReasons)
        assertTrue(outcome.rawResponse!!.contains("candidates"))
    }

    @Test
    fun `a pass that still lists fail reasons is not trusted`() {
        val (client, server) = newClient()
        server.expect(requestTo(url)).andRespond(
            withSuccess(
                geminiBody("""{"is_passed":true,"fail_reasons":["ZONE_ANOMALY_DETECTED"]}"""),
                MediaType.APPLICATION_JSON,
            ),
        )

        val outcome = assertIs<AiEvaluateOutcome.NeedsManualReview>(client.evaluate(request))

        assertEquals(ManualReviewReason.AI_FAILED, outcome.reason)
    }

    @Test
    fun `rate limit is retried once then goes to manual review`() {
        val (client, server) = newClient()
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        val outcome = assertIs<AiEvaluateOutcome.NeedsManualReview>(client.evaluate(request))

        assertEquals(ManualReviewReason.RATE_LIMITED, outcome.reason)
        server.verify()
    }

    @Test
    fun `rate limit followed by success is approved`() {
        val (client, server) = newClient()
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        server.expect(requestTo(url)).andRespond(
            withSuccess(geminiBody("""{"is_passed":true,"fail_reasons":[]}"""), MediaType.APPLICATION_JSON),
        )

        assertIs<AiEvaluateOutcome.Success>(client.evaluate(request))
        server.verify()
    }

    @Test
    fun `server error goes to manual review as model not ready`() {
        val (client, server) = newClient()
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val outcome = assertIs<AiEvaluateOutcome.NeedsManualReview>(client.evaluate(request))

        assertEquals(ManualReviewReason.MODEL_NOT_READY, outcome.reason)
    }

    @Test
    fun `rejected request such as a bad key goes to manual review as AI error`() {
        val (client, server) = newClient()
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.FORBIDDEN))

        val outcome = assertIs<AiEvaluateOutcome.NeedsManualReview>(client.evaluate(request))

        assertEquals(ManualReviewReason.AI_ERROR, outcome.reason)
    }

    @Test
    fun `unparseable or blocked response goes to manual review as AI error`() {
        val (client, server) = newClient()
        server.expect(requestTo(url))
            .andRespond(withSuccess("""{"promptFeedback":{"blockReason":"SAFETY"}}""", MediaType.APPLICATION_JSON))

        val outcome = assertIs<AiEvaluateOutcome.NeedsManualReview>(client.evaluate(request))

        assertEquals(ManualReviewReason.AI_ERROR, outcome.reason)
    }

    @Test
    fun `missing api key skips the call and goes to manual review`() {
        val (client, server) = newClient(apiKey = "")

        val outcome = assertIs<AiEvaluateOutcome.NeedsManualReview>(client.evaluate(request))

        assertEquals(ManualReviewReason.MODEL_NOT_READY, outcome.reason)
        server.verify()
    }
}
