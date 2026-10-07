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
import team.siru.ecoguard.user.Role
import team.siru.ecoguard.user.UserRepository

@Service
class CleaningAreaService(
    private val cleaningAreaRepository: CleaningAreaRepository,
    private val assignmentRepository: AssignmentRepository,
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun getAreas(): List<AreaMapResponse> {
        val assignmentsByArea = assignmentRepository.findAllWithStudent().groupBy { it.area.id }
        return cleaningAreaRepository.findAll().map { AreaMapResponse.from(it, assignmentsByArea[it.id].orEmpty()) }
    }

    /**
     * 활성 구역에 학생을 배정한다. 한 구역에 여러 명을 배정할 수 있고,
     * 학생은 한 구역만 담당하므로 다른 구역에 배정돼 있던 학생은 이 구역으로 옮겨진다.
     */
    @Transactional
    fun assignStudents(areaId: Long, request: AssignStudentsRequest): List<AssignmentResponse> {
        val area = cleaningAreaRepository.findById(areaId).orElseThrow { BusinessException(ErrorCode.AREA_NOT_FOUND) }
        if (!area.isActive) {
            throw BusinessException(ErrorCode.INACTIVE_AREA)
        }

        val students = request.studentIds.distinct().map { studentId ->
            userRepository.findById(studentId)
                .filter { it.role == Role.STUDENT }
                .orElseThrow { BusinessException(ErrorCode.STUDENT_NOT_FOUND) }
        }

        val assignments = students.map { student ->
            val existing = assignmentRepository.findByStudentId(student.id)
            existing.firstOrNull { it.area.id == areaId }
                ?: run {
                    assignmentRepository.deleteAll(existing)
                    assignmentRepository.save(Assignment(area = area, student = student))
                }
        }
        return assignments.map(AssignmentResponse::from)
    }

    @Transactional(readOnly = true)
    fun getMyAssignment(studentId: Long): MyAssignmentResponse {
        val assignment = assignmentRepository.findFirstByStudentIdOrderByCreatedAtDesc(studentId)
            ?: throw BusinessException(ErrorCode.NO_ASSIGNMENT)
        val members = assignmentRepository.findByAreaIdOrderByCreatedAtAsc(assignment.area.id).map { it.student }
        return MyAssignmentResponse.from(assignment, members)
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
