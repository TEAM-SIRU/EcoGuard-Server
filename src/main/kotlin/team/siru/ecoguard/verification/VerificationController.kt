package team.siru.ecoguard.verification

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.SecurityUtils
import java.time.OffsetDateTime
import team.siru.ecoguard.verification.dto.MyVerificationResponse
import team.siru.ecoguard.verification.dto.SubmitVerificationResponse
import team.siru.ecoguard.verification.dto.TodayVerificationResponse

private const val MAX_IDEMPOTENCY_KEY_LENGTH = 64

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
        /** 재전송 시 같은 값을 보내면 중복 접수되지 않고 처음 접수 결과를 돌려준다. 64자 이하. */
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        /** 이 전송을 처음 시작한 시각(ISO-8601, 예: 2026-10-05T08:09:58+09:00). 마감 직전 시작한 재시도를 받아 줄 때 쓴다. */
        @RequestHeader("X-Submit-Started-At", required = false) startedAt: String?,
    ): ResponseEntity<SubmitVerificationResponse> {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length > MAX_IDEMPOTENCY_KEY_LENGTH)) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key: 1~64자여야 합니다.")
        }
        val startedAtLocal = startedAt?.let {
            runCatching { OffsetDateTime.parse(it) }
                .getOrElse { throw BusinessException(ErrorCode.VALIDATION_ERROR, "X-Submit-Started-At: ISO-8601 형식이어야 합니다.") }
        }
        val response = verificationService.submit(SecurityUtils.currentUserId(), areaId, photo, idempotencyKey, startedAtLocal)
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
