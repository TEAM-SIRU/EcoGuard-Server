package team.siru.ecoguard.cleaningarea

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.siru.ecoguard.cleaningarea.dto.AreaMapResponse
import team.siru.ecoguard.cleaningarea.dto.AssignStudentsRequest
import team.siru.ecoguard.cleaningarea.dto.AssignmentResponse
import team.siru.ecoguard.cleaningarea.dto.MyAssignmentResponse
import team.siru.ecoguard.cleaningarea.dto.UpdateAreaRequest
import team.siru.ecoguard.common.exception.ApiErrors
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.SecurityUtils

@Tag(
    name = "3. 청소 구역 (Cleaning Area)",
    description = "학교 도면의 청소 구역과 학생 배정. 구역은 서버 설정으로 미리 만들어져 있고(생성 API 없음), " +
        "앱은 좌표 대신 `zoneCode` 로 도면의 칸을 찾아 표시한다. 학생 한 명은 한 구역만 맡는다.",
)
@RestController
@RequestMapping("/api/v1")
class CleaningAreaController(
    private val cleaningAreaService: CleaningAreaService,
) {

    @Operation(
        summary = "전체 청소 구역 + 배정 학생 목록",
        description = """
**[공통]** 모든 구역(비활성 포함)과 구역마다 배정된 학생 목록입니다. 도면 화면에서 `zoneCode` 로 칸을 찾아 표시합니다.

- `isActive=false` 인 구역은 배정·인증을 할 수 없는 구역입니다(도면에서 흐리게 표시 등).
- `assignedStudents` 는 배정된 순서(오래된 순)이며, 없으면 `[]` 입니다.
""",
    )
    @GetMapping("/cleaning-areas")
    fun getAreas(): ResponseEntity<List<AreaMapResponse>> =
        ResponseEntity.ok(cleaningAreaService.getAreas())

    @Operation(
        summary = "구역에 학생 배정",
        description = """
**[교사]** 활성 구역에 학생을 한 명 이상 배정합니다. 한 구역에 여러 명을 배정할 수 있습니다.

- 요청 중 **한 명이라도** 이미 어느 구역(같은 구역 포함)에 배정돼 있으면 아무도 배정하지 않고 `409 ALREADY_ASSIGNED` 입니다.
  구역을 바꾸려면 먼저 `DELETE /cleaning-areas/{areaId}/assignments/{studentId}` 로 해제한 뒤 다시 배정하세요.
- `studentIds` 중 없는 사용자이거나 학생이 아닌 계정이 있으면 `404 STUDENT_NOT_FOUND` 입니다(이때도 아무도 배정되지 않음).
- 중복된 ID 는 한 번만 처리됩니다. 배정된 학생에게는 `AREA_ASSIGNED` 알림이 갑니다.
""",
    )
    @PostMapping("/cleaning-areas/{areaId}/assignments")
    @ApiErrors(ErrorCode.AREA_NOT_FOUND, ErrorCode.INACTIVE_AREA, ErrorCode.STUDENT_NOT_FOUND, ErrorCode.ALREADY_ASSIGNED, success = 201)
    @PreAuthorize("hasRole('TEACHER')")
    fun assignStudents(
        @Parameter(description = "배정할 구역 ID (`GET /cleaning-areas` 의 `areaId`)", example = "3")
        @PathVariable areaId: Long,
        @Valid @RequestBody request: AssignStudentsRequest,
    ): ResponseEntity<List<AssignmentResponse>> {
        val response = cleaningAreaService.assignStudents(areaId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(
        summary = "구역 배정 해제",
        description = """
**[교사]** 학생을 그 구역에서 뺍니다. 응답 바디는 없습니다(`204`). 해제된 학생에게는 `AREA_UNASSIGNED` 알림이 갑니다.

그 학생이 이 구역에 배정돼 있지 않으면 `404 ASSIGNMENT_NOT_FOUND` 입니다.
""",
    )
    @DeleteMapping("/cleaning-areas/{areaId}/assignments/{studentId}")
    @ApiErrors(ErrorCode.ASSIGNMENT_NOT_FOUND, success = 204)
    @PreAuthorize("hasRole('TEACHER')")
    fun unassignStudent(
        @Parameter(description = "구역 ID", example = "3")
        @PathVariable areaId: Long,
        @Parameter(description = "배정을 해제할 학생의 사용자 ID (`assignedStudents[].studentId`)", example = "12")
        @PathVariable studentId: Long,
    ): ResponseEntity<Void> {
        cleaningAreaService.unassignStudent(areaId, studentId)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "내 배정 구역",
        description = """
**[학생]** 내가 맡은 구역과, 같은 구역에 함께 배정된 학생(본인 포함) 목록입니다.

- 아직 배정되지 않았으면 `404 NO_ASSIGNMENT` 입니다. 홈에서 "배정 대기" 상태로 표시하면 됩니다.
  (모집 신청 후 배정 대기 여부는 `GET /applications/me` 의 `waitingForAssignment` 로도 알 수 있습니다.)
- `cleanTime` 이 인증 가능 시간입니다. 실제 제출 가능 여부는 `GET /verifications/today` 를 기준으로 하세요.
""",
    )
    @GetMapping("/assignments/me")
    @ApiErrors(ErrorCode.NO_ASSIGNMENT)
    @PreAuthorize("hasRole('STUDENT')")
    fun getMyAssignment(): ResponseEntity<MyAssignmentResponse> =
        ResponseEntity.ok(cleaningAreaService.getMyAssignment(SecurityUtils.currentUserId()))

    @Operation(
        summary = "구역 활성/비활성 전환",
        description = """
**[교사]** 구역을 켜거나 끕니다. 응답 바디는 없습니다(`200`).

- AI 검수 모델이 준비되지 않은 구역을 켜려고 하면 `409 ZONE_MODEL_NOT_READY` 입니다.
- 끄더라도 기존 배정은 그대로 남습니다. 비활성 구역에는 새로 배정할 수 없습니다.
""",
    )
    @PatchMapping("/cleaning-areas/{areaId}")
    @ApiErrors(ErrorCode.AREA_NOT_FOUND, ErrorCode.ZONE_MODEL_NOT_READY)
    @PreAuthorize("hasRole('TEACHER')")
    fun updateArea(
        @Parameter(description = "구역 ID", example = "3")
        @PathVariable areaId: Long,
        @Valid @RequestBody request: UpdateAreaRequest,
    ): ResponseEntity<Void> {
        cleaningAreaService.updateArea(areaId, request)
        return ResponseEntity.ok().build()
    }
}
