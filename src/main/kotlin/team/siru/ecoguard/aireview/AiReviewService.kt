package team.siru.ecoguard.aireview

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import team.siru.ecoguard.activity.ServiceTimeReason
import team.siru.ecoguard.aireview.dto.ManualDecision
import team.siru.ecoguard.aireview.dto.ManualReviewItemResponse
import team.siru.ecoguard.aireview.dto.ReviewResultResponse
import team.siru.ecoguard.activity.ActivityService
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.storage.FileStorageService
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus
import java.time.Clock
import java.time.Duration

private const val VERIFICATION_MINUTES = 10

@Service
class AiReviewService(
    private val aiEvaluator: AiEvaluator,
    private val verificationRepository: VerificationRepository,
    private val aiReviewRepository: AiReviewRepository,
    private val activityService: ActivityService,
    private val fileStorageService: FileStorageService,
    private val transactionTemplate: TransactionTemplate,
    private val queueProperties: AiReviewQueueProperties,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 전용 작업 실행기([AI_REVIEW_EXECUTOR])의 대기열에서 차례를 기다렸다가 실행된다. 대기열이 가득 차면 호출 단계에서
     * 거부되며, 그 처리는 호출한 쪽에서 [sendToManualReview] 로 한다.
     *
     * 외부 AI 호출은 수 초~수십 초가 걸리므로 트랜잭션(DB 커넥션) 밖에서 하고, 결과를 반영할 때만 짧게 트랜잭션을 연다.
     */
    @Async(AI_REVIEW_EXECUTOR)
    fun processReview(verificationId: Long, job: ReviewJob) {
        val isProcessing = transactionTemplate.execute {
            verificationRepository.findById(verificationId).orElse(null)?.status == VerificationStatus.PROCESSING
        }
        // 이미 수동 검토로 넘어갔거나 처리된 인증이면 AI 를 호출하지 않는다.
        if (!isProcessing) return

        // 대기열에서 너무 오래 기다렸으면 AI 를 부르지 않고 바로 교사에게 넘긴다. (학생이 결과를 하염없이 기다리지 않게 한다.)
        val waited = Duration.between(job.queuedAt, clock.instant())
        if (waited > Duration.ofSeconds(queueProperties.maxWaitSeconds)) {
            log.warn("AI 검수 대기 시간이 상한을 넘어 수동 검토로 보냅니다 (id={}, 대기 {}초)", verificationId, waited.seconds)
            transactionTemplate.executeWithoutResult {
                applyOutcome(verificationId, AiEvaluateOutcome.NeedsManualReview(ManualReviewReason.TIMEOUT))
            }
            return
        }

        // 사진은 대기열에 바이트로 쌓아 두지 않고, 차례가 왔을 때 파일에서 읽는다.
        val imageBytes = try {
            fileStorageService.read(job.photoUrl)
        } catch (e: Exception) {
            log.error("검수할 사진을 읽지 못해 수동 검토로 보냅니다 (id={})", verificationId, e)
            transactionTemplate.executeWithoutResult {
                applyOutcome(verificationId, AiEvaluateOutcome.NeedsManualReview(ManualReviewReason.AI_ERROR))
            }
            return
        }

        // 예상 못 한 예외가 나도 @Async 가 삼키므로, 10분 뒤 복구 작업을 기다리지 않고 바로 수동 검토로 보낸다.
        try {
            val outcome = aiEvaluator.evaluate(
                EvaluateRequest(
                    imageBytes = imageBytes,
                    zoneId = job.zoneId,
                    zoneName = job.zoneName,
                    zoneDescription = job.zoneDescription,
                    userId = job.userId,
                    queuedAt = job.queuedAt,
                ),
            )
            transactionTemplate.executeWithoutResult { applyOutcome(verificationId, outcome) }
        } catch (e: Exception) {
            log.error("AI 검수 중 예외가 나 수동 검토로 보냅니다 (id={})", verificationId, e)
            try {
                transactionTemplate.executeWithoutResult {
                    applyOutcome(verificationId, AiEvaluateOutcome.NeedsManualReview(ManualReviewReason.AI_ERROR))
                }
            } catch (inner: Exception) {
                log.error("수동 검토로 보내지도 못했습니다. 복구 작업이 처리합니다 (id={})", verificationId, inner)
            }
        }
    }

    /**
     * AI 검수를 거치지 않고 인증을 교사 수동 검토로 보낸다. 대기열이 가득 찼을 때처럼 제출을 받은 직후 호출한다.
     * 제출 트랜잭션의 커밋 직후(afterCommit)에 불리므로, 그 트랜잭션에 얹히지 않도록 새 트랜잭션에서 실행한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun sendToManualReview(verificationId: Long, reason: ManualReviewReason) {
        applyOutcome(verificationId, AiEvaluateOutcome.NeedsManualReview(reason))
    }

    private fun applyOutcome(verificationId: Long, outcome: AiEvaluateOutcome) {
        // AI 호출 중에 시간 초과 복구나 교사 처리로 상태가 바뀌었을 수 있으므로, 잠근 뒤 다시 확인한다.
        val verification = verificationRepository.findWithLockById(verificationId) ?: return
        if (verification.status != VerificationStatus.PROCESSING) {
            log.info("AI 검수 결과를 버렸습니다. 이미 처리된 인증입니다 (id={}, status={})", verificationId, verification.status)
            return
        }

        when (outcome) {
            is AiEvaluateOutcome.Success -> {
                val response = outcome.response
                aiReviewRepository.save(
                    AiReview(
                        verification = verification,
                        rawResponse = outcome.rawResponse,
                        decision = response.decision,
                        isPassed = response.isPassed,
                    ),
                )
                // 어떤 검수 구현이든 "통과"로 확실히 말한 경우에만 자동 승인한다. 그 외(불통과, 값 누락, 통과인데 사유가 있는 모순된 응답)는
                // 학생이 억울하게 반려되지 않도록 자동 반려하지 않고 교사 수동 검토로 보낸다. REJECTED 는 교사가 수동 검토에서 반려할 때만 생긴다.
                if (response.isPassed == true && response.failReasons.isNullOrEmpty()) {
                    verification.status = VerificationStatus.APPROVED
                    activityService.accumulate(
                        studentId = verification.student.id,
                        minutes = VERIFICATION_MINUTES,
                        reason = ServiceTimeReason.VERIFICATION,
                        areaName = verification.area.name,
                        date = verification.verificationDate,
                    )
                } else {
                    verification.failReasons = (response.failReasons ?: emptyList()).toMutableList()
                    verification.status = VerificationStatus.MANUAL_REVIEW
                    verification.manualReviewReason =
                        (if (response.isPassed == null) ManualReviewReason.AI_ERROR else ManualReviewReason.AI_FAILED).name
                }
            }

            is AiEvaluateOutcome.NeedsManualReview -> {
                // 응답 원문이 있으면(판정을 해석하지 못한 경우 포함) 분석할 수 있게 남기고, 판정은 AI 가 실제로 말한 값을 그대로 저장한다.
                // 수동 검토로 보낸 서버의 최종 처리와 이유는 인증의 status 와 manualReviewReason 에 따로 남는다.
                outcome.rawResponse?.let {
                    aiReviewRepository.save(
                        AiReview(verification = verification, rawResponse = it, decision = outcome.decision, isPassed = outcome.isPassed),
                    )
                }
                verification.failReasons = outcome.failReasons.toMutableList()
                verification.status = VerificationStatus.MANUAL_REVIEW
                verification.manualReviewReason = outcome.reason.name
            }
        }
    }

    @Transactional(readOnly = true)
    fun getManualReviewQueue(): List<ManualReviewItemResponse> =
        verificationRepository.findByStatusOrderByCreatedAtAsc(VerificationStatus.MANUAL_REVIEW)
            .map(ManualReviewItemResponse::from)

    @Transactional
    fun decide(verificationId: Long, decision: ManualDecision) {
        // 교사 두 명이 동시에 승인해도 봉사시간이 두 번 적립되지 않도록 잠근 뒤 상태를 확인한다.
        val verification = verificationRepository.findWithLockById(verificationId)
            ?: throw BusinessException(ErrorCode.REVIEW_NOT_FOUND)
        if (verification.status != VerificationStatus.MANUAL_REVIEW) {
            throw BusinessException(ErrorCode.NOT_MANUAL_REVIEW)
        }

        if (decision == ManualDecision.APPROVED) {
            verification.status = VerificationStatus.APPROVED
            activityService.accumulate(
                studentId = verification.student.id,
                minutes = VERIFICATION_MINUTES,
                reason = ServiceTimeReason.VERIFICATION,
                areaName = verification.area.name,
                date = verification.verificationDate,
            )
        } else {
            verification.status = VerificationStatus.REJECTED
        }
    }

    @Transactional(readOnly = true)
    fun getReviewResult(verificationId: Long, studentId: Long?): ReviewResultResponse {
        val verification = verificationRepository.findById(verificationId)
            .filter { studentId == null || it.student.id == studentId }
            .orElseThrow { BusinessException(ErrorCode.REVIEW_NOT_FOUND) }
        return ReviewResultResponse.from(verification)
    }
}
