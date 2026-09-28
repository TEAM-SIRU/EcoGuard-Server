package team.siru.ecoguard.appeal

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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.siru.ecoguard.appeal.dto.AppealDecisionRequest
import team.siru.ecoguard.appeal.dto.AppealResponse
import team.siru.ecoguard.appeal.dto.CreateAppealRequest
import team.siru.ecoguard.appeal.dto.CreateAppealResponse
import team.siru.ecoguard.common.security.SecurityUtils

@RestController
@RequestMapping("/api/v1")
class AppealController(
    private val appealService: AppealService,
) {

    @PostMapping("/verifications/{verificationId}/appeals")
    @PreAuthorize("hasRole('STUDENT')")
    fun create(
        @PathVariable verificationId: Long,
        @Valid @RequestBody request: CreateAppealRequest,
    ): ResponseEntity<CreateAppealResponse> {
        val response = appealService.create(SecurityUtils.currentUserId(), verificationId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping("/appeals/{appealId}")
    @PreAuthorize("hasRole('TEACHER')")
    fun decide(@PathVariable appealId: Long, @Valid @RequestBody request: AppealDecisionRequest): ResponseEntity<Void> {
        appealService.decide(appealId, request.decision)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/appeals")
    @PreAuthorize("hasRole('TEACHER')")
    fun getAppeals(@RequestParam(required = false) status: AppealStatus?): ResponseEntity<List<AppealResponse>> =
        ResponseEntity.ok(appealService.getAppeals(status))
}
