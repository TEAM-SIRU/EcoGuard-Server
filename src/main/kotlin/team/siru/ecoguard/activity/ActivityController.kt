package team.siru.ecoguard.activity

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.siru.ecoguard.activity.dto.MyActivityResponse
import team.siru.ecoguard.activity.dto.StudentActivityResponse
import team.siru.ecoguard.activity.dto.WeeklyActivityResponse
import team.siru.ecoguard.activity.dto.WeeklyParticipationResponse
import team.siru.ecoguard.common.exception.ApiErrors
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.SecurityUtils

private const val MONTHLY_DESCRIPTION_RULES = """
- `year`, `month` 를 생략하면 서버 기준 이번 달입니다. 하나만 주면 나머지는 이번 달 값으로 채웁니다. 13월처럼 없는 달이면 `400 VALIDATION_ERROR`.
- `records` 는 **평일만**, 날짜 최신순입니다. 제출한 날은 그 인증의 상태, 인증 시간이 지났는데 제출하지 않은 날은 `NOT_SUBMITTED` 입니다.
- 첫 배정(또는 첫 제출) 이전 날, 방학, 주말, 아직 인증 시간이 지나지 않은 날(`UPCOMING`)은 `records` 에 없습니다.
- `summary.requiredDays` 는 이번 달 남은 평일까지 포함한 청소해야 하는 날 수입니다.
- `minutes` / `monthlyMinutes` 는 그날·그달 실제 적립된 봉사시간(분)이며, 인증 승인과 이의신청 승인 적립을 합친 값입니다.
"""

@Tag(name = "8. 봉사시간·활동 (Activity)", description = "학생의 월별·주별 청소 기록과 누적 봉사시간, 교사용 학생 검색과 참여율")
@RestController
@RequestMapping("/api/v1")
class ActivityController(
    private val activityService: ActivityService,
) {

    @Operation(
        summary = "내 월별 활동 기록",
        description = "**[학생]** 한 달 동안의 날짜별 인증 결과, 승인/반려/미제출 집계, 누적·월간 봉사시간입니다.\n" +
            MONTHLY_DESCRIPTION_RULES,
    )
    @GetMapping("/service-times/me")
    @ApiErrors(ErrorCode.VALIDATION_ERROR)
    @PreAuthorize("hasRole('STUDENT')")
    fun getMyActivity(
        @Parameter(description = "선택. 조회 연도 (생략 시 올해)", example = "2026")
        @RequestParam(required = false) year: Int?,
        @Parameter(description = "선택. 조회 월 1~12 (생략 시 이번 달)", example = "10")
        @RequestParam(required = false) month: Int?,
    ): ResponseEntity<MyActivityResponse> =
        ResponseEntity.ok(activityService.getMonthlyActivity(SecurityUtils.currentUserId(), year, month))

    @Operation(
        summary = "이번 주 청소 현황 (홈 N/5일)",
        description = """
**[학생]** 이번 주 월~금 중 며칠 인증이 승인됐는지(`completedDays` / `requiredDays`)와 요일별 결과입니다.

- `days` 에는 배정 이전 날과 방학인 날이 빠지므로 5개보다 적을 수 있습니다. 아직 오지 않은 날은 `UPCOMING` 입니다.
- `requiredDays` 는 5에서 방학인 평일 수를 뺀 값입니다.
""",
    )
    @GetMapping("/service-times/me/weekly")
    @PreAuthorize("hasRole('STUDENT')")
    fun getMyWeeklyActivity(): ResponseEntity<WeeklyActivityResponse> =
        ResponseEntity.ok(activityService.getWeeklyActivity(SecurityUtils.currentUserId()))

    @Operation(
        summary = "이번 주 요일별 참여율 (교사 홈)",
        description = """
**[교사]** 이번 주 월~금 요일마다, 청소 구역에 배정된 학생 중 인증을 제출한 비율입니다. `days` 는 항상 5개입니다.

`participationRate = round(submittedCount / assignedCount × 100)` (배정 학생이 없으면 0).
""",
    )
    @GetMapping("/service-times/weekly")
    @PreAuthorize("hasRole('TEACHER')")
    fun getWeeklyParticipation(): ResponseEntity<WeeklyParticipationResponse> =
        ResponseEntity.ok(activityService.getWeeklyParticipation())

    @Operation(
        summary = "학생 검색 + 활동 요약 (교사)",
        description = """
**[교사]** 이름 또는 학번에 `keyword` 가 들어간 학생 목록과 각자의 출석 일수·누적 봉사시간입니다.
`keyword` 를 생략하거나 비우면 전체 학생입니다. 결과가 없으면 `[]` 가 아니라 `404 NO_SEARCH_RESULT` 입니다.
""",
    )
    @GetMapping("/students/activities")
    @ApiErrors(ErrorCode.NO_SEARCH_RESULT)
    @PreAuthorize("hasRole('TEACHER')")
    fun searchStudents(
        @Parameter(description = "선택. 이름 또는 학번 일부 (부분 일치)", example = "홍길")
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<List<StudentActivityResponse>> =
        ResponseEntity.ok(activityService.searchStudents(keyword))

    @Operation(
        summary = "학생 월별 활동 기록 (교사)",
        description = "**[교사]** 특정 학생의 월별 기록입니다. 응답 형식과 규칙은 `GET /service-times/me` 와 같습니다.\n" +
            MONTHLY_DESCRIPTION_RULES +
            "- 없는 사용자이거나 학생이 아니면 `404 STUDENT_NOT_FOUND`.\n",
    )
    @GetMapping("/students/{studentId}/activities")
    @ApiErrors(ErrorCode.STUDENT_NOT_FOUND, ErrorCode.VALIDATION_ERROR)
    @PreAuthorize("hasRole('TEACHER')")
    fun getStudentActivity(
        @Parameter(description = "학생의 사용자 ID", example = "12")
        @PathVariable studentId: Long,
        @Parameter(description = "선택. 조회 연도 (생략 시 올해)", example = "2026")
        @RequestParam(required = false) year: Int?,
        @Parameter(description = "선택. 조회 월 1~12 (생략 시 이번 달)", example = "10")
        @RequestParam(required = false) month: Int?,
    ): ResponseEntity<MyActivityResponse> =
        ResponseEntity.ok(activityService.getStudentMonthlyActivity(studentId, year, month))
}
