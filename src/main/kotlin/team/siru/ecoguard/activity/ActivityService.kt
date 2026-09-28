package team.siru.ecoguard.activity

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.activity.dto.MyActivityResponse
import team.siru.ecoguard.activity.dto.ServiceTimeLogItem
import team.siru.ecoguard.activity.dto.StudentActivityResponse
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.user.UserRepository
import team.siru.ecoguard.verification.VerificationRepository
import java.time.LocalDate

@Service
class ActivityService(
    private val serviceTimeLogRepository: ServiceTimeLogRepository,
    private val userRepository: UserRepository,
    private val assignmentRepository: AssignmentRepository,
    private val verificationRepository: VerificationRepository,
) {

    @Transactional
    fun accumulate(studentId: Long, minutes: Int, reason: ServiceTimeReason, areaName: String?, date: LocalDate = LocalDate.now()) {
        runCatching {
            val student = userRepository.findById(studentId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
            serviceTimeLogRepository.save(
                ServiceTimeLog(student = student, minutes = minutes, reason = reason, date = date, areaName = areaName),
            )
        }.getOrElse { throw BusinessException(ErrorCode.ACCUMULATION_FAILED) }
    }

    fun getMyActivity(studentId: Long): MyActivityResponse {
        val logs = serviceTimeLogRepository.findByStudentIdOrderByDateDesc(studentId)
        val total = serviceTimeLogRepository.sumMinutesByStudentId(studentId)
        return MyActivityResponse(total, logs.map(ServiceTimeLogItem::from))
    }

    @Transactional(readOnly = true)
    fun searchStudents(keyword: String): List<StudentActivityResponse> {
        val students = userRepository.findByNameContainingOrStudentNumberContaining(keyword, keyword)
            .filter { it.role == team.siru.ecoguard.user.Role.STUDENT }
        if (students.isEmpty()) {
            throw BusinessException(ErrorCode.NO_SEARCH_RESULT)
        }
        return students.map { student ->
            val assignment = assignmentRepository.findFirstByStudentIdOrderByCreatedAtDesc(student.id)
            val latestVerification = verificationRepository.findByStudentIdOrderByVerificationDateDesc(student.id).firstOrNull()
            StudentActivityResponse(
                studentId = student.id,
                name = student.name,
                area = assignment?.area?.name,
                photoUrl = latestVerification?.photoUrl,
                totalMinutes = serviceTimeLogRepository.sumMinutesByStudentId(student.id),
            )
        }
    }
}
