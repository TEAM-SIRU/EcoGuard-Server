package team.siru.ecoguard.appeal

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.activity.ActivityService
import team.siru.ecoguard.activity.ServiceTimeReason
import team.siru.ecoguard.appeal.dto.AppealResponse
import team.siru.ecoguard.appeal.dto.CreateAppealRequest
import team.siru.ecoguard.appeal.dto.CreateAppealResponse
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.user.UserRepository
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus

private const val APPEAL_APPROVED_MINUTES = 10

@Service
class AppealService(
    private val appealRepository: AppealRepository,
    private val verificationRepository: VerificationRepository,
    private val userRepository: UserRepository,
    private val activityService: ActivityService,
) {

    @Transactional
    fun create(studentId: Long, verificationId: Long, request: CreateAppealRequest): CreateAppealResponse {
        val verification = verificationRepository.findById(verificationId)
            .orElseThrow { BusinessException(ErrorCode.VERIFICATION_NOT_FOUND) }
        if (verification.status == VerificationStatus.APPROVED) {
            throw BusinessException(ErrorCode.APPEAL_ON_APPROVED_VERIFICATION)
        }
        val student = userRepository.findById(studentId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }

        val appeal = appealRepository.save(
            Appeal(verification = verification, student = student, content = request.content),
        )
        return CreateAppealResponse(appeal.id, appeal.status)
    }

    @Transactional
    fun decide(appealId: Long, decision: AppealStatus) {
        val appeal = appealRepository.findById(appealId).orElseThrow { BusinessException(ErrorCode.APPEAL_NOT_FOUND) }
        if (appeal.status != AppealStatus.PENDING) {
            throw BusinessException(ErrorCode.ALREADY_PROCESSED)
        }

        if (decision == AppealStatus.APPROVED) {
            appeal.status = AppealStatus.APPROVED
            val verification = appeal.verification
            verification.status = VerificationStatus.APPROVED
            activityService.accumulate(
                studentId = appeal.student.id,
                minutes = APPEAL_APPROVED_MINUTES,
                reason = ServiceTimeReason.APPEAL_APPROVED,
                areaName = verification.area.name,
                date = verification.verificationDate,
            )
        } else {
            appeal.status = AppealStatus.REJECTED
        }
    }

    @Transactional(readOnly = true)
    fun getAppeals(status: AppealStatus?): List<AppealResponse> {
        val appeals = if (status != null) {
            appealRepository.findByStatusOrderByCreatedAtAsc(status)
        } else {
            appealRepository.findAll()
        }
        return appeals.map(AppealResponse::from)
    }
}
