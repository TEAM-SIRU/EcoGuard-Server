package team.siru.ecoguard.cleaningarea.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import team.siru.ecoguard.cleaningarea.Assignment
import team.siru.ecoguard.cleaningarea.CleaningArea

data class Coordinates(
    val x: Double,
    val y: Double,
)

data class AreaMapResponse(
    val areaId: Long,
    val zoneCode: String,
    val name: String,
    val coordinates: Coordinates,
    val isActive: Boolean,
) {
    companion object {
        fun from(area: CleaningArea) = AreaMapResponse(
            areaId = area.id,
            zoneCode = area.zoneCode,
            name = area.name,
            coordinates = Coordinates(area.x, area.y),
            isActive = area.isActive,
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
    val areaName: String,
    val description: String?,
    val cleanTime: String?,
    val mapCoordinates: Coordinates,
) {
    companion object {
        fun from(assignment: Assignment) = MyAssignmentResponse(
            areaName = assignment.area.name,
            description = assignment.area.description,
            cleanTime = assignment.area.cleanTime,
            mapCoordinates = Coordinates(assignment.area.x, assignment.area.y),
        )
    }
}
