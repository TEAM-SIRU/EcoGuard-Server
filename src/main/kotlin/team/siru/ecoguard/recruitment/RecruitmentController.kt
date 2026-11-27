package team.siru.ecoguard.recruitment

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.siru.ecoguard.common.exception.ApiErrors
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.SecurityUtils
import team.siru.ecoguard.recruitment.dto.ApplicantResponse
import team.siru.ecoguard.recruitment.dto.ApplicationStatusResponse
import team.siru.ecoguard.recruitment.dto.ApplyRequest
import team.siru.ecoguard.recruitment.dto.ApplyResponse
import team.siru.ecoguard.recruitment.dto.CreateRecruitmentRequest
import team.siru.ecoguard.recruitment.dto.CreateRecruitmentResponse
import team.siru.ecoguard.recruitment.dto.CurrentRecruitmentResponse
import team.siru.ecoguard.recruitment.dto.RecruitmentSummaryResponse
import team.siru.ecoguard.recruitment.dto.UpdateRecruitmentRequest

@Tag(
    name = "6. 청소 모집 (Recruitment)",
    description = "교사가 학년·반별 청소 인원을 모집하고 학생이 선착순으로 신청한다(반마다 최대 6명). " +
        "신청은 즉시 확정(`APPROVED`)되며, 이후 교사가 청소 구역에 배정한다.",
)
@RestController
@RequestMapping("/api/v1")
class RecruitmentController(
    private val recruitmentService: RecruitmentService,
) {

    @Operation(
        summary = "모집 공고 생성",
        description = """
**[교사]** 학년·반의 청소 모집을 엽니다. 해당 반 학생들에게 `RECRUITMENT_CREATED` 알림이 갑니다.

- `startDate` < `endDate` 가 아니면 `400 INVALID_PERIOD`.
- `maxCount` 가 1~6 이 아니면 `400 INVALID_MAX_COUNT`.
- `activityStartTime` < `activityEndTime` 이 아니면 `400 INVALID_ACTIVITY_TIME`. 둘 다 생략하면 07:20~08:10.
""",
    )
    @PostMapping("/recruitments")
    @ApiErrors(ErrorCode.INVALID_PERIOD, ErrorCode.INVALID_MAX_COUNT, ErrorCode.INVALID_ACTIVITY_TIME, ErrorCode.USER_NOT_FOUND, success = 201)
    @PreAuthorize("hasRole('TEACHER')")
    fun createRecruitment(@Valid @RequestBody request: CreateRecruitmentRequest): ResponseEntity<CreateRecruitmentResponse> {
        val response = recruitmentService.createRecruitment(SecurityUtils.currentUserId(), request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(
        summary = "모집 공고 수정",
        description = """
**[교사]** 보낸 필드만 바꿉니다(생략하거나 `null` 이면 그대로). 응답 바디는 없습니다(`200`).

- 이미 마감된(`endDate` 가 지난) 모집은 `409 RECRUITMENT_CLOSED`.
- `maxCount` 를 현재 신청 인원보다 적게 하면 `409 MAX_COUNT_BELOW_APPLICANTS`.
- 기간·인원·활동 시간 검사는 생성과 같습니다(바뀌지 않은 값과 합쳐서 검사).
""",
    )
    @PatchMapping("/recruitments/{recruitmentId}")
    @ApiErrors(ErrorCode.RECRUITMENT_NOT_FOUND, ErrorCode.RECRUITMENT_CLOSED, ErrorCode.INVALID_PERIOD, ErrorCode.INVALID_MAX_COUNT, ErrorCode.INVALID_ACTIVITY_TIME, ErrorCode.MAX_COUNT_BELOW_APPLICANTS)
    @PreAuthorize("hasRole('TEACHER')")
    fun updateRecruitment(
        @Parameter(description = "모집 공고 ID", example = "7")
        @PathVariable recruitmentId: Long,
        @RequestBody request: UpdateRecruitmentRequest,
    ): ResponseEntity<Void> {
        recruitmentService.updateRecruitment(recruitmentId, request)
        return ResponseEntity.ok().build()
    }

    @Operation(
        summary = "전체 모집 현황",
        description = """
**[교사]** 모든 모집 공고를 시작일 최신순 → 학년 → 반 순으로 줍니다. 없으면 `[]` 입니다.
""",
    )
    @GetMapping("/recruitments")
    @PreAuthorize("hasRole('TEACHER')")
    fun getRecruitments(): ResponseEntity<List<RecruitmentSummaryResponse>> {
        return ResponseEntity.ok(recruitmentService.getRecruitments())
    }

    @Operation(
        summary = "모집 공고 삭제",
        description = """
**[교사]** 잘못 만든 모집을 지웁니다. 응답 바디는 없습니다(`204`).
신청자가 한 명이라도 있으면 `409 RECRUITMENT_HAS_APPLICANTS` 입니다.
""",
    )
    @DeleteMapping("/recruitments/{recruitmentId}")
    @ApiErrors(ErrorCode.RECRUITMENT_NOT_FOUND, ErrorCode.RECRUITMENT_HAS_APPLICANTS, success = 204)
    @PreAuthorize("hasRole('TEACHER')")
    fun deleteRecruitment(
        @Parameter(description = "모집 공고 ID", example = "7")
        @PathVariable recruitmentId: Long,
    ): ResponseEntity<Void> {
        recruitmentService.deleteRecruitment(recruitmentId)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "내 반 모집 공고",
        description = """
**[학생]** 내 학년·반의 모집 공고 하나를 줍니다. **진행 중(`OPEN`)인 모집을 우선** 하고, 없으면 가장 최근 모집(예정 또는 마감)을 줍니다.

- 신청 버튼 활성화: `periodStatus == OPEN && !isFull && !alreadyApplied`.
- 내 반의 모집이 한 번도 없었거나, 내 계정에 학년/반 정보가 없으면 `404 NO_ACTIVE_RECRUITMENT` 입니다.
""",
    )
    @GetMapping("/recruitments/current")
    @ApiErrors(ErrorCode.NO_ACTIVE_RECRUITMENT, ErrorCode.USER_NOT_FOUND)
    @PreAuthorize("hasRole('STUDENT')")
    fun getCurrentRecruitment(): ResponseEntity<CurrentRecruitmentResponse> {
        return ResponseEntity.ok(recruitmentService.getCurrentRecruitment(SecurityUtils.currentUserId()))
    }

    @Operation(
        summary = "모집 신청자 목록",
        description = """
**[교사]** 신청 순서(선착순)대로 신청자를 줍니다. 없으면 `[]` 입니다. 구역 배정 화면에서 배정할 학생을 고를 때 씁니다.
""",
    )
    @GetMapping("/recruitments/{recruitmentId}/applications")
    @ApiErrors(ErrorCode.RECRUITMENT_NOT_FOUND)
    @PreAuthorize("hasRole('TEACHER')")
    fun getApplicants(
        @Parameter(description = "모집 공고 ID", example = "7")
        @PathVariable recruitmentId: Long,
    ): ResponseEntity<List<ApplicantResponse>> {
        return ResponseEntity.ok(recruitmentService.getApplicants(recruitmentId))
    }

    @Operation(
        summary = "모집 신청",
        description = """
**[학생]** 선착순으로 즉시 확정됩니다(`status=APPROVED`). `order` 가 몇 번째 신청인지입니다.

**검사 순서와 에러**

| 조건 | 응답 |
|---|---|
| `motivation` 이 비어 있음 | `400 VALIDATION_ERROR` |
| 모집 공고 없음 | `404 RECRUITMENT_NOT_FOUND` |
| 내 학년/반의 모집이 아님 | `403 CLASS_MISMATCH` |
| 모집 기간이 아님(시작 전/마감 후) | `400 OUT_OF_PERIOD` |
| 이미 신청함 | `409 ALREADY_APPLIED` |
| 정원 마감 | `409 RECRUITMENT_FULL` |
""",
    )
    @PostMapping("/recruitments/{recruitmentId}/applications")
    @ApiErrors(ErrorCode.RECRUITMENT_NOT_FOUND, ErrorCode.CLASS_MISMATCH, ErrorCode.OUT_OF_PERIOD, ErrorCode.ALREADY_APPLIED, ErrorCode.RECRUITMENT_FULL, ErrorCode.USER_NOT_FOUND, success = 201)
    @PreAuthorize("hasRole('STUDENT')")
    fun apply(
        @Parameter(description = "신청할 모집 공고 ID (`GET /recruitments/current` 의 `recruitmentId`)", example = "7")
        @PathVariable recruitmentId: Long,
        @Valid @RequestBody request: ApplyRequest,
    ): ResponseEntity<ApplyResponse> {
        val response = recruitmentService.apply(SecurityUtils.currentUserId(), recruitmentId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(
        summary = "내 신청 상태",
        description = """
**[학생]** 가장 최근에 한 모집 신청의 상태입니다. 신청한 적이 없으면 `404 NO_APPLICATION` 입니다.

- `waitingForAssignment=true`: 신청은 확정됐지만 아직 청소 구역이 배정되지 않음 → "배정 대기 중" 표시.
- 배정되면 `false` 가 되고, 배정 구역은 `GET /assignments/me` 로 확인합니다.
""",
    )
    @GetMapping("/applications/me")
    @ApiErrors(ErrorCode.NO_APPLICATION)
    @PreAuthorize("hasRole('STUDENT')")
    fun getMyApplicationStatus(): ResponseEntity<ApplicationStatusResponse> {
        return ResponseEntity.ok(recruitmentService.getMyApplicationStatus(SecurityUtils.currentUserId()))
    }
}
