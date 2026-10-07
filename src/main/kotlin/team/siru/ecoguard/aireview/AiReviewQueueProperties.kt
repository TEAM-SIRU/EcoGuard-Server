package team.siru.ecoguard.aireview

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * AI 검수 대기열 설정. 제출이 한꺼번에 몰려도 AI 호출은 [workers] 개씩만 처리하고 나머지는 대기열에서 차례를 기다린다.
 * 대기열이 가득 차거나 [maxWaitSeconds] 를 넘기면 교사 수동 검토로 보낸다. (아침 인증 시간대 약 50명 기준의 기본값)
 */
@ConfigurationProperties(prefix = "ai-review.queue")
data class AiReviewQueueProperties(
    /** 동시에 AI 검수를 처리하는 작업 스레드 수. */
    val workers: Int = 2,
    /** 처리 차례를 기다릴 수 있는 최대 건수. 넘으면 `QUEUE_FULL` 로 수동 검토에 보낸다. */
    val capacity: Int = 200,
    /** 제출 후 이 시간(초) 안에 AI 응답을 받지 못하면 `TIMEOUT` 으로 수동 검토에 보낸다. */
    val maxWaitSeconds: Long = 300,
) {
    init {
        check(workers >= 1) { "ai-review.queue.workers 는 1 이상이어야 합니다." }
        check(capacity >= 1) { "ai-review.queue.capacity 는 1 이상이어야 합니다." }
        check(maxWaitSeconds >= 1) { "ai-review.queue.max-wait-seconds 는 1 이상이어야 합니다." }
    }
}
