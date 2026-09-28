package team.siru.ecoguard.aireview

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.siru.ecoguard.aireview.dto.ManualReviewDecisionRequest
import team.siru.ecoguard.aireview.dto.ManualReviewItemResponse
import team.siru.ecoguard.aireview.dto.ReviewResultResponse

@RestController
@RequestMapping("/api/v1/verifications")
class AiReviewController(
    private val aiReviewService: AiReviewService,
) {

    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    fun getManualReviewQueue(): ResponseEntity<List<ManualReviewItemResponse>> =
        ResponseEntity.ok(aiReviewService.getManualReviewQueue())

    @PatchMapping("/{verificationId}/review")
    @PreAuthorize("hasRole('TEACHER')")
    fun decide(
        @PathVariable verificationId: Long,
        @Valid @RequestBody request: ManualReviewDecisionRequest,
    ): ResponseEntity<Void> {
        aiReviewService.decide(verificationId, request.decision)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/{verificationId}/review")
    @PreAuthorize("hasRole('STUDENT')")
    fun getReviewResult(@PathVariable verificationId: Long): ResponseEntity<ReviewResultResponse> =
        ResponseEntity.ok(aiReviewService.getReviewResult(verificationId))
}
