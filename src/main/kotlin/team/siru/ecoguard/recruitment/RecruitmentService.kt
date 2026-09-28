package team.siru.ecoguard.recruitment

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.recruitment.dto.ApplicantResponse
import team.siru.ecoguard.recruitment.dto.ApplicationStatusResponse
import team.siru.ecoguard.recruitment.dto.ApplyRequest
import team.siru.ecoguard.recruitment.dto.ApplyResponse
import team.siru.ecoguard.recruitment.dto.CreateRecruitmentRequest
import team.siru.ecoguard.recruitment.dto.CreateRecruitmentResponse
import team.siru.ecoguard.recruitment.dto.CurrentRecruitmentResponse
import team.siru.ecoguard.recruitment.dto.UpdateRecruitmentRequest
import team.siru.ecoguard.user.UserRepository
import java.time.LocalDateTime

@Service
class RecruitmentService(
    private val recruitmentRepository: RecruitmentRepository,
    private val applicationRepository: RecruitmentApplicationRepository,
    private val userRepository: UserRepository,
) {

    @Transactional
    fun createRecruitment(teacherId: Long, request: CreateRecruitmentRequest): CreateRecruitmentResponse {
        if (!request.startDate.isBefore(request.endDate)) {
            throw BusinessException(ErrorCode.INVALID_PERIOD)
        }
        if (request.maxCount !in 1..6) {
            throw BusinessException(ErrorCode.INVALID_MAX_COUNT)
        }
        val teacher = userRepository.findById(teacherId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }

        val recruitment = recruitmentRepository.save(
            Recruitment(
                semester = request.semester,
                grade = request.grade,
                classNo = request.classNo,
                maxCount = request.maxCount,
                startDate = request.startDate,
                endDate = request.endDate,
                teacher = teacher,
            ),
        )
        return CreateRecruitmentResponse(recruitment.id)
    }

    @Transactional
    fun updateRecruitment(recruitmentId: Long, request: UpdateRecruitmentRequest) {
        val recruitment = getRecruitmentOrThrow(recruitmentId)
        val now = LocalDateTime.now()
        if (recruitment.isClosed(now)) {
            throw BusinessException(ErrorCode.RECRUITMENT_CLOSED)
        }
        request.maxCount?.let {
            if (it !in 1..6) throw BusinessException(ErrorCode.INVALID_MAX_COUNT)
            recruitment.maxCount = it
        }
        val newStart = request.startDate ?: recruitment.startDate
        val newEnd = request.endDate ?: recruitment.endDate
        if (!newStart.isBefore(newEnd)) {
            throw BusinessException(ErrorCode.INVALID_PERIOD)
        }
        recruitment.startDate = newStart
        recruitment.endDate = newEnd
    }

    @Transactional
    fun apply(studentId: Long, recruitmentId: Long, request: ApplyRequest): ApplyResponse {
        val recruitment = getRecruitmentOrThrow(recruitmentId)
        val now = LocalDateTime.now()
        if (!recruitment.isOpen(now)) {
            throw BusinessException(ErrorCode.OUT_OF_PERIOD)
        }
        if (applicationRepository.existsByRecruitmentIdAndStudentId(recruitmentId, studentId)) {
            throw BusinessException(ErrorCode.ALREADY_APPLIED)
        }
        if (applicationRepository.countByRecruitmentId(recruitmentId) >= recruitment.maxCount) {
            throw BusinessException(ErrorCode.RECRUITMENT_FULL)
        }
        val student = userRepository.findById(studentId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }

        val application = applicationRepository.save(
            RecruitmentApplication(recruitment = recruitment, student = student, motivation = request.motivation),
        )
        return ApplyResponse(application.id, application.status)
    }

    fun getMyApplicationStatus(studentId: Long): ApplicationStatusResponse {
        val application = applicationRepository.findFirstByStudentIdOrderByCreatedAtDesc(studentId)
            ?: throw BusinessException(ErrorCode.NO_APPLICATION)
        return ApplicationStatusResponse(application.status)
    }

    fun getCurrentRecruitment(): CurrentRecruitmentResponse {
        val now = LocalDateTime.now()
        val recruitment = recruitmentRepository
            .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(now, now)
            ?: throw BusinessException(ErrorCode.NO_ACTIVE_RECRUITMENT)
        val currentApplicants = applicationRepository.countByRecruitmentId(recruitment.id)
        return CurrentRecruitmentResponse.of(recruitment, currentApplicants)
    }

    @Transactional(readOnly = true)
    fun getApplicants(recruitmentId: Long): List<ApplicantResponse> {
        getRecruitmentOrThrow(recruitmentId)
        return applicationRepository.findByRecruitmentIdOrderByCreatedAtAsc(recruitmentId).map(ApplicantResponse::from)
    }

    private fun getRecruitmentOrThrow(recruitmentId: Long): Recruitment =
        recruitmentRepository.findById(recruitmentId).orElseThrow { BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND) }
}
