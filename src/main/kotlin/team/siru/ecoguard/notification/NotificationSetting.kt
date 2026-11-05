package team.siru.ecoguard.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import team.siru.ecoguard.common.BaseEntity

/** 사용자가 알림 종류별로 정한 수신 여부. 행이 없으면 켜진 것으로 본다. */
@Entity
@Table(
    name = "notification_settings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "category"])],
)
class NotificationSetting(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var category: NotificationCategory,

    @Column(nullable = false)
    var enabled: Boolean,
) : BaseEntity()
