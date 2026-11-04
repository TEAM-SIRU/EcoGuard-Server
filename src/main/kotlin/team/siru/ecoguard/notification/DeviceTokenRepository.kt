package team.siru.ecoguard.notification

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

interface DeviceTokenRepository : JpaRepository<DeviceToken, Long> {
    fun findByToken(token: String): DeviceToken?

    fun findByUserIdIn(userIds: Collection<Long>): List<DeviceToken>

    @Transactional
    @Modifying
    @Query("delete from DeviceToken t where t.userId = :userId")
    fun deleteByUserId(@Param("userId") userId: Long)

    @Transactional
    @Modifying
    @Query("delete from DeviceToken t where t.userId = :userId and t.token = :token")
    fun deleteByUserIdAndToken(@Param("userId") userId: Long, @Param("token") token: String)

    @Transactional
    @Modifying
    @Query("delete from DeviceToken t where t.token in :tokens")
    fun deleteByTokenIn(@Param("tokens") tokens: Collection<String>)

    @Transactional
    @Modifying
    @Query("delete from DeviceToken t where t.lastSeenAt < :threshold")
    fun deleteStale(@Param("threshold") threshold: LocalDateTime): Int
}
