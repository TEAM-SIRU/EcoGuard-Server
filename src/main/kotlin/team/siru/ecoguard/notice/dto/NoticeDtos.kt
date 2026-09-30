package team.siru.ecoguard.notice.dto

import team.siru.ecoguard.notice.Notice
import java.time.LocalDateTime

data class CreateNoticeRequest(
    val title: String? = null,
    val content: String? = null,
)

data class CreateNoticeResponse(
    val noticeId: Long,
)

data class UpdateNoticeRequest(
    val title: String? = null,
    val content: String? = null,
)

data class NoticeListItemResponse(
    val noticeId: Long,
    val title: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(notice: Notice) = NoticeListItemResponse(notice.id, notice.title, notice.createdAt)
    }
}

data class NoticeDetailResponse(
    val noticeId: Long,
    val title: String,
    val content: String,
    val createdAt: LocalDateTime,
    /** 이전(더 오래된) 공지 */
    val previousNoticeId: Long?,
    /** 다음(더 최근) 공지 */
    val nextNoticeId: Long?,
) {
    companion object {
        fun from(notice: Notice, previousNoticeId: Long?, nextNoticeId: Long?) = NoticeDetailResponse(
            noticeId = notice.id,
            title = notice.title,
            content = notice.content,
            createdAt = notice.createdAt,
            previousNoticeId = previousNoticeId,
            nextNoticeId = nextNoticeId,
        )
    }
}
