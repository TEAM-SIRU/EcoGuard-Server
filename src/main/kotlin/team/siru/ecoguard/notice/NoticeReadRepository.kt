package team.siru.ecoguard.notice

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NoticeReadRepository : JpaRepository<NoticeRead, Long> {
    fun existsByNoticeIdAndUserId(noticeId: Long, userId: Long): Boolean

    @Query("select r.noticeId from NoticeRead r where r.userId = :userId")
    fun findNoticeIdsByUserId(@Param("userId") userId: Long): List<Long>

    @Modifying
    @Query("delete from NoticeRead r where r.noticeId = :noticeId")
    fun deleteByNoticeId(@Param("noticeId") noticeId: Long)
}
