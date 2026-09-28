package team.siru.ecoguard.activity

import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.siru.ecoguard.activity.dto.MyActivityResponse
import team.siru.ecoguard.activity.dto.StudentActivityResponse
import team.siru.ecoguard.common.security.SecurityUtils

@RestController
@RequestMapping("/api/v1")
class ActivityController(
    private val activityService: ActivityService,
) {

    @GetMapping("/service-times/me")
    @PreAuthorize("hasRole('STUDENT')")
    fun getMyActivity(): ResponseEntity<MyActivityResponse> =
        ResponseEntity.ok(activityService.getMyActivity(SecurityUtils.currentUserId()))

    @GetMapping("/students/activities")
    @PreAuthorize("hasRole('TEACHER')")
    fun searchStudents(@RequestParam keyword: String): ResponseEntity<List<StudentActivityResponse>> =
        ResponseEntity.ok(activityService.searchStudents(keyword))
}
