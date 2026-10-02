package team.siru.ecoguard.aireview

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

/**
 * AI 검수는 커밋 후 비동기로 실행되므로, 서버 재시작이나 예외로 끝까지 가지 못하면 인증이
 * 영원히 PROCESSING 으로 남는다(같은 날 재제출도 불가). 오래 머문 건은 교사 수동 검토로 넘긴다.
 */
@Component
class StaleReviewRecovery(
    private val verificationRepository: VerificationRepository,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = CHECK_INTERVAL_MILLIS, initialDelay = CHECK_INTERVAL_MILLIS)
    @Transactional
    fun recover() {
        val threshold = LocalDateTime.now(clock).minus(STALE_AFTER)
        val stale = verificationRepository.findByStatusAndCreatedAtBefore(VerificationStatus.PROCESSING, threshold)
        stale.forEach {
            it.status = VerificationStatus.MANUAL_REVIEW
            it.manualReviewReason = ManualReviewReason.TIMEOUT.name
        }
        if (stale.isNotEmpty()) {
            log.warn("검수가 끝나지 않은 인증 {}건을 수동 검토로 전환했습니다: {}", stale.size, stale.map { it.id })
        }
    }

    companion object {
        private const val CHECK_INTERVAL_MILLIS = 5 * 60 * 1000L
        private val STALE_AFTER: Duration = Duration.ofMinutes(10)
    }
}
