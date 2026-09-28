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
    val title: String,
    val content: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(notice: Notice) = NoticeDetailResponse(notice.title, notice.content, notice.createdAt)
    }
}
