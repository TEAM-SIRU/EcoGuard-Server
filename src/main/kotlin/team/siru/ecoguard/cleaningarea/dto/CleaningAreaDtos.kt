package team.siru.ecoguard.cleaningarea.dto

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import team.siru.ecoguard.cleaningarea.Assignment
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.cleaningarea.CleaningAreaSemester
import team.siru.ecoguard.user.User

private const val ZONE_CODE_DESCRIPTION =
    "도면의 칸을 찾는 구역 식별자. 앱 도면 리소스와 이 값으로 매칭한다 " +
        "(예: main_stair_a, main_entrance_a, geumbong_corridor_f2, connector_f3 … 전체는 `GET /cleaning-areas`)"

private const val CLEAN_TIME_DESCRIPTION = "인증 가능 시간 `HH:mm~HH:mm`. 값이 없으면 07:20~08:10 으로 처리한다"

@Schema(description = "구역에 배정된 학생")
data class AssignedStudent(
    @field:Schema(description = "학생의 사용자 ID", example = "12")
    val studentId: Long,
    @field:Schema(description = "학번", example = "2104", nullable = true)
    val studentNumber: String?,
    @field:Schema(description = "이름", example = "홍길동")
    val name: String,
) {
    companion object {
        fun from(student: User) = AssignedStudent(student.id, student.studentNumber, student.name)
    }
}

@Schema(description = "청소 구역과 배정 학생")
data class AreaMapResponse(
    @field:Schema(description = "구역 ID", example = "3")
    val areaId: Long,
    @field:Schema(description = ZONE_CODE_DESCRIPTION, example = "main_stair_a")
    val zoneCode: String,
    @field:Schema(description = "구역 이름", example = "본관 계단 A")
    val name: String,
    @field:Schema(description = "청소 범위 설명", example = "본관 계단 A 1층→4층 (1→2층, 2→3층, 3→4층 구간 / 1층 현관 제외)", nullable = true)
    val description: String?,
    @field:Schema(description = CLEAN_TIME_DESCRIPTION, example = "07:20~08:10", nullable = true)
    val cleanTime: String?,
    @field:Schema(description = "어느 학기 구역인지")
    val semester: CleaningAreaSemester,
    @field:Schema(description = "활성 여부. false 면 배정·인증 불가", example = "true")
    val isActive: Boolean,
    @field:Schema(description = "배정된 학생 (배정 순). 없으면 []")
    val assignedStudents: List<AssignedStudent>,
) {
    companion object {
        fun from(area: CleaningArea, assignments: List<Assignment>) = AreaMapResponse(
            areaId = area.id,
            zoneCode = area.zoneCode,
            name = area.name,
            description = area.description,
            cleanTime = area.cleanTime,
            semester = area.semester,
            isActive = area.isActive,
            assignedStudents = assignments.map { AssignedStudent.from(it.student) },
        )
    }
}

@Schema(description = "구역 배정 요청")
data class AssignStudentsRequest(
    @field:ArraySchema(
        arraySchema = Schema(
            description = "배정할 학생의 사용자 ID 목록 (1명 이상). 한 명이라도 이미 배정돼 있으면 전체가 거절된다",
            example = "[12, 15]",
        ),
        minItems = 1,
    )
    @field:NotEmpty
    val studentIds: List<Long>,
)

@Schema(description = "생성된 배정")
data class AssignmentResponse(
    @field:Schema(description = "배정 ID", example = "57")
    val assignmentId: Long,
    @field:Schema(description = "배정된 학생의 사용자 ID", example = "12")
    val studentId: Long,
) {
    companion object {
        fun from(assignment: Assignment) = AssignmentResponse(assignment.id, assignment.student.id)
    }
}

@Schema(description = "구역 활성 여부 변경 요청")
data class UpdateAreaRequest(
    @field:Schema(description = "true: 활성화, false: 비활성화", example = "true")
    @field:NotNull
    val isActive: Boolean,
)

@Schema(description = "내 배정 구역")
data class MyAssignmentResponse(
    @field:Schema(description = "구역 ID. 인증 제출 시 `areaId` 로 보낼 수 있다", example = "3")
    val areaId: Long,
    /** 앱 도면에서 내 구역 칸을 찾는 식별자 (예: main_stair_a) */
    @field:Schema(description = ZONE_CODE_DESCRIPTION, example = "main_stair_a")
    val zoneCode: String,
    @field:Schema(description = "구역 이름", example = "본관 계단 A")
    val areaName: String,
    @field:Schema(description = "청소 범위 설명", example = "본관 계단 A 1층→4층 (1→2층, 2→3층, 3→4층 구간 / 1층 현관 제외)", nullable = true)
    val description: String?,
    @field:Schema(description = CLEAN_TIME_DESCRIPTION, example = "07:20~08:10", nullable = true)
    val cleanTime: String?,
    /** 같은 구역에 함께 배정된 학생 (본인 포함) */
    @field:Schema(description = "같은 구역에 함께 배정된 학생 (본인 포함, 배정 순)")
    val members: List<AssignedStudent>,
) {
    companion object {
        fun from(assignment: Assignment, members: List<User>) = MyAssignmentResponse(
            areaId = assignment.area.id,
            zoneCode = assignment.area.zoneCode,
            areaName = assignment.area.name,
            description = assignment.area.description,
            cleanTime = assignment.area.cleanTime,
            members = members.map(AssignedStudent::from),
        )
    }
}
