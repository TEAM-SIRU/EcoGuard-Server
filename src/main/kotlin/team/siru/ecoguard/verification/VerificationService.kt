package team.siru.ecoguard.verification

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import team.siru.ecoguard.aireview.AiReviewService
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.storage.FileStorageService
import team.siru.ecoguard.user.UserRepository
import team.siru.ecoguard.verification.dto.MyVerificationResponse
import team.siru.ecoguard.verification.dto.SubmitVerificationResponse
import java.time.LocalDateTime

@Service
class VerificationService(
    private val verificationRepository: VerificationRepository,
    private val assignmentRepository: AssignmentRepository,
    private val userRepository: UserRepository,
    private val fileStorageService: FileStorageService,
    private val aiReviewService: AiReviewService,
) {

    @Transactional
    fun submit(studentId: Long, areaId: Long?, photo: MultipartFile): SubmitVerificationResponse {
        // 인증은 배정받은 구역에 대해서만 가능하다. areaId는 선택값이며, 보내면 배정 구역과 일치해야 한다.
        val area = assignmentRepository.findFirstByStudentIdOrderByCreatedAtDesc(studentId)?.area
            ?: throw BusinessException(ErrorCode.NO_ASSIGNMENT)
        if (areaId != null && areaId != area.id) {
            throw BusinessException(ErrorCode.NOT_ASSIGNED_AREA)
        }

        val now = LocalDateTime.now()
        if (!CleaningTimeWindow.isWithin(area.cleanTime, now.toLocalTime())) {
            throw BusinessException(ErrorCode.OUT_OF_CERTIFICATION_TIME)
        }

        val today = now.toLocalDate()
        if (verificationRepository.existsByStudentIdAndVerificationDate(studentId, today)) {
            throw BusinessException(ErrorCode.ALREADY_SUBMITTED_TODAY)
        }

        val imageBytes = runCatching { photo.bytes }.getOrElse { throw BusinessException(ErrorCode.INVALID_IMAGE) }
        val photoUrl = fileStorageService.storeImage(imageBytes, photo.originalFilename, "verifications")

        val student = userRepository.findById(studentId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }

        val verification = verificationRepository.save(
            Verification(student = student, area = area, photoUrl = photoUrl, verificationDate = today),
        )

        // 비동기 검수가 커밋되지 않은 인증 행을 읽지 못하는 일이 없도록 커밋 이후에 검수를 시작한다.
        val verificationId = verification.id
        val zoneCode = area.zoneCode
        val studentNumber = student.studentNumber
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                aiReviewService.processReview(verificationId, imageBytes, zoneCode, studentNumber)
            }
        })

        return SubmitVerificationResponse(verification.id, verification.status)
    }

    @Transactional(readOnly = true)
    fun getMyVerifications(studentId: Long): List<MyVerificationResponse> =
        verificationRepository.findByStudentIdOrderByVerificationDateDesc(studentId).map(MyVerificationResponse::from)
}
