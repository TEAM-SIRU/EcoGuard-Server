package team.siru.ecoguard.verification

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import team.siru.ecoguard.common.security.SecurityUtils
import team.siru.ecoguard.verification.dto.MyVerificationResponse
import team.siru.ecoguard.verification.dto.SubmitVerificationResponse
import team.siru.ecoguard.verification.dto.TodayVerificationResponse

@RestController
@RequestMapping("/api/v1/verifications")
class VerificationController(
    private val verificationService: VerificationService,
) {

    @PostMapping(consumes = ["multipart/form-data"])
    @PreAuthorize("hasRole('STUDENT')")
    fun submit(
        @RequestPart("photo") photo: MultipartFile,
        @RequestParam(required = false) areaId: Long?,
    ): ResponseEntity<SubmitVerificationResponse> {
        val response = verificationService.submit(SecurityUtils.currentUserId(), areaId, photo)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/today")
    @PreAuthorize("hasRole('STUDENT')")
    fun getToday(): ResponseEntity<TodayVerificationResponse> =
        ResponseEntity.ok(verificationService.getToday(SecurityUtils.currentUserId()))

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    fun getMyVerifications(): ResponseEntity<List<MyVerificationResponse>> =
        ResponseEntity.ok(verificationService.getMyVerifications(SecurityUtils.currentUserId()))
}
