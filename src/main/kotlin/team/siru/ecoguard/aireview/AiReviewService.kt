package team.siru.ecoguard.aireview

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.activity.ServiceTimeReason
import team.siru.ecoguard.aireview.dto.ManualDecision
import team.siru.ecoguard.aireview.dto.ManualReviewItemResponse
import team.siru.ecoguard.aireview.dto.ReviewResultResponse
import team.siru.ecoguard.activity.ActivityService
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus

private const val VERIFICATION_MINUTES = 10

@Service
class AiReviewService(
    private val aiEvaluator: AiEvaluator,
    private val verificationRepository: VerificationRepository,
    private val aiReviewRepository: AiReviewRepository,
    private val activityService: ActivityService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @Transactional
    fun processReview(verificationId: Long, request: EvaluateRequest) {
        val verification = verificationRepository.findById(verificationId).orElse(null) ?: return
        // 이미 수동 검토로 넘어갔거나 처리된 인증이면 건드리지 않는다.
        if (verification.status != VerificationStatus.PROCESSING) return

        when (val outcome = aiEvaluator.evaluate(request)) {
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
                if (response.isPassed == true) {
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
                    verification.failReasons = (response.failReasons ?: emptyList()).toMutableList()
                }
            }

            is AiEvaluateOutcome.NeedsManualReview -> {
                outcome.rawResponse?.let {
                    aiReviewRepository.save(
                        AiReview(verification = verification, rawResponse = it, decision = "FAIL", isPassed = false),
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
        val verification = verificationRepository.findById(verificationId)
            .orElseThrow { BusinessException(ErrorCode.REVIEW_NOT_FOUND) }
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
