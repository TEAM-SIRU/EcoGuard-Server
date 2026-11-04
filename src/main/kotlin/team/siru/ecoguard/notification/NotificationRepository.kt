package team.siru.ecoguard.notification

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

interface NotificationRepository : JpaRepository<Notification, Long> {
    fun findByUserIdOrderByIdDesc(userId: Long, pageable: Pageable): List<Notification>

    fun findByIdAndUserId(id: Long, userId: Long): Notification?

    fun countByUserIdAndReadAtIsNull(userId: Long): Long

    @Transactional
    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    fun markAllRead(@Param("userId") userId: Long, @Param("now") now: LocalDateTime): Int
}
