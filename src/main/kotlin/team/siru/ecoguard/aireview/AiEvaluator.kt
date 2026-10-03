package team.siru.ecoguard.aireview

/** AI 검수 대상. 어떤 검수 구현(Gemini, 자체 AI 서버)을 쓰든 같은 입력을 받는다. */
data class EvaluateRequest(
    val imageBytes: ByteArray,
    /** 청소구역 코드 (CLEANING_AREA.zone_code). 자체 AI 서버의 zone_id 와 같은 값이다. */
    val zoneId: String,
    val zoneName: String,
    val zoneDescription: String?,
    val userId: String?,
)

/**
 * 청소 인증 사진 검수 구현체. `ai-review.provider` 설정(gemini | ai-server)으로 하나만 활성화된다.
 * 구현체는 예외를 던지지 않고 실패를 [AiEvaluateOutcome.NeedsManualReview] 로 돌려줘야 한다.
 */
interface AiEvaluator {
    fun evaluate(request: EvaluateRequest): AiEvaluateOutcome
}
