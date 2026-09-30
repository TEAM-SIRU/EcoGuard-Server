package team.siru.ecoguard.cleaningarea.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import team.siru.ecoguard.cleaningarea.Assignment
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.user.User

data class Coordinates(
    val x: Double,
    val y: Double,
)

data class AssignedStudent(
    val studentId: Long,
    val studentNumber: String?,
    val name: String,
) {
    companion object {
        fun from(student: User) = AssignedStudent(student.id, student.studentNumber, student.name)
    }
}

data class AreaMapResponse(
    val areaId: Long,
    val zoneCode: String,
    val name: String,
    val description: String?,
    val cleanTime: String?,
    val coordinates: Coordinates,
    val isActive: Boolean,
    val assignedStudents: List<AssignedStudent>,
) {
    companion object {
        fun from(area: CleaningArea, assignments: List<Assignment>) = AreaMapResponse(
            areaId = area.id,
            zoneCode = area.zoneCode,
            name = area.name,
            description = area.description,
            cleanTime = area.cleanTime,
            coordinates = Coordinates(area.x, area.y),
            isActive = area.isActive,
            assignedStudents = assignments.map { AssignedStudent.from(it.student) },
        )
    }
}

data class AssignStudentsRequest(
    @field:NotEmpty
    val studentIds: List<Long>,
)

data class AssignmentResponse(
    val assignmentId: Long,
    val studentId: Long,
) {
    companion object {
        fun from(assignment: Assignment) = AssignmentResponse(assignment.id, assignment.student.id)
    }
}

data class UpdateAreaRequest(
    @field:NotNull
    val isActive: Boolean,
)

data class MyAssignmentResponse(
    val areaId: Long,
    val areaName: String,
    val description: String?,
    val cleanTime: String?,
    val mapCoordinates: Coordinates,
    /** 같은 구역에 함께 배정된 학생 (본인 포함) */
    val members: List<AssignedStudent>,
) {
    companion object {
        fun from(assignment: Assignment, members: List<User>) = MyAssignmentResponse(
            areaId = assignment.area.id,
            areaName = assignment.area.name,
            description = assignment.area.description,
            cleanTime = assignment.area.cleanTime,
            mapCoordinates = Coordinates(assignment.area.x, assignment.area.y),
            members = members.map(AssignedStudent::from),
        )
    }
}
