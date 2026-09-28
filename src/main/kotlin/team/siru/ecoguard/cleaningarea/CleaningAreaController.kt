package team.siru.ecoguard.cleaningarea

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
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
import team.siru.ecoguard.common.security.SecurityUtils

@RestController
@RequestMapping("/api/v1")
class CleaningAreaController(
    private val cleaningAreaService: CleaningAreaService,
) {

    @GetMapping("/cleaning-areas")
    fun getAreas(): ResponseEntity<List<AreaMapResponse>> =
        ResponseEntity.ok(cleaningAreaService.getAreas())

    @PostMapping("/cleaning-areas/{areaId}/assignments")
    @PreAuthorize("hasRole('TEACHER')")
    fun assignStudents(
        @PathVariable areaId: Long,
        @Valid @RequestBody request: AssignStudentsRequest,
    ): ResponseEntity<List<AssignmentResponse>> {
        val response = cleaningAreaService.assignStudents(areaId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/assignments/me")
    @PreAuthorize("hasRole('STUDENT')")
    fun getMyAssignment(): ResponseEntity<MyAssignmentResponse> =
        ResponseEntity.ok(cleaningAreaService.getMyAssignment(SecurityUtils.currentUserId()))

    @PatchMapping("/cleaning-areas/{areaId}")
    @PreAuthorize("hasRole('TEACHER')")
    fun updateArea(@PathVariable areaId: Long, @Valid @RequestBody request: UpdateAreaRequest): ResponseEntity<Void> {
        cleaningAreaService.updateArea(areaId, request)
        return ResponseEntity.ok().build()
    }
}
