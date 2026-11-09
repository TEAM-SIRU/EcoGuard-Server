package team.siru.ecoguard.activity

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
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.SecurityUtils
import java.time.Clock
import java.time.DateTimeException
import java.time.YearMonth
import team.siru.ecoguard.common.exception.ApiErrors

@RestController
@RequestMapping("/api/v1")
class ActivityController(
    private val activityService: ActivityService,
    private val clock: Clock,
) {

    @GetMapping("/service-times/me")
    @ApiErrors(ErrorCode.VALIDATION_ERROR)
    // REVIEW: PreAuthorize 어노테이션보단 Filter를 이용하여 Spirng Security Context에 사용자 정보를 넣고 그곳에서 관리하는 것이 더 좋을 것 같습니다
    // 현재는 인증/인가 로직이 컨트롤러에도 일부분 책임이 있어 관리가 어렵고 권한 설정이 하나의 지점에 모이지 않고 있습니다.
    // GSMC v3 서버 권한 설정 클래스(https://github.com/team-incube/GSMC-server-V3/blob/main/src/main/kotlin/com/team/incube/gsmc/v3/global/security/config/DomainAuthorizationConfig.kt)를 참고해보세요
    @PreAuthorize("hasRole('STUDENT')")
    fun getMyActivity(
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
    ): ResponseEntity<MyActivityResponse> =
        ResponseEntity.ok(activityService.getMonthlyActivity(SecurityUtils.currentUserId(), toYearMonth(year, month)))

    @GetMapping("/service-times/me/weekly")
    @PreAuthorize("hasRole('STUDENT')")
    fun getMyWeeklyActivity(): ResponseEntity<WeeklyActivityResponse> =
        ResponseEntity.ok(activityService.getWeeklyActivity(SecurityUtils.currentUserId()))

    @GetMapping("/service-times/weekly")
    @PreAuthorize("hasRole('TEACHER')")
    fun getWeeklyParticipation(): ResponseEntity<WeeklyParticipationResponse> =
        ResponseEntity.ok(activityService.getWeeklyParticipation())

    @GetMapping("/students/activities")
    @ApiErrors(ErrorCode.NO_SEARCH_RESULT)
    @PreAuthorize("hasRole('TEACHER')")
    fun searchStudents(@RequestParam(required = false) keyword: String?): ResponseEntity<List<StudentActivityResponse>> =
        ResponseEntity.ok(activityService.searchStudents(keyword))

    @GetMapping("/students/{studentId}/activities")
    @ApiErrors(ErrorCode.STUDENT_NOT_FOUND, ErrorCode.VALIDATION_ERROR)
    @PreAuthorize("hasRole('TEACHER')")
    fun getStudentActivity(
        @PathVariable studentId: Long,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
    ): ResponseEntity<MyActivityResponse> =
        ResponseEntity.ok(activityService.getStudentMonthlyActivity(studentId, toYearMonth(year, month)))

    // REVIEW: 이러한 로직은 서비스 계층으로 이동시키는게 좋아보여요, 컨트롤러 계층이 이러한 책임을 가지는 것은 SRP 위반이 되며 비즈니스 로직을 일부분 가지고 있는 것이에요
    private fun toYearMonth(year: Int?, month: Int?): YearMonth {
        val now = YearMonth.now(clock)
        return try {
            YearMonth.of(year ?: now.year, month ?: now.monthValue)
        } catch (e: DateTimeException) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }
    }
}
