package team.siru.ecoguard.aireview

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gemini")
data class GeminiProperties(
    /** 비어 있으면 Gemini 를 호출하지 않고 모든 인증을 교사 수동 검토로 보낸다. */
    val apiKey: String = "",
    val model: String = "gemini-2.5-flash-lite",
    val baseUrl: String = "https://generativelanguage.googleapis.com",
    val timeoutMillis: Long = 15000,
    /** 무료 요금제는 분당 호출 수가 적으므로 동시에 보내는 요청 수를 제한한다. */
    val maxConcurrency: Int = 2,
    /** 429/503 응답 시 한 번 재시도하기 전 대기 시간 */
    val retryDelayMillis: Long = 2000,
)
