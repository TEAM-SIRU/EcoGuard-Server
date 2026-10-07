package team.siru.ecoguard.aireview

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gemini")
data class GeminiProperties(
    /** 비어 있으면 Gemini 를 호출하지 않고 모든 인증을 교사 수동 검토로 보낸다. */
    val apiKey: String = "",
    /** 모델을 하나만 쓸 때의 이름. [models] 가 비어 있을 때만 쓴다. */
    val model: String = "gemini-2.5-flash-lite",
    /**
     * 우선순위 순서의 모델 목록. 각 항목은 `이름` 또는 `이름:분당요청한도`(예: `gemini-2.5-flash-lite:15`).
     * 요청이 몰리거나 한 모델이 429 를 내면 다음 모델로 넘어간다. 비어 있으면 [model] 하나만 쓴다.
     */
    val models: List<String> = emptyList(),
    /** [models] 항목에 한도를 쓰지 않았을 때의 모델당 분당 요청 한도. 0 이면 한도를 두지 않고 429 응답으로만 조절한다. */
    val rpmLimit: Int = 0,
    val baseUrl: String = "https://generativelanguage.googleapis.com",
    val timeoutMillis: Long = 15000,
    /** 429 를 받은 모델을 선택 대상에서 제외하는 시간(초). 응답에 `Retry-After` 가 있으면 그 값을 쓴다. */
    val rateLimitCooldownSeconds: Long = 60,
    /** 503, 5xx, 시간 초과가 난 모델을 선택 대상에서 제외하는 시간(초). */
    val unavailableCooldownSeconds: Long = 10,
    /** 한 건의 검수에서 Gemini 를 호출해 볼 최대 횟수. 넘으면 교사 수동 검토로 보낸다. */
    val maxAttempts: Int = 3,
) {
    /** 설정을 우선순위 순서의 모델 목록으로 풀어낸다. 같은 이름은 먼저 나온 것만 쓴다. */
    fun modelSpecs(): List<GeminiModelSpec> {
        val parsed = models.map { it.trim() }.filter { it.isNotEmpty() }.map { entry ->
            val colon = entry.lastIndexOf(':')
            val limit = if (colon > 0) entry.substring(colon + 1).trim().toIntOrNull() else null
            if (colon > 0 && limit != null) {
                GeminiModelSpec(entry.substring(0, colon).trim(), limit.coerceAtLeast(0))
            } else {
                GeminiModelSpec(entry, rpmLimit.coerceAtLeast(0))
            }
        }.distinctBy { it.name }
        return parsed.ifEmpty { listOf(GeminiModelSpec(model, rpmLimit.coerceAtLeast(0))) }
    }
}

/** 사용할 Gemini 모델 하나와 그 모델의 분당 요청 한도(0 이면 한도 없음). */
data class GeminiModelSpec(val name: String, val rpmLimit: Int)
