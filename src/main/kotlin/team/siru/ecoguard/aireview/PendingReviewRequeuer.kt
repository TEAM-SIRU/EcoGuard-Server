package team.siru.ecoguard.aireview

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus
import java.time.Clock

/**
 * 대기열은 서버 메모리에 있어서 재시작(배포 포함)하면 대기·처리 중이던 검수가 사라진다.
 * 서버가 뜰 때 PROCESSING 으로 남은 인증을 다시 대기열에 넣어, 학생이 수동 검토까지 기다리지 않고 AI 검수를 받게 한다.
 *
 * 대기 시간 상한은 재시작 시각부터 다시 센다. 다시 넣지 못한 건은 기존대로 [StaleReviewRecovery] 가 수동 검토로 넘긴다.
 */
@Component
class PendingReviewRequeuer(
    private val verificationRepository: VerificationRepository,
    private val dispatcher: AiReviewDispatcher,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun requeue() {
        val now = clock.instant()
        // 구역·학생은 지연 로딩이라 트랜잭션 안에서 작업 정보로 옮긴다. 대기열에 넣는 건 트랜잭션 밖에서 한다.
        val jobs = transactionTemplate.execute {
            verificationRepository.findByStatusOrderByCreatedAtAsc(VerificationStatus.PROCESSING).map {
                it.id to ReviewJob(
                    photoUrl = it.photoUrl,
                    zoneId = it.area.zoneCode,
                    zoneName = it.area.name,
                    zoneDescription = it.area.description,
                    userId = it.student.studentNumber,
                    queuedAt = now,
                )
            }
        }.orEmpty()
        if (jobs.isEmpty()) return

        log.info("재시작 전에 끝나지 않은 AI 검수 {}건을 다시 대기열에 넣습니다: {}", jobs.size, jobs.map { it.first })
        jobs.forEach { (id, job) -> dispatcher.dispatch(id, job) }
    }
}
