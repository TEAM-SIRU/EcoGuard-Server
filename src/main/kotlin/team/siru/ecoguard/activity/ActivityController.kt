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
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.SecurityUtils
import java.time.Clock
import java.time.DateTimeException
import java.time.YearMonth

@RestController
@RequestMapping("/api/v1")
class ActivityController(
    private val activityService: ActivityService,
    private val clock: Clock,
) {

    @GetMapping("/service-times/me")
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

    @GetMapping("/students/activities")
    @PreAuthorize("hasRole('TEACHER')")
    fun searchStudents(@RequestParam(required = false) keyword: String?): ResponseEntity<List<StudentActivityResponse>> =
        ResponseEntity.ok(activityService.searchStudents(keyword))

    @GetMapping("/students/{studentId}/activities")
    @PreAuthorize("hasRole('TEACHER')")
    fun getStudentActivity(
        @PathVariable studentId: Long,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
    ): ResponseEntity<MyActivityResponse> =
        ResponseEntity.ok(activityService.getStudentMonthlyActivity(studentId, toYearMonth(year, month)))

    private fun toYearMonth(year: Int?, month: Int?): YearMonth {
        val now = YearMonth.now(clock)
        return try {
            YearMonth.of(year ?: now.year, month ?: now.monthValue)
        } catch (e: DateTimeException) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }
    }
}
