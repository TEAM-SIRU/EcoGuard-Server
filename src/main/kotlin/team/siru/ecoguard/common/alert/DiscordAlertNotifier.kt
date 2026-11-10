package team.siru.ecoguard.common.alert

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue

/**
 * 처리하지 못한 서버 에러(500)를 디스코드 채널로 알린다.
 * 알림은 별도 스레드에서 보내므로 디스코드가 느리거나 실패해도 API 응답에는 영향이 없다.
 */
@Component
class DiscordAlertNotifier(
    private val properties: DiscordAlertProperties,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.timeoutMillis)).build(),
            ).apply { setReadTimeout(Duration.ofMillis(properties.timeoutMillis)) },
        )
        .build()

    /** 알림 전송용 스레드 1개. 대기열이 꽉 차면 새 알림은 버린다(서버 자원을 알림에 쓰지 않도록). */
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(MAX_QUEUED_ALERTS),
        ThreadPoolExecutor.DiscardPolicy(),
    )

    /** 에러 종류 + API 별로 마지막으로 보낸 시각 */
    private val lastSentAt = ConcurrentHashMap<String, Instant>()

    fun notifyUnhandled(e: Exception, method: String, path: String) {
        if (properties.webhookUrl.isBlank()) return
        if (!passesCooldown("${e.javaClass.name} $method $path")) return

        val content = buildMessage(e, method, path)
        executor.execute {
            try {
                restClient.post()
                    .uri(properties.webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapOf("content" to content))
                    .retrieve()
                    .toBodilessEntity()
            } catch (ex: Exception) {
                log.warn("디스코드 에러 알림 전송 실패", ex)
            }
        }
    }

    private fun passesCooldown(key: String): Boolean {
        val now = clock.instant()
        var allowed = false
        lastSentAt.compute(key) { _, last ->
            if (last == null || Duration.between(last, now).seconds >= properties.cooldownSeconds) {
                allowed = true
                now
            } else {
                last
            }
        }
        return allowed
    }

    private fun buildMessage(e: Exception, method: String, path: String): String {
        val stack = e.stackTrace.take(STACK_LINES).joinToString("\n") { "  at $it" }
        val message = """
            |🚨 **서버 에러 (500)**
            |시각: ${LocalDateTime.now(clock)}
            |요청: `$method $path`
            |예외: `${e.javaClass.name}`
            |내용: ${e.message ?: "(없음)"}
            |```
            |$stack
            |```
        """.trimMargin()
        // 디스코드 메시지는 2000자까지만 보낼 수 있다.
        return if (message.length <= DISCORD_LIMIT) message else message.take(DISCORD_LIMIT - 4) + "\n```"
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdown()
    }

    private companion object {
        const val MAX_QUEUED_ALERTS = 20
        const val STACK_LINES = 8
        const val DISCORD_LIMIT = 1990
    }
}
