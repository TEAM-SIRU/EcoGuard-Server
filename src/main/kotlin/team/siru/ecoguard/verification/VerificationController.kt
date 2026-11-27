package team.siru.ecoguard.verification

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
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
import team.siru.ecoguard.common.exception.ApiErrors
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.SecurityUtils
import team.siru.ecoguard.verification.dto.MyVerificationResponse
import team.siru.ecoguard.verification.dto.SubmitVerificationResponse
import team.siru.ecoguard.verification.dto.TodayVerificationResponse
import java.time.OffsetDateTime

private const val MAX_IDEMPOTENCY_KEY_LENGTH = 64

@Tag(
    name = "4. 청소 인증 (Verification)",
    description = "학생이 청소 후 사진을 올리는 인증. 하루 한 번, 평일 인증 가능 시간(기본 07:20~08:10) 안에만 제출할 수 있다. " +
        "제출 직후 상태는 `PROCESSING` 이고 AI 검수 결과에 따라 `APPROVED` 또는 `MANUAL_REVIEW`(교사 확인)로 바뀐다.",
)
@RestController
@RequestMapping("/api/v1/verifications")
class VerificationController(
    private val verificationService: VerificationService,
) {

    @Operation(
        summary = "청소 인증 사진 제출",
        description = """
**[학생]** `multipart/form-data` 로 사진 한 장을 올립니다. 접수되면 `201` 과 함께 `status=PROCESSING` 이 오고, AI 검수는 비동기로 진행됩니다.

**요청 예 (curl)**
```
curl -X POST {서버}/api/v1/verifications \
  -H "Authorization: Bearer {accessToken}" \
  -H "Idempotency-Key: 3f1c9a52-7b1e-4b8e-9d0a-5c2f1e7a9b10" \
  -H "X-Submit-Started-At: 2026-10-07T08:09:58+09:00" \
  -F "photo=@cleaned.jpg;type=image/jpeg"
```
- `photo`: 필수. JPG·PNG 만, 최대 10MB. 파일 이름·Content-Type 이 아니라 실제 이미지 내용으로 판별합니다.
- `areaId`: 선택. 보내면 내 배정 구역과 같아야 합니다(다르면 `403 NOT_ASSIGNED_AREA`). 생략하면 배정 구역으로 처리합니다.

**검사 순서와 에러** (위에서부터 먼저 걸리는 것이 응답됩니다)

| 순서 | 조건 | 응답 |
|---|---|---|
| 0 | 같은 `Idempotency-Key` 로 이미 접수됨 | **`201` + 처음 접수 결과 그대로** (중복 접수 안 됨, 마감 후여도 동일) |
| 1 | 배정된 구역 없음 | `404 NO_ASSIGNMENT` |
| 2 | `areaId` 가 배정 구역과 다름 | `403 NOT_ASSIGNED_AREA` |
| 3 | 주말 | `403 OUT_OF_CERTIFICATION_TIME` |
| 4 | 방학 기간(NEIS 학사일정) | `403 VACATION_PERIOD` |
| 5 | 인증 가능 시간 밖 (아래 마감 유예 제외) | `403 OUT_OF_CERTIFICATION_TIME` |
| 6 | 오늘 이미 제출 | `409 ALREADY_SUBMITTED_TODAY` (+ `submittedAt`) |
| 7 | 읽을 수 없는 이미지 / 10MB 초과 | `400 INVALID_IMAGE` |

**재전송(네트워크 실패) 처리 — 권장**
- 제출 버튼을 누를 때 UUID 하나를 만들어 `Idempotency-Key` 로 보내고, **재시도할 때도 같은 값** 을 보내세요. 응답을 못 받았던 요청이 실제로 접수됐더라도 중복되지 않고 같은 결과가 옵니다.
- 처음 전송을 시작한 시각을 `X-Submit-Started-At` 에 넣어 재시도에도 같은 값으로 보내면, 마감 직전에 시작한 전송이 마감 뒤 재시도돼도 **마감 후 5분까지** 받아 줍니다(`Idempotency-Key` 가 있어야 함).

**제출 후 결과 확인**: `GET /verifications/{verificationId}/review` 를 조회하거나, `VERIFICATION_APPROVED` / `VERIFICATION_MANUAL_REVIEW` / `VERIFICATION_REJECTED` 푸시 알림(`refId`=verificationId)을 받습니다.
""",
    )
    @PostMapping(consumes = ["multipart/form-data"])
    @ApiErrors(ErrorCode.VALIDATION_ERROR, ErrorCode.NO_ASSIGNMENT, ErrorCode.NOT_ASSIGNED_AREA, ErrorCode.OUT_OF_CERTIFICATION_TIME, ErrorCode.VACATION_PERIOD, ErrorCode.ALREADY_SUBMITTED_TODAY, ErrorCode.INVALID_IMAGE, ErrorCode.USER_NOT_FOUND, success = 201)
    @PreAuthorize("hasRole('STUDENT')")
    fun submit(
        @Parameter(
            description = "청소 후 찍은 사진 파일 (JPG/PNG, 최대 10MB). form 필드 이름은 반드시 `photo`",
            schema = Schema(type = "string", format = "binary"),
        )
        @RequestPart("photo") photo: MultipartFile,
        @Parameter(description = "선택. 인증할 구역 ID. 보내면 내 배정 구역(`GET /assignments/me` 의 `areaId`)과 같아야 한다. 쿼리 또는 form 필드로 보낸다", example = "3")
        @RequestParam(required = false) areaId: Long?,
        /** 재전송 시 같은 값을 보내면 중복 접수되지 않고 처음 접수 결과를 돌려준다. 64자 이하. */
        @Parameter(
            description = "선택(권장). 제출 1건마다 만든 고유 값(UUID 권장, 1~64자). 재시도할 때 같은 값을 보내면 중복 접수되지 않고 처음 결과를 돌려준다",
            example = "3f1c9a52-7b1e-4b8e-9d0a-5c2f1e7a9b10",
        )
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        /** 이 전송을 처음 시작한 시각(ISO-8601, 예: 2026-10-05T08:09:58+09:00). 마감 직전 시작한 재시도를 받아 줄 때 쓴다. */
        @Parameter(
            description = "선택. 이 제출을 **처음** 전송 시작한 시각. 시간대 오프셋을 포함한 ISO-8601 (`+09:00` 또는 `Z`). " +
                "재시도에도 같은 값을 보낸다. `Idempotency-Key` 와 함께 보내야 마감 후 5분 유예가 적용된다. 형식이 틀리면 `400 VALIDATION_ERROR`",
            example = "2026-10-07T08:09:58+09:00",
        )
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

    @Operation(
        summary = "오늘 인증 화면 정보",
        description = """
**[학생]** 인증 화면에 들어갈 때 호출합니다. 배정 구역, 인증 가능 시간, **서버 현재 시각**, 오늘 제출 여부를 한 번에 줍니다.

- 기기 시계 대신 `serverTime` 을 기준으로 남은 시간·버튼 활성화를 판단하세요.
- `canSubmit=false` 면 `unavailableReason` 으로 문구를 정합니다. 여러 조건이 겹치면 아래 표의 위쪽이 우선입니다.

| unavailableReason | 의미 | 예시 문구 |
|---|---|---|
| `ALREADY_SUBMITTED` | 오늘 이미 제출함 (`submitted=true`, 아래 `status` 참고) | 오늘 인증을 완료했어요 |
| `WEEKEND` | 주말 | 주말에는 인증하지 않아요 |
| `VACATION` | 방학 기간 | 방학 중에는 인증하지 않아요 |
| `BEFORE_START` | 인증 시작 전 (`startTime` 전) | 07:20 부터 인증할 수 있어요 |
| `AFTER_END` | 인증 시간 종료 (`endTime` 후) | 오늘 인증 시간이 끝났어요 |

- 배정 구역이 없으면 `404 NO_ASSIGNMENT` 입니다.
""",
    )
    @GetMapping("/today")
    @ApiErrors(ErrorCode.NO_ASSIGNMENT)
    @PreAuthorize("hasRole('STUDENT')")
    fun getToday(): ResponseEntity<TodayVerificationResponse> =
        ResponseEntity.ok(verificationService.getToday(SecurityUtils.currentUserId()))

    @Operation(
        summary = "내 인증 기록 전체",
        description = """
**[학생]** 내가 제출한 모든 인증을 날짜 최신순으로 줍니다. 없으면 `[]` 입니다.

- `failReasons` 는 `reviewStatus=REJECTED` 일 때만 값이 있고, 그 외에는 `null` 입니다.
- 반려된 인증에는 `POST /verifications/{verificationId}/appeals` 로 이의신청할 수 있습니다.
- 달력 형태(미제출일 포함)가 필요하면 `GET /service-times/me` 를 쓰세요.
""",
    )
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    fun getMyVerifications(): ResponseEntity<List<MyVerificationResponse>> =
        ResponseEntity.ok(verificationService.getMyVerifications(SecurityUtils.currentUserId()))
}
