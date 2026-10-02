package team.siru.ecoguard.recruitment

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.recruitment.dto.ApplicantResponse
import team.siru.ecoguard.recruitment.dto.ApplicationStatusResponse
import team.siru.ecoguard.recruitment.dto.ApplyRequest
import team.siru.ecoguard.recruitment.dto.ApplyResponse
import team.siru.ecoguard.recruitment.dto.ConfirmRecruitmentResponse
import team.siru.ecoguard.recruitment.dto.CreateRecruitmentRequest
import team.siru.ecoguard.recruitment.dto.CreateRecruitmentResponse
import team.siru.ecoguard.recruitment.dto.CurrentRecruitmentResponse
import team.siru.ecoguard.recruitment.dto.RecruitmentSummaryResponse
import team.siru.ecoguard.recruitment.dto.UpdateRecruitmentRequest
import team.siru.ecoguard.user.UserRepository
import java.time.Clock
import java.time.LocalDateTime

private const val MAX_COUNT_PER_CLASS = 6

@Service
class RecruitmentService(
    private val recruitmentRepository: RecruitmentRepository,
    private val applicationRepository: RecruitmentApplicationRepository,
    private val userRepository: UserRepository,
    private val assignmentRepository: AssignmentRepository,
    private val clock: Clock,
) {

    @Transactional
    fun createRecruitment(teacherId: Long, request: CreateRecruitmentRequest): CreateRecruitmentResponse {
        if (!request.startDate.isBefore(request.endDate)) {
            throw BusinessException(ErrorCode.INVALID_PERIOD)
        }
        if (request.maxCount !in 1..MAX_COUNT_PER_CLASS) {
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
        val now = LocalDateTime.now(clock)
        if (recruitment.isClosed(now)) {
            throw BusinessException(ErrorCode.RECRUITMENT_CLOSED)
        }
        request.maxCount?.let {
            if (it !in 1..MAX_COUNT_PER_CLASS) throw BusinessException(ErrorCode.INVALID_MAX_COUNT)
            if (it < applicationRepository.countByRecruitmentId(recruitmentId)) {
                throw BusinessException(ErrorCode.MAX_COUNT_BELOW_APPLICANTS)
            }
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
        val recruitment = recruitmentRepository.findWithLockById(recruitmentId)
            ?: throw BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND)
        val student = userRepository.findById(studentId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
        if (student.grade != recruitment.grade || student.classNo != recruitment.classNo) {
            throw BusinessException(ErrorCode.CLASS_MISMATCH)
        }
        val now = LocalDateTime.now(clock)
        if (!recruitment.isOpen(now)) {
            throw BusinessException(ErrorCode.OUT_OF_PERIOD)
        }
        if (applicationRepository.existsByRecruitmentIdAndStudentId(recruitmentId, studentId)) {
            throw BusinessException(ErrorCode.ALREADY_APPLIED)
        }
        if (applicationRepository.countByRecruitmentId(recruitmentId) >= recruitment.maxCount) {
            throw BusinessException(ErrorCode.RECRUITMENT_FULL)
        }

        val application = applicationRepository.save(
            RecruitmentApplication(recruitment = recruitment, student = student, motivation = request.motivation),
        )
        val order = applicationRepository.countByRecruitmentIdAndIdLessThanEqual(recruitmentId, application.id)
        return ApplyResponse(application.id, application.status, order, student.studentNumber, student.name)
    }

    /**
     * 선착순으로 모집 인원을 확정한다. 신청 순서 기준 정원 이내는 승인, 초과분은 반려한다.
     * 확정 후 추가 신청이 들어와도 다시 호출하면 같은 기준으로 재확정된다.
     */
    @Transactional
    fun confirmRecruitment(recruitmentId: Long): ConfirmRecruitmentResponse {
        val recruitment = getRecruitmentOrThrow(recruitmentId)
        val applications = applicationRepository.findByRecruitmentIdOrderByCreatedAtAscIdAsc(recruitmentId)
        applications.forEachIndexed { index, application ->
            application.status = if (index < recruitment.maxCount) ApplicationStatus.APPROVED else ApplicationStatus.REJECTED
        }
        return ConfirmRecruitmentResponse(
            approvedCount = applications.count { it.status == ApplicationStatus.APPROVED },
            rejectedCount = applications.count { it.status == ApplicationStatus.REJECTED },
        )
    }

    @Transactional(readOnly = true)
    fun getMyApplicationStatus(studentId: Long): ApplicationStatusResponse {
        val application = applicationRepository.findFirstByStudentIdOrderByCreatedAtDesc(studentId)
            ?: throw BusinessException(ErrorCode.NO_APPLICATION)
        val order = applicationRepository.countByRecruitmentIdAndIdLessThanEqual(application.recruitment.id, application.id)
        val assigned = assignmentRepository.existsByStudentId(studentId)
        return ApplicationStatusResponse(
            status = application.status,
            order = order,
            waitingForAssignment = application.status == ApplicationStatus.APPROVED && !assigned,
        )
    }

    /** 학생 앱: 내 반의 모집 공고. 진행 중인 모집을 우선하고, 없으면 가장 최근 모집(예정/마감)을 보여준다. */
    @Transactional(readOnly = true)
    fun getCurrentRecruitment(studentId: Long): CurrentRecruitmentResponse {
        val student = userRepository.findById(studentId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
        val grade = student.grade ?: throw BusinessException(ErrorCode.NO_ACTIVE_RECRUITMENT)
        val classNo = student.classNo ?: throw BusinessException(ErrorCode.NO_ACTIVE_RECRUITMENT)

        val now = LocalDateTime.now(clock)
        val recruitments = recruitmentRepository.findByGradeAndClassNoOrderByStartDateDesc(grade, classNo)
        val recruitment = recruitments.firstOrNull { it.isOpen(now) }
            ?: recruitments.firstOrNull()
            ?: throw BusinessException(ErrorCode.NO_ACTIVE_RECRUITMENT)

        val currentApplicants = applicationRepository.countByRecruitmentId(recruitment.id)
        val applied = applicationRepository.existsByRecruitmentIdAndStudentId(recruitment.id, studentId)
        return CurrentRecruitmentResponse.of(recruitment, currentApplicants, applied, now)
    }

    /** 교사 웹: 학년/반별 전체 모집 현황. */
    @Transactional(readOnly = true)
    fun getRecruitments(): List<RecruitmentSummaryResponse> {
        val now = LocalDateTime.now(clock)
        return recruitmentRepository.findAllByOrderByStartDateDescGradeAscClassNoAsc().map {
            RecruitmentSummaryResponse.of(it, applicationRepository.countByRecruitmentId(it.id), now)
        }
    }

    @Transactional(readOnly = true)
    fun getApplicants(recruitmentId: Long): List<ApplicantResponse> {
        getRecruitmentOrThrow(recruitmentId)
        return applicationRepository.findByRecruitmentIdOrderByCreatedAtAscIdAsc(recruitmentId)
            .mapIndexed { index, application -> ApplicantResponse.from(application, index + 1L) }
    }

    private fun getRecruitmentOrThrow(recruitmentId: Long): Recruitment =
        recruitmentRepository.findById(recruitmentId).orElseThrow { BusinessException(ErrorCode.RECRUITMENT_NOT_FOUND) }
}
