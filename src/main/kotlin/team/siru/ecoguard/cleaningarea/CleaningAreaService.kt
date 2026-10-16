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
     * 활성 구역에 학생을 배정한다. 한 구역에 여러 명을 배정할 수 있지만 학생은 한 구역만 맡을 수 있다.
     * 요청에 이미 다른 구역에 배정된 학생이 있으면 아무도 배정하지 않고 ALREADY_ASSIGNED로 거절한다.
     * 같은 구역에 이미 배정된 학생은 그대로 둔다.
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

        val existingByStudent = students.associate { it.id to assignmentRepository.findByStudentId(it.id) }
        if (existingByStudent.values.any { list -> list.any { it.area.id != areaId } }) {
            throw BusinessException(ErrorCode.ALREADY_ASSIGNED)
        }

        val assignments = students.map { student ->
            existingByStudent.getValue(student.id).firstOrNull()
                ?: assignmentRepository.save(Assignment(area = area, student = student))
        }
        return assignments.map(AssignmentResponse::from)
    }

    /**
     * 잘못 배정한 학생을 구역에서 뺀다. 그 구역에 배정돼 있지 않으면 ASSIGNMENT_NOT_FOUND.
     * 같은 구역에 중복으로 쌓인 배정 행이 있어도 한 번에 모두 지운다.
     */
    @Transactional
    fun unassignStudent(areaId: Long, studentId: Long) {
        val assignments = assignmentRepository.findByAreaIdAndStudentId(areaId, studentId)
        if (assignments.isEmpty()) throw BusinessException(ErrorCode.ASSIGNMENT_NOT_FOUND)
        assignmentRepository.deleteAll(assignments)
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
