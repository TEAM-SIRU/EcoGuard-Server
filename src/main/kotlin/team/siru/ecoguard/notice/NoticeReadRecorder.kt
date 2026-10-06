package team.siru.ecoguard.notice

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 읽음 기록은 별도 트랜잭션으로 저장한다. 같은 공지를 동시에 두 번 열어 유니크 제약에 걸려도
 * 공지 조회 자체는 실패하지 않아야 하기 때문이다.
 */
@Component
class NoticeReadRecorder(
    private val noticeReadRepository: NoticeReadRepository,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markRead(noticeId: Long, userId: Long) {
        if (noticeReadRepository.existsByNoticeIdAndUserId(noticeId, userId)) return
        try {
            noticeReadRepository.saveAndFlush(NoticeRead(noticeId = noticeId, userId = userId))
        } catch (e: DataIntegrityViolationException) {
            // 다른 요청이 먼저 기록했다. 읽음 상태는 이미 같으므로 무시한다.
        }
    }
}
