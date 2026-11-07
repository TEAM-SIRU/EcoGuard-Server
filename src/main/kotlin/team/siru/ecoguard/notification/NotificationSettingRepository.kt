package team.siru.ecoguard.notification

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NotificationSettingRepository : JpaRepository<NotificationSetting, Long> {
    fun findByUserId(userId: Long): List<NotificationSetting>

    fun findByUserIdAndCategory(userId: Long, category: NotificationCategory): NotificationSetting?

    /** [userIds] 중 [category] 알림을 끈 사용자. */
    @Query(
        "select s.userId from NotificationSetting s " +
            "where s.category = :category and s.enabled = false and s.userId in :userIds",
    )
    fun findDisabledUserIds(
        @Param("category") category: NotificationCategory,
        @Param("userIds") userIds: Collection<Long>,
    ): List<Long>
}
