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
     * 요청에 이미 배정된 학생(같은 구역이든 다른 구역이든)이 있으면 아무도 배정하지 않고 ALREADY_ASSIGNED로 거절한다.
     * 같은 학생에 대한 동시 요청이 중복 배정을 만들지 않도록 학생 행을 잠근 뒤 확인한다.
     */
    @Transactional
    fun assignStudents(areaId: Long, request: AssignStudentsRequest): List<AssignmentResponse> {
        val area = cleaningAreaRepository.findById(areaId).orElseThrow { BusinessException(ErrorCode.AREA_NOT_FOUND) }
        if (!area.isActive) {
            throw BusinessException(ErrorCode.INACTIVE_AREA)
        }

        // 교착을 피하려고 항상 id 순서로 잠근다.
        val students = request.studentIds.distinct().sorted().map { studentId ->
            userRepository.findWithLockById(studentId)
                ?.takeIf { it.role == Role.STUDENT }
                ?: throw BusinessException(ErrorCode.STUDENT_NOT_FOUND)
        }

        if (students.any { assignmentRepository.existsByStudentId(it.id) }) {
            throw BusinessException(ErrorCode.ALREADY_ASSIGNED)
        }

        return students
            .map { assignmentRepository.save(Assignment(area = area, student = it)) }
            .map(AssignmentResponse::from)
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
