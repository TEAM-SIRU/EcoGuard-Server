package team.siru.ecoguard.notice

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.notice.dto.CreateNoticeRequest
import team.siru.ecoguard.notice.dto.CreateNoticeResponse
import team.siru.ecoguard.notice.dto.NoticeDetailResponse
import team.siru.ecoguard.notice.dto.NoticeListItemResponse
import team.siru.ecoguard.notice.dto.UpdateNoticeRequest
import team.siru.ecoguard.user.UserRepository

@Service
class NoticeService(
    private val noticeRepository: NoticeRepository,
    private val userRepository: UserRepository,
) {

    @Transactional
    fun create(teacherId: Long, request: CreateNoticeRequest): CreateNoticeResponse {
        if (request.title.isNullOrBlank() || request.content.isNullOrBlank()) {
            throw BusinessException(ErrorCode.TITLE_OR_CONTENT_EMPTY)
        }
        val teacher = userRepository.findById(teacherId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
        val notice = noticeRepository.save(Notice(title = request.title, content = request.content, teacher = teacher))
        return CreateNoticeResponse(notice.id)
    }

    @Transactional
    fun update(noticeId: Long, request: UpdateNoticeRequest) {
        if (request.title?.isBlank() == true || request.content?.isBlank() == true) {
            throw BusinessException(ErrorCode.TITLE_OR_CONTENT_EMPTY)
        }
        val notice = getNoticeOrThrow(noticeId)
        request.title?.let { notice.title = it }
        request.content?.let { notice.content = it }
    }

    @Transactional
    fun delete(noticeId: Long) {
        val notice = getNoticeOrThrow(noticeId)
        noticeRepository.delete(notice)
    }

    fun getList(): List<NoticeListItemResponse> =
        noticeRepository.findAllByOrderByCreatedAtDesc().map(NoticeListItemResponse::from)

    fun getDetail(noticeId: Long): NoticeDetailResponse {
        val notice = getNoticeOrThrow(noticeId)
        return NoticeDetailResponse.from(
            notice,
            previousNoticeId = noticeRepository.findFirstByIdLessThanOrderByIdDesc(noticeId)?.id,
            nextNoticeId = noticeRepository.findFirstByIdGreaterThanOrderByIdAsc(noticeId)?.id,
        )
    }

    private fun getNoticeOrThrow(noticeId: Long): Notice =
        noticeRepository.findById(noticeId).orElseThrow { BusinessException(ErrorCode.NOTICE_NOT_FOUND) }
}
