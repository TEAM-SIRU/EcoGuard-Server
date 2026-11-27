package team.siru.ecoguard.notice

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
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
import team.siru.ecoguard.notice.dto.CreateNoticeRequest
import team.siru.ecoguard.notice.dto.CreateNoticeResponse
import team.siru.ecoguard.notice.dto.NoticeDetailResponse
import team.siru.ecoguard.notice.dto.NoticeListItemResponse
import team.siru.ecoguard.notice.dto.UpdateNoticeRequest

@Tag(name = "9. 공지 (Notice)", description = "교사가 쓰고 모든 사용자가 읽는 공지. 사용자별 읽음 여부를 기록한다.")
@RestController
@RequestMapping("/api/v1/notices")
class NoticeController(
    private val noticeService: NoticeService,
) {

    @Operation(
        summary = "공지 작성",
        description = """
**[교사]** 공지를 올립니다. 모든 학생에게 `NOTICE_CREATED` 알림이 갑니다.
`title`, `content` 둘 다 필수이며 하나라도 비어 있으면 `400 TITLE_OR_CONTENT_EMPTY` 입니다.
""",
    )
    @PostMapping
    @ApiErrors(ErrorCode.TITLE_OR_CONTENT_EMPTY, ErrorCode.USER_NOT_FOUND, success = 201)
    @PreAuthorize("hasRole('TEACHER')")
    fun create(@RequestBody request: CreateNoticeRequest): ResponseEntity<CreateNoticeResponse> {
        val response = noticeService.create(SecurityUtils.currentUserId(), request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(
        summary = "공지 수정",
        description = """
**[교사]** 보낸 필드만 바꿉니다(생략·`null` 이면 그대로). 빈 문자열이나 공백만 보내면 `400 TITLE_OR_CONTENT_EMPTY`. 응답 바디는 없습니다(`200`).
""",
    )
    @PatchMapping("/{noticeId}")
    @ApiErrors(ErrorCode.TITLE_OR_CONTENT_EMPTY, ErrorCode.NOTICE_NOT_FOUND)
    @PreAuthorize("hasRole('TEACHER')")
    fun update(
        @Parameter(description = "공지 ID", example = "4")
        @PathVariable noticeId: Long,
        @RequestBody request: UpdateNoticeRequest,
    ): ResponseEntity<Void> {
        noticeService.update(noticeId, request)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "공지 삭제", description = "**[교사]** 공지와 읽음 기록을 지웁니다. 응답 바디는 없습니다(`204`).")
    @DeleteMapping("/{noticeId}")
    @ApiErrors(ErrorCode.NOTICE_NOT_FOUND, success = 204)
    @PreAuthorize("hasRole('TEACHER')")
    fun delete(
        @Parameter(description = "공지 ID", example = "4")
        @PathVariable noticeId: Long,
    ): ResponseEntity<Void> {
        noticeService.delete(noticeId)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "공지 목록",
        description = """
**[공통]** 전체 공지를 최신순으로 줍니다(페이지 없음). 없으면 `[]` 입니다.
`isRead` 는 요청한 사용자가 상세를 열어 본 적이 있는지입니다.
""",
    )
    @GetMapping
    fun getList(): ResponseEntity<List<NoticeListItemResponse>> =
        ResponseEntity.ok(noticeService.getList(SecurityUtils.currentUserId()))

    @Operation(
        summary = "공지 상세",
        description = """
**[공통]** 공지 본문입니다. **호출하면 요청한 사용자에게 읽음으로 기록** 됩니다(목록의 `isRead` 가 true 로 바뀜).
`previousNoticeId`(더 오래된 공지), `nextNoticeId`(더 최근 공지)로 이전/다음 이동을 만들 수 있고, 없으면 `null` 입니다.
""",
    )
    @GetMapping("/{noticeId}")
    @ApiErrors(ErrorCode.NOTICE_NOT_FOUND)
    fun getDetail(
        @Parameter(description = "공지 ID", example = "4")
        @PathVariable noticeId: Long,
    ): ResponseEntity<NoticeDetailResponse> =
        ResponseEntity.ok(noticeService.getDetail(SecurityUtils.currentUserId(), noticeId))
}
