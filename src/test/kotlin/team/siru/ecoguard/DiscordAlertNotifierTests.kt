package team.siru.ecoguard

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import team.siru.ecoguard.common.alert.DiscordAlertNotifier
import team.siru.ecoguard.common.alert.DiscordAlertProperties
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.exception.GlobalExceptionHandler
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

class DiscordAlertNotifierTests {

    private val clock = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneId.of("Asia/Seoul"))

    @Test
    fun `without a webhook url the server error response is unchanged and nothing is sent`() {
        val notifier = DiscordAlertNotifier(DiscordAlertProperties(webhookUrl = ""), clock)
        val handler = GlobalExceptionHandler(notifier)

        val response = handler.handleException(IllegalStateException("boom"), MockHttpServletRequest("GET", "/api/v1/test"))

        assertEquals(500, response.statusCode.value())
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.name, response.body?.code)
        notifier.shutdown()
    }

    @Test
    fun `a failing webhook never breaks the error response`() {
        // 연결할 수 없는 주소: 전송은 별도 스레드에서 실패하고 응답에는 영향이 없어야 한다.
        val notifier = DiscordAlertNotifier(
            DiscordAlertProperties(webhookUrl = "http://127.0.0.1:1/webhook", timeoutMillis = 200),
            clock,
        )
        val handler = GlobalExceptionHandler(notifier)

        val response = handler.handleException(IllegalStateException("boom"), MockHttpServletRequest("POST", "/api/v1/test"))

        assertEquals(500, response.statusCode.value())
        notifier.shutdown()
    }
}
