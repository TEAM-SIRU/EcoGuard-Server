package team.siru.ecoguard.notice

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
import team.siru.ecoguard.common.security.SecurityUtils
import team.siru.ecoguard.notice.dto.CreateNoticeRequest
import team.siru.ecoguard.notice.dto.CreateNoticeResponse
import team.siru.ecoguard.notice.dto.NoticeDetailResponse
import team.siru.ecoguard.notice.dto.NoticeListItemResponse
import team.siru.ecoguard.notice.dto.UpdateNoticeRequest
import team.siru.ecoguard.common.exception.ApiErrors
import team.siru.ecoguard.common.exception.ErrorCode

@RestController
@RequestMapping("/api/v1/notices")
class NoticeController(
    private val noticeService: NoticeService,
) {

    @PostMapping
    @ApiErrors(ErrorCode.TITLE_OR_CONTENT_EMPTY, ErrorCode.USER_NOT_FOUND, success = 201)
    @PreAuthorize("hasRole('TEACHER')")
    fun create(@RequestBody request: CreateNoticeRequest): ResponseEntity<CreateNoticeResponse> {
        val response = noticeService.create(SecurityUtils.currentUserId(), request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping("/{noticeId}")
    @ApiErrors(ErrorCode.TITLE_OR_CONTENT_EMPTY, ErrorCode.NOTICE_NOT_FOUND)
    @PreAuthorize("hasRole('TEACHER')")
    fun update(@PathVariable noticeId: Long, @RequestBody request: UpdateNoticeRequest): ResponseEntity<Void> {
        noticeService.update(noticeId, request)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{noticeId}")
    @ApiErrors(ErrorCode.NOTICE_NOT_FOUND, success = 204)
    @PreAuthorize("hasRole('TEACHER')")
    fun delete(@PathVariable noticeId: Long): ResponseEntity<Void> {
        noticeService.delete(noticeId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    fun getList(): ResponseEntity<List<NoticeListItemResponse>> =
        ResponseEntity.ok(noticeService.getList(SecurityUtils.currentUserId()))

    @GetMapping("/{noticeId}")
    @ApiErrors(ErrorCode.NOTICE_NOT_FOUND)
    fun getDetail(@PathVariable noticeId: Long): ResponseEntity<NoticeDetailResponse> =
        ResponseEntity.ok(noticeService.getDetail(SecurityUtils.currentUserId(), noticeId))
}
