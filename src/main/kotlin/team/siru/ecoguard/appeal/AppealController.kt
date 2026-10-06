package team.siru.ecoguard.appeal

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import team.siru.ecoguard.appeal.dto.AppealDecisionRequest
import team.siru.ecoguard.appeal.dto.AppealResponse
import team.siru.ecoguard.appeal.dto.CreateAppealRequest
import team.siru.ecoguard.appeal.dto.CreateAppealResponse
import team.siru.ecoguard.appeal.dto.MyAppealResponse
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.SecurityUtils

@RestController
@RequestMapping("/api/v1")
class AppealController(
    private val appealService: AppealService,
) {

    /** 사진 없이 내용만 보내는 요청 (JSON). */
    @PostMapping("/verifications/{verificationId}/appeals", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('STUDENT')")
    fun create(
        @PathVariable verificationId: Long,
        @Valid @RequestBody request: CreateAppealRequest,
    ): ResponseEntity<CreateAppealResponse> {
        val response = appealService.create(SecurityUtils.currentUserId(), verificationId, request.content)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /** 다시 찍은 사진(`photos`, 최대 3장)과 함께 보내는 요청 (multipart/form-data). */
    @PostMapping("/verifications/{verificationId}/appeals", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasRole('STUDENT')")
    fun createWithPhotos(
        @PathVariable verificationId: Long,
        @RequestParam(required = false) content: String?,
        @RequestPart(name = "photos", required = false) photos: List<MultipartFile>?,
    ): ResponseEntity<CreateAppealResponse> {
        if (content.isNullOrBlank()) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR, "content: 내용을 입력해야 합니다.")
        }
        val response = appealService.create(
            SecurityUtils.currentUserId(),
            verificationId,
            content,
            photos.orEmpty().filterNot { it.isEmpty },
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping("/appeals/{appealId}")
    @PreAuthorize("hasRole('TEACHER')")
    fun decide(@PathVariable appealId: Long, @Valid @RequestBody request: AppealDecisionRequest): ResponseEntity<Void> {
        appealService.decide(appealId, request.decision, request.reply)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/appeals")
    @PreAuthorize("hasRole('TEACHER')")
    fun getAppeals(@RequestParam(required = false) status: AppealStatus?): ResponseEntity<List<AppealResponse>> =
        ResponseEntity.ok(appealService.getAppeals(status))

    @GetMapping("/appeals/me")
    @PreAuthorize("hasRole('STUDENT')")
    fun getMyAppeals(): ResponseEntity<List<MyAppealResponse>> =
        ResponseEntity.ok(appealService.getMyAppeals(SecurityUtils.currentUserId()))
}
