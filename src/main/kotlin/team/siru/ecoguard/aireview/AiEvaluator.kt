package team.siru.ecoguard.aireview

import java.time.Instant

/**
 * AI 검수 대기열에 넣는 작업. 사진 바이트 대신 **파일 경로**만 들고 있어서, 대기열에 많이 쌓여도 메모리를 거의 쓰지 않는다.
 * 사진은 차례가 와서 AI 에 보내기 직전에 파일에서 읽는다.
 */
data class ReviewJob(
    /** 저장된 사진의 URL(`/files/verifications/....jpg`). */
    val photoUrl: String,
    val zoneId: String,
    val zoneName: String,
    val zoneDescription: String?,
    val userId: String?,
    /** 제출이 접수된 시각. 대기 시간 상한을 이 시각부터 센다. */
    val queuedAt: Instant,
)

/** AI 검수 대상. 어떤 검수 구현(Gemini, 자체 AI 서버)을 쓰든 같은 입력을 받는다. */
data class EvaluateRequest(
    val imageBytes: ByteArray,
    /** 청소구역 코드 (CLEANING_AREA.zone_code). 자체 AI 서버의 zone_id 와 같은 값이다. */
    val zoneId: String,
    val zoneName: String,
    val zoneDescription: String?,
    val userId: String?,
    /** 제출이 접수된 시각. 대기 시간 상한의 기준이며, null 이면 검수를 시작한 시각을 기준으로 한다. */
    val queuedAt: Instant? = null,
)

/**
 * 청소 사진 검수 구현체. `ai-review.provider` 설정(gemini | ai-server)으로 하나만 활성화된다.
 * 구현체는 예외를 던지지 않고 실패를 [AiEvaluateOutcome.NeedsManualReview] 로 돌려줘야 한다.
 */
interface AiEvaluator {
    fun evaluate(request: EvaluateRequest): AiEvaluateOutcome
}
