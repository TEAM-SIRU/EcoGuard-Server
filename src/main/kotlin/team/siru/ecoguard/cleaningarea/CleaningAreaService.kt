package team.siru.ecoguard.cleaningarea

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.cleaningarea.dto.AreaMapResponse
import team.siru.ecoguard.cleaningarea.dto.AssignStudentsRequest
import team.siru.ecoguard.cleaningarea.dto.AssignmentResponse
import team.siru.ecoguard.cleaningarea.dto.MyAssignmentResponse
import team.siru.ecoguard.cleaningarea.dto.UpdateAreaRequest
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.user.UserRepository

@Service
class CleaningAreaService(
    private val cleaningAreaRepository: CleaningAreaRepository,
    private val assignmentRepository: AssignmentRepository,
    private val userRepository: UserRepository,
) {

    fun getAreas(): List<AreaMapResponse> =
        cleaningAreaRepository.findAll().map(AreaMapResponse::from)

    @Transactional
    fun assignStudents(areaId: Long, request: AssignStudentsRequest): List<AssignmentResponse> {
        val area = cleaningAreaRepository.findById(areaId).orElseThrow { BusinessException(ErrorCode.AREA_NOT_FOUND) }
        if (!area.isActive) {
            throw BusinessException(ErrorCode.INACTIVE_AREA)
        }

        val students = request.studentIds.map { studentId ->
            userRepository.findById(studentId).orElseThrow { BusinessException(ErrorCode.STUDENT_NOT_FOUND) }
        }

        val assignments = students.map { student -> assignmentRepository.save(Assignment(area = area, student = student)) }
        return assignments.map(AssignmentResponse::from)
    }

    @Transactional(readOnly = true)
    fun getMyAssignment(studentId: Long): MyAssignmentResponse {
        val assignment = assignmentRepository.findFirstByStudentIdOrderByCreatedAtDesc(studentId)
            ?: throw BusinessException(ErrorCode.NO_ASSIGNMENT)
        return MyAssignmentResponse.from(assignment)
    }

    @Transactional
    fun updateArea(areaId: Long, request: UpdateAreaRequest) {
        val area = cleaningAreaRepository.findById(areaId).orElseThrow { BusinessException(ErrorCode.AREA_NOT_FOUND) }
        if (request.isActive && !area.modelReady) {
            throw BusinessException(ErrorCode.ZONE_MODEL_NOT_READY)
        }
        area.isActive = request.isActive
    }
}
