package team.siru.ecoguard.appeal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
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
import team.siru.ecoguard.common.exception.ApiErrors
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.SecurityUtils

/** 같은 경로를 JSON 과 multipart 두 메서드가 나눠 받으므로, 문서에는 같은 설명을 쓴다. */
private const val CREATE_APPEAL_DESCRIPTION = """
**[학생]** 반려(`REJECTED`)된 **본인** 인증에 이의신청합니다. 교사들에게 `APPEAL_RECEIVED` 알림이 갑니다.
같은 주소로 두 가지 형식을 받습니다. **Content-Type 으로 구분** 합니다.

**① 사진 없이 – `application/json`**
```json
{ "content": "난간까지 닦았는데 사진 각도 때문에 안 보였어요." }
```

**② 사진과 함께 – `multipart/form-data`**
```
curl -X POST {서버}/api/v1/verifications/101/appeals \
  -H "Authorization: Bearer {accessToken}" \
  -F "content=난간까지 닦았는데 사진 각도 때문에 안 보였어요." \
  -F "photos=@retake1.jpg;type=image/jpeg" \
  -F "photos=@retake2.jpg;type=image/jpeg"
```
- `content`: 필수(공백만 있으면 안 됨). multipart 에서는 텍스트 form 필드로 보냅니다.
- `photos`: 선택, **같은 필드 이름 `photos` 를 반복** 해서 최대 3장. JPG·PNG, 장당 10MB. 4장 이상이면 `400 TOO_MANY_APPEAL_PHOTOS`.

**규칙**
- 반려된 본인 인증이 아니면(남의 인증, 아직 검수 중, 승인됨 등) `409 APPEAL_NOT_ALLOWED`.
- 이미 검토 중(`PENDING`)인 이의신청이 있으면 `409 APPEAL_ALREADY_PENDING`. 결과가 나온 뒤에는 횟수 제한 없이 다시 신청할 수 있고, `round` 가 1씩 늘어납니다.
- 승인되면 인증이 `APPROVED` 로 바뀌고 봉사시간 10분이 적립됩니다(`APPEAL_APPROVED` 알림).
"""

@Tag(
    name = "7. 이의신청 (Appeal)",
    description = "교사가 반려한 청소 인증에 학생이 이의를 제기하고, 교사가 승인/반려로 답한다.",
)
@RestController
@RequestMapping("/api/v1")
class AppealController(
    private val appealService: AppealService,
) {

    /** 사진 없이 내용만 보내는 요청 (JSON). */
    @Operation(summary = "이의신청 (JSON: 사진 없이 / multipart: 사진과 함께)", description = CREATE_APPEAL_DESCRIPTION)
    @PostMapping("/verifications/{verificationId}/appeals", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ApiErrors(ErrorCode.TOO_MANY_APPEAL_PHOTOS, ErrorCode.VERIFICATION_NOT_FOUND, ErrorCode.APPEAL_NOT_ALLOWED, ErrorCode.APPEAL_ALREADY_PENDING, ErrorCode.INVALID_IMAGE, ErrorCode.USER_NOT_FOUND, success = 201)
    @PreAuthorize("hasRole('STUDENT')")
    fun create(
        @Parameter(description = "반려된 인증 ID", example = "101")
        @PathVariable verificationId: Long,
        @Valid @RequestBody request: CreateAppealRequest,
    ): ResponseEntity<CreateAppealResponse> {
        val response = appealService.create(SecurityUtils.currentUserId(), verificationId, request.content)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /** 다시 찍은 사진(`photos`, 최대 3장)과 함께 보내는 요청 (multipart/form-data). */
    @Operation(summary = "이의신청 (JSON: 사진 없이 / multipart: 사진과 함께)", description = CREATE_APPEAL_DESCRIPTION)
    @PostMapping("/verifications/{verificationId}/appeals", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ApiErrors(ErrorCode.VALIDATION_ERROR, ErrorCode.TOO_MANY_APPEAL_PHOTOS, ErrorCode.VERIFICATION_NOT_FOUND, ErrorCode.APPEAL_NOT_ALLOWED, ErrorCode.APPEAL_ALREADY_PENDING, ErrorCode.INVALID_IMAGE, ErrorCode.USER_NOT_FOUND, success = 201)
    @PreAuthorize("hasRole('STUDENT')")
    fun createWithPhotos(
        @Parameter(description = "반려된 인증 ID", example = "101")
        @PathVariable verificationId: Long,
        @Parameter(description = "필수. 이의신청 내용. multipart 의 텍스트 form 필드로 보낸다(Swagger 에는 query 로 표시되지만 form 필드·query 둘 다 받는다). 비어 있으면 `400 VALIDATION_ERROR`", example = "난간까지 닦았는데 사진 각도 때문에 안 보였어요.")
        @RequestParam(required = false) content: String?,
        @Parameter(
            description = "선택. 다시 찍은 사진, 같은 필드 이름 `photos` 로 최대 3장 (JPG/PNG, 장당 10MB)",
            array = ArraySchema(schema = Schema(type = "string", format = "binary"), maxItems = 3),
        )
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

    @Operation(
        summary = "이의신청 승인/반려",
        description = """
**[교사]** 검토 중(`PENDING`)인 이의신청에 답합니다. 응답 바디는 없습니다(`200`).

```json
{ "decision": "APPROVED", "replyTitle": "확인했어요", "reply": "다시 찍은 사진에서 깨끗한 게 확인돼 승인합니다." }
```
- `decision` 은 `APPROVED` 또는 `REJECTED` 만 됩니다. 그 외 값이나 누락은 `400 INVALID_DECISION`.
- `APPROVED`: 인증이 `APPROVED` 로 바뀌고 봉사시간 10분이 적립됩니다(이미 승인된 인증이면 적립 없음). 학생에게 `APPEAL_APPROVED` 알림.
- `REJECTED`: `reply` 가 학생에게 반려 사유로 보입니다. 학생에게 `APPEAL_REJECTED` 알림.
- 이미 처리된 이의신청이면 `409 ALREADY_PROCESSED`.
""",
    )
    @PatchMapping("/appeals/{appealId}")
    @ApiErrors(ErrorCode.INVALID_DECISION, ErrorCode.APPEAL_NOT_FOUND, ErrorCode.ALREADY_PROCESSED)
    @PreAuthorize("hasRole('TEACHER')")
    fun decide(
        @Parameter(description = "이의신청 ID", example = "15")
        @PathVariable appealId: Long,
        @Valid @RequestBody request: AppealDecisionRequest,
    ): ResponseEntity<Void> {
        appealService.decide(appealId, request.decision, request.reply, request.replyTitle)
        return ResponseEntity.ok().build()
    }

    @Operation(
        summary = "이의신청 목록 (교사)",
        description = """
**[교사]** 이의신청을 접수 순(오래된 것 먼저)으로 줍니다. `status` 를 주면 그 상태만, 생략하면 전체입니다. 없으면 `[]`.
""",
    )
    @GetMapping("/appeals")
    @PreAuthorize("hasRole('TEACHER')")
    fun getAppeals(
        @Parameter(description = "선택. 이 상태의 이의신청만 조회 (예: 처리할 것만 보려면 PENDING)", example = "PENDING")
        @RequestParam(required = false) status: AppealStatus?,
    ): ResponseEntity<List<AppealResponse>> =
        ResponseEntity.ok(appealService.getAppeals(status))

    @Operation(
        summary = "내 이의신청 목록",
        description = """
**[학생]** 내 이의신청을 최신순으로 줍니다. 없으면 `[]` 입니다.
`reply`, `replyTitle` 은 교사가 답한 뒤에 채워지고, `awardedMinutes` 는 승인으로 실제 적립된 경우에만 있습니다.
""",
    )
    @GetMapping("/appeals/me")
    @PreAuthorize("hasRole('STUDENT')")
    fun getMyAppeals(): ResponseEntity<List<MyAppealResponse>> =
        ResponseEntity.ok(appealService.getMyAppeals(SecurityUtils.currentUserId()))
}
