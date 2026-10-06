package team.siru.ecoguard.notice

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import team.siru.ecoguard.common.BaseEntity

/** 사용자가 공지 상세를 연 기록. 공지 목록의 읽음 여부 표시에 쓴다. */
@Entity
@Table(
    name = "notice_reads",
    uniqueConstraints = [UniqueConstraint(columnNames = ["notice_id", "user_id"])],
)
class NoticeRead(
    @Column(name = "notice_id", nullable = false)
    var noticeId: Long,

    @Column(name = "user_id", nullable = false)
    var userId: Long,
) : BaseEntity()
