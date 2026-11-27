package team.siru.ecoguard.aireview

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
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
import team.siru.ecoguard.common.exception.ApiErrors
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.SecurityUtils
import team.siru.ecoguard.user.Role

@Tag(
    name = "5. 검수 (Review)",
    description = "제출된 인증의 검수 결과 조회(학생)와 교사 수동 검토. AI 는 확실히 통과한 사진만 자동 승인하고, " +
        "그 외(불통과·오류·대기열 초과·시간 초과)는 모두 `MANUAL_REVIEW` 로 보내 교사가 승인/반려한다.",
)
@RestController
@RequestMapping("/api/v1/verifications")
class AiReviewController(
    private val aiReviewService: AiReviewService,
) {

    @Operation(
        summary = "수동 검토 대기 목록",
        description = """
**[교사]** `MANUAL_REVIEW` 상태인 인증을 제출 순(오래된 것 먼저)으로 줍니다. 없으면 `[]` 입니다.

`reason` 은 수동 검토로 넘어온 이유, `failReasons` 는 AI 가 지적한 문제(참고용)입니다.
처리는 `PATCH /verifications/{verificationId}/review` 로 합니다.
""",
    )
    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    fun getManualReviewQueue(): ResponseEntity<List<ManualReviewItemResponse>> =
        ResponseEntity.ok(aiReviewService.getManualReviewQueue())

    @Operation(
        summary = "수동 검토 승인/반려",
        description = """
**[교사]** 수동 검토 중인 인증을 승인하거나 반려합니다. 응답 바디는 없습니다(`200`).

- `APPROVED`: 인증 승인 + 학생에게 봉사시간 10분 적립 + `VERIFICATION_APPROVED` 알림.
- `REJECTED`: 반려 + `VERIFICATION_REJECTED` 알림. `reason` 을 적으면 AI 사유 대신 그 문장이 학생에게 반려 사유로 보입니다.
  비우면 AI 가 지적한 사유가 그대로 보입니다. 학생은 반려된 인증에 이의신청할 수 있습니다.
- 이미 처리됐거나 수동 검토 상태가 아니면 `409 NOT_MANUAL_REVIEW` 입니다(교사 두 명이 동시에 처리한 경우 포함).
""",
    )
    @PatchMapping("/{verificationId}/review")
    @ApiErrors(ErrorCode.VALIDATION_ERROR, ErrorCode.REVIEW_NOT_FOUND, ErrorCode.NOT_MANUAL_REVIEW)
    @PreAuthorize("hasRole('TEACHER')")
    fun decide(
        @Parameter(description = "처리할 인증 ID", example = "101")
        @PathVariable verificationId: Long,
        @Valid @RequestBody request: ManualReviewDecisionRequest,
    ): ResponseEntity<Void> {
        aiReviewService.decide(verificationId, request.decision, request.reason)
        return ResponseEntity.ok().build()
    }

    @Operation(
        summary = "인증 검수 결과 조회",
        description = """
**[학생·교사]** 인증 한 건의 현재 상태입니다. 학생은 제출 후 결과 화면에서 이 API 를 조회하거나 푸시 알림을 받아 갱신합니다.

- 학생은 **본인 인증만** 조회할 수 있고, 남의 인증 ID 면 `404 REVIEW_NOT_FOUND` 입니다.
- `PROCESSING` 이면 아직 AI 검수 중입니다. 폴링한다면 3~5초 간격을 권장합니다(대기열이 길면 최대 수 분, 그 뒤에는 자동으로 `MANUAL_REVIEW`).
- `failReasons` 는 `REJECTED` 일 때만 값이 있습니다.
""",
    )
    @GetMapping("/{verificationId}/review")
    @ApiErrors(ErrorCode.REVIEW_NOT_FOUND)
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    fun getReviewResult(
        @Parameter(description = "인증 ID (`POST /verifications` 응답의 `verificationId`)", example = "101")
        @PathVariable verificationId: Long,
    ): ResponseEntity<ReviewResultResponse> {
        val requesterId = if (SecurityUtils.hasRole(Role.TEACHER)) null else SecurityUtils.currentUserId()
        return ResponseEntity.ok(aiReviewService.getReviewResult(verificationId, requesterId))
    }
}
