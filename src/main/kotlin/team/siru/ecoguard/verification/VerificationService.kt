package team.siru.ecoguard.verification

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import team.siru.ecoguard.aireview.AiReviewService
import team.siru.ecoguard.cleaningarea.CleaningAreaRepository
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
    private val cleaningAreaRepository: CleaningAreaRepository,
    private val userRepository: UserRepository,
    private val fileStorageService: FileStorageService,
    private val aiReviewService: AiReviewService,
) {

    @Transactional
    fun submit(studentId: Long, areaId: Long, photo: MultipartFile): SubmitVerificationResponse {
        val area = cleaningAreaRepository.findById(areaId).orElseThrow { BusinessException(ErrorCode.AREA_NOT_FOUND) }

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

        aiReviewService.processReview(verification.id, imageBytes, area.zoneCode, student.studentNumber)

        return SubmitVerificationResponse(verification.id, verification.status)
    }

    @Transactional(readOnly = true)
    fun getMyVerifications(studentId: Long): List<MyVerificationResponse> =
        verificationRepository.findByStudentIdOrderByVerificationDateDesc(studentId).map(MyVerificationResponse::from)
}
