package team.siru.ecoguard.recruitment

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
import team.siru.ecoguard.common.security.SecurityUtils
import team.siru.ecoguard.recruitment.dto.ApplicantResponse
import team.siru.ecoguard.recruitment.dto.ApplicationStatusResponse
import team.siru.ecoguard.recruitment.dto.ApplyRequest
import team.siru.ecoguard.recruitment.dto.ApplyResponse
import team.siru.ecoguard.recruitment.dto.CreateRecruitmentRequest
import team.siru.ecoguard.recruitment.dto.CreateRecruitmentResponse
import team.siru.ecoguard.recruitment.dto.CurrentRecruitmentResponse
import team.siru.ecoguard.recruitment.dto.UpdateRecruitmentRequest

@RestController
@RequestMapping("/api/v1")
class RecruitmentController(
    private val recruitmentService: RecruitmentService,
) {

    @PostMapping("/recruitments")
    @PreAuthorize("hasRole('TEACHER')")
    fun createRecruitment(@Valid @RequestBody request: CreateRecruitmentRequest): ResponseEntity<CreateRecruitmentResponse> {
        val response = recruitmentService.createRecruitment(SecurityUtils.currentUserId(), request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping("/recruitments/{recruitmentId}")
    @PreAuthorize("hasRole('TEACHER')")
    fun updateRecruitment(
        @PathVariable recruitmentId: Long,
        @RequestBody request: UpdateRecruitmentRequest,
    ): ResponseEntity<Void> {
        recruitmentService.updateRecruitment(recruitmentId, request)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/recruitments/current")
    fun getCurrentRecruitment(): ResponseEntity<CurrentRecruitmentResponse> {
        return ResponseEntity.ok(recruitmentService.getCurrentRecruitment())
    }

    @GetMapping("/recruitments/{recruitmentId}/applications")
    @PreAuthorize("hasRole('TEACHER')")
    fun getApplicants(@PathVariable recruitmentId: Long): ResponseEntity<List<ApplicantResponse>> {
        return ResponseEntity.ok(recruitmentService.getApplicants(recruitmentId))
    }

    @PostMapping("/recruitments/{recruitmentId}/applications")
    @PreAuthorize("hasRole('STUDENT')")
    fun apply(
        @PathVariable recruitmentId: Long,
        @Valid @RequestBody request: ApplyRequest,
    ): ResponseEntity<ApplyResponse> {
        val response = recruitmentService.apply(SecurityUtils.currentUserId(), recruitmentId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/applications/me")
    @PreAuthorize("hasRole('STUDENT')")
    fun getMyApplicationStatus(): ResponseEntity<ApplicationStatusResponse> {
        return ResponseEntity.ok(recruitmentService.getMyApplicationStatus(SecurityUtils.currentUserId()))
    }
}
