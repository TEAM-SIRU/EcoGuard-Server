package team.siru.ecoguard.verification

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import team.siru.ecoguard.aireview.AiReviewDispatcher
import team.siru.ecoguard.aireview.ReviewJob
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.storage.FileStorageService
import team.siru.ecoguard.schoolcalendar.VacationService
import team.siru.ecoguard.user.UserRepository
import team.siru.ecoguard.verification.dto.MyVerificationResponse
import team.siru.ecoguard.verification.dto.SubmitVerificationResponse
import team.siru.ecoguard.verification.dto.TodayUnavailableReason
import team.siru.ecoguard.verification.dto.TodayVerificationResponse
import org.springframework.dao.DataIntegrityViolationException
import java.time.Clock
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Service
class VerificationService(
    private val verificationRepository: VerificationRepository,
    private val assignmentRepository: AssignmentRepository,
    private val userRepository: UserRepository,
    private val fileStorageService: FileStorageService,
    private val aiReviewDispatcher: AiReviewDispatcher,
    private val vacationService: VacationService,
    private val properties: VerificationProperties,
    private val clock: Clock,
) {

    @Transactional
    fun submit(
        studentId: Long,
        areaId: Long?,
        photo: MultipartFile,
        idempotencyKey: String? = null,
        startedAtOffset: OffsetDateTime? = null,
    ): SubmitVerificationResponse {
        val startedAt = startedAtOffset?.atZoneSameInstant(clock.zone)?.toLocalDateTime()
        // 같은 키로 이미 접수된 요청이면 새로 처리하지 않고 처음 접수 결과를 그대로 돌려준다 (마감 뒤 재전송도 포함).
        if (idempotencyKey != null) {
            verificationRepository.findByStudentIdAndIdempotencyKey(studentId, idempotencyKey)?.let {
                return SubmitVerificationResponse(it.id, it.status, it.createdAt)
            }
        }

        // 인증은 배정받은 구역에 대해서만 가능하다. areaId는 선택값이며, 보내면 배정 구역과 일치해야 한다.
        val area = assignmentRepository.findFirstByStudentIdOrderByCreatedAtDesc(studentId)?.area
            ?: throw BusinessException(ErrorCode.NO_ASSIGNMENT)
        if (areaId != null && areaId != area.id) {
            throw BusinessException(ErrorCode.NOT_ASSIGNED_AREA)
        }

        val now = LocalDateTime.now(clock)
        if (!CleaningTimeWindow.isCertificationDay(now.toLocalDate())) {
            throw BusinessException(ErrorCode.OUT_OF_CERTIFICATION_TIME)
        }
        if (vacationService.isVacation(now.toLocalDate())) {
            throw BusinessException(ErrorCode.VACATION_PERIOD)
        }
        if (!CleaningTimeWindow.isWithin(area.cleanTime, now.toLocalTime()) &&
            !isLateRetryAllowed(area.cleanTime, now, idempotencyKey, startedAt)
        ) {
            throw BusinessException(ErrorCode.OUT_OF_CERTIFICATION_TIME)
        }

        val today = now.toLocalDate()
        verificationRepository.findByStudentIdAndVerificationDate(studentId, today)?.let {
            throw BusinessException(ErrorCode.ALREADY_SUBMITTED_TODAY, submittedAt = it.createdAt)
        }

        val imageBytes = runCatching { photo.bytes }.getOrElse { throw BusinessException(ErrorCode.INVALID_IMAGE) }
        val photoUrl = fileStorageService.storeImage(imageBytes, photo.originalFilename, "verifications")
        // 이후 단계가 실패해 롤백되면(동시 제출 충돌 등) 이 사진을 가리키는 인증이 없으므로 같이 지운다.
        fileStorageService.deleteOnRollback(photoUrl)

        val student = userRepository.findById(studentId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }

        // 동시에 두 번 제출되면 위의 exists 검사를 둘 다 통과하므로, 유니크 제약 위반도 같은 오류로 변환한다.
        val verification = try {
            verificationRepository.saveAndFlush(
                Verification(
                    student = student,
                    area = area,
                    photoUrl = photoUrl,
                    verificationDate = today,
                    idempotencyKey = idempotencyKey,
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.ALREADY_SUBMITTED_TODAY)
        }

        // 비동기 검수가 커밋되지 않은 인증 행을 읽지 못하는 일이 없도록 커밋 이후에 검수를 시작한다.
        // 대기열에는 사진 바이트가 아니라 파일 경로만 넣는다. 사진은 차례가 와서 AI 에 보내기 직전에 파일에서 읽는다.
        val verificationId = verification.id
        val job = ReviewJob(
            photoUrl = photoUrl,
            zoneId = area.zoneCode,
            zoneName = area.name,
            zoneDescription = area.description,
            userId = student.studentNumber,
            queuedAt = clock.instant(),
        )
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                aiReviewDispatcher.dispatch(verificationId, job)
            }
        })

        return SubmitVerificationResponse(verification.id, verification.status, verification.createdAt)
    }

    /**
     * 마감 직전에 전송을 시작한 요청의 재시도를 마감 뒤에도 받아 준다.
     * 재시도 키가 있고, 전송 시작 시각이 인증 가능 시간 안이며, 지금이 마감 후 유예 시간 안일 때만 허용한다.
     */
    private fun isLateRetryAllowed(
        cleanTime: String?,
        now: LocalDateTime,
        idempotencyKey: String?,
        startedAt: LocalDateTime?,
    ): Boolean {
        if (idempotencyKey == null || startedAt == null || properties.lateRetryGraceMinutes <= 0) return false
        if (startedAt.isAfter(now) || startedAt.toLocalDate() != now.toLocalDate()) return false
        if (!CleaningTimeWindow.isWithin(cleanTime, startedAt.toLocalTime())) return false
        val end = CleaningTimeWindow.parse(cleanTime).second
        return !now.toLocalTime().isAfter(end.plusMinutes(properties.lateRetryGraceMinutes))
    }

    /** 인증 화면 진입용. 배정 구역, 인증 가능 시간, 서버 현재 시각, 오늘 제출 여부를 한 번에 돌려준다. */
    @Transactional(readOnly = true)
    fun getToday(studentId: Long): TodayVerificationResponse {
        val area = assignmentRepository.findFirstByStudentIdOrderByCreatedAtDesc(studentId)?.area
            ?: throw BusinessException(ErrorCode.NO_ASSIGNMENT)
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()
        val (start, end) = CleaningTimeWindow.parse(area.cleanTime)
        val submitted = verificationRepository.findByStudentIdAndVerificationDate(studentId, today)

        val unavailableReason = when {
            submitted != null -> TodayUnavailableReason.ALREADY_SUBMITTED
            !CleaningTimeWindow.isCertificationDay(today) -> TodayUnavailableReason.WEEKEND
            vacationService.isVacation(today) -> TodayUnavailableReason.VACATION
            now.toLocalTime().isBefore(start) -> TodayUnavailableReason.BEFORE_START
            now.toLocalTime().isAfter(end) -> TodayUnavailableReason.AFTER_END
            else -> null
        }
        return TodayVerificationResponse(
            serverTime = now,
            areaId = area.id,
            areaName = area.name,
            cleanTime = area.cleanTime,
            startTime = start,
            endTime = end,
            canSubmit = unavailableReason == null,
            unavailableReason = unavailableReason,
            submitted = submitted != null,
            verificationId = submitted?.id,
            status = submitted?.status,
            submittedAt = submitted?.createdAt,
        )
    }

    @Transactional(readOnly = true)
    fun getMyVerifications(studentId: Long): List<MyVerificationResponse> =
        verificationRepository.findByStudentIdOrderByVerificationDateDesc(studentId).map(MyVerificationResponse::from)
}
