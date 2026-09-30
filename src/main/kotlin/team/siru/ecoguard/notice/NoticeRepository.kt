package team.siru.ecoguard.notice

import org.springframework.data.jpa.repository.JpaRepository

interface NoticeRepository : JpaRepository<Notice, Long> {
    fun findAllByOrderByCreatedAtDesc(): List<Notice>
    fun findFirstByIdLessThanOrderByIdDesc(id: Long): Notice?
    fun findFirstByIdGreaterThanOrderByIdAsc(id: Long): Notice?
}
