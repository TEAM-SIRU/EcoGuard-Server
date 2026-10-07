package team.siru.ecoguard.aireview

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskRejectedException
import org.springframework.stereotype.Component

/**
 * 제출이 커밋된 직후 AI 검수를 대기열에 넣는다. 제출 응답은 이미 확정된 뒤라서, 여기서 어떤 일이 생겨도 예외를 밖으로 내보내지 않는다.
 *
 * - 대기열이 가득 차 거부되면 자동 반려하지 않고 교사 수동 검토(`QUEUE_FULL`)로 보낸다.
 * - 그 밖의 이유로 검수를 시작하지 못하면 로그만 남긴다. PROCESSING 으로 남은 인증은 일정 시간 뒤 [StaleReviewRecovery] 가 수동 검토로 넘긴다.
 */
@Component
class AiReviewDispatcher(
    private val aiReviewService: AiReviewService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun dispatch(verificationId: Long, job: ReviewJob) {
        try {
            aiReviewService.processReview(verificationId, job)
        } catch (e: TaskRejectedException) {
            log.warn("AI 검수 대기열이 가득 차 수동 검토로 보냅니다 (verificationId={})", verificationId)
            sendToManualReview(verificationId)
        } catch (e: Exception) {
            log.error("AI 검수를 시작하지 못했습니다 (verificationId={})", verificationId, e)
        }
    }

    private fun sendToManualReview(verificationId: Long) {
        try {
            aiReviewService.sendToManualReview(verificationId, ManualReviewReason.QUEUE_FULL)
        } catch (e: Exception) {
            log.error("인증을 수동 검토로 보내지 못했습니다 (verificationId={})", verificationId, e)
        }
    }
}
