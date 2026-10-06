package team.siru.ecoguard.appeal

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import team.siru.ecoguard.activity.ActivityService
import team.siru.ecoguard.activity.ServiceTimeReason
import team.siru.ecoguard.appeal.dto.AppealResponse
import team.siru.ecoguard.appeal.dto.CreateAppealResponse
import team.siru.ecoguard.appeal.dto.MyAppealResponse
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.storage.FileStorageService
import team.siru.ecoguard.user.UserRepository
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus

const val APPEAL_APPROVED_MINUTES = 10
private const val MAX_PHOTOS = 3

@Service
class AppealService(
    private val appealRepository: AppealRepository,
    private val verificationRepository: VerificationRepository,
    private val userRepository: UserRepository,
    private val activityService: ActivityService,
    private val fileStorageService: FileStorageService,
) {

    /**
     * 반려된 본인 인증에 대해 횟수 제한 없이 이의신청할 수 있다. 단, 검토 중인 신청이 있으면 결과가 나온 뒤 재신청한다.
     * 사진은 선택이며 최대 [MAX_PHOTOS]장까지 첨부할 수 있다.
     */
    @Transactional
    fun create(
        studentId: Long,
        verificationId: Long,
        content: String,
        photos: List<MultipartFile> = emptyList(),
    ): CreateAppealResponse {
        if (photos.size > MAX_PHOTOS) {
            throw BusinessException(ErrorCode.TOO_MANY_APPEAL_PHOTOS)
        }
        val verification = verificationRepository.findById(verificationId)
            .orElseThrow { BusinessException(ErrorCode.VERIFICATION_NOT_FOUND) }
        if (verification.student.id != studentId || verification.status != VerificationStatus.REJECTED) {
            throw BusinessException(ErrorCode.APPEAL_NOT_ALLOWED)
        }
        if (appealRepository.existsByVerificationIdAndStatus(verificationId, AppealStatus.PENDING)) {
            throw BusinessException(ErrorCode.APPEAL_ALREADY_PENDING)
        }
        val student = userRepository.findById(studentId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }

        val photoUrls = photos.map { photo ->
            val bytes = runCatching { photo.bytes }.getOrElse { throw BusinessException(ErrorCode.INVALID_IMAGE) }
            fileStorageService.storeImage(bytes, photo.originalFilename, "appeals")
        }

        val round = appealRepository.countByVerificationId(verificationId).toInt() + 1
        val appeal = appealRepository.save(
            Appeal(
                verification = verification,
                student = student,
                content = content,
                round = round,
                photoUrls = photoUrls.toMutableList(),
            ),
        )
        return CreateAppealResponse(appeal.id, appeal.status, appeal.round)
    }

    @Transactional
    fun decide(appealId: Long, rawDecision: String?, reply: String?) {
        val decision = AppealStatus.entries.firstOrNull { it.name == rawDecision && it != AppealStatus.PENDING }
            ?: throw BusinessException(ErrorCode.INVALID_DECISION)
        val appeal = appealRepository.findWithLockById(appealId) ?: throw BusinessException(ErrorCode.APPEAL_NOT_FOUND)
        if (appeal.status != AppealStatus.PENDING) {
            throw BusinessException(ErrorCode.ALREADY_PROCESSED)
        }

        appeal.status = decision
        appeal.reply = reply
        if (decision == AppealStatus.APPROVED) {
            val verification = appeal.verification
            if (verification.status != VerificationStatus.APPROVED) {
                verification.status = VerificationStatus.APPROVED
                activityService.accumulate(
                    studentId = appeal.student.id,
                    minutes = APPEAL_APPROVED_MINUTES,
                    reason = ServiceTimeReason.APPEAL_APPROVED,
                    areaName = verification.area.name,
                    date = verification.verificationDate,
                )
            }
        }
    }

    @Transactional(readOnly = true)
    fun getAppeals(status: AppealStatus?): List<AppealResponse> {
        val appeals = if (status != null) {
            appealRepository.findByStatusOrderByCreatedAtAsc(status)
        } else {
            appealRepository.findAllByOrderByCreatedAtAscIdAsc()
        }
        return appeals.map(AppealResponse::from)
    }

    @Transactional(readOnly = true)
    fun getMyAppeals(studentId: Long): List<MyAppealResponse> =
        appealRepository.findByStudentIdOrderByCreatedAtDescIdDesc(studentId).map(MyAppealResponse::from)
}
