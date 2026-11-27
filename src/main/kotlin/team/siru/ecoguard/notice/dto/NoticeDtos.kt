package team.siru.ecoguard.notice.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.siru.ecoguard.notice.Notice
import java.time.LocalDateTime

@Schema(description = "공지 작성 요청. 두 필드 모두 필수(비어 있으면 TITLE_OR_CONTENT_EMPTY)")
data class CreateNoticeRequest(
    @field:Schema(description = "제목 (필수)", example = "다음 주 청소 시간 변경 안내", requiredMode = Schema.RequiredMode.REQUIRED)
    val title: String? = null,
    @field:Schema(description = "본문 (필수, 줄바꿈 포함 가능)", example = "다음 주부터 청소 인증 시간이 07:30~08:10 으로 바뀝니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    val content: String? = null,
)

@Schema(description = "생성된 공지")
data class CreateNoticeResponse(
    @field:Schema(description = "공지 ID", example = "4")
    val noticeId: Long,
)

@Schema(description = "공지 수정 요청. 보낸 필드만 바뀐다(생략·null 은 그대로, 빈 문자열은 에러)")
data class UpdateNoticeRequest(
    @field:Schema(description = "새 제목", example = "다음 주 청소 시간 변경 안내 (수정)", nullable = true)
    val title: String? = null,
    @field:Schema(description = "새 본문", example = "다음 주부터 청소 인증 시간이 07:30~08:10 으로 바뀝니다.", nullable = true)
    val content: String? = null,
)

@Schema(description = "공지 목록 항목")
data class NoticeListItemResponse(
    @field:Schema(description = "공지 ID", example = "4")
    val noticeId: Long,
    @field:Schema(description = "제목", example = "다음 주 청소 시간 변경 안내")
    val title: String,
    /** 본문 앞부분 (공백 정리 후 최대 100자) */
    @field:Schema(description = "본문 미리보기 (줄바꿈·연속 공백을 한 칸으로 바꾼 뒤 최대 100자)", example = "다음 주부터 청소 인증 시간이 07:30~08:10 으로 바뀝니다.")
    val preview: String,
    /** 요청한 사용자가 상세를 이미 열어 봤는지 */
    @field:Schema(description = "요청한 사용자가 상세를 이미 열어 봤는지", example = "false")
    val isRead: Boolean,
    @field:Schema(description = "작성 시각 (KST)")
    val createdAt: LocalDateTime,
) {
    companion object {
        private const val PREVIEW_LENGTH = 100

        fun from(notice: Notice, isRead: Boolean) = NoticeListItemResponse(
            noticeId = notice.id,
            title = notice.title,
            preview = notice.content.replace(Regex("\\s+"), " ").trim().take(PREVIEW_LENGTH),
            isRead = isRead,
            createdAt = notice.createdAt,
        )
    }
}

@Schema(description = "공지 상세")
data class NoticeDetailResponse(
    @field:Schema(description = "공지 ID", example = "4")
    val noticeId: Long,
    @field:Schema(description = "제목", example = "다음 주 청소 시간 변경 안내")
    val title: String,
    @field:Schema(description = "본문 원문 (줄바꿈 유지)", example = "다음 주부터 청소 인증 시간이 07:30~08:10 으로 바뀝니다.")
    val content: String,
    @field:Schema(description = "작성 시각 (KST)")
    val createdAt: LocalDateTime,
    /** 이전(더 오래된) 공지 */
    @field:Schema(description = "이전(더 오래된) 공지 ID. 없으면 null", example = "3", nullable = true)
    val previousNoticeId: Long?,
    /** 다음(더 최근) 공지 */
    @field:Schema(description = "다음(더 최근) 공지 ID. 없으면 null", example = "5", nullable = true)
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
