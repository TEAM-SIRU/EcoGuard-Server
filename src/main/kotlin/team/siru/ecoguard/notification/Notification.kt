package team.siru.ecoguard.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import team.siru.ecoguard.common.BaseEntity
import java.time.LocalDateTime

/** 앱 안의 알림함에 쌓이는 알림. 푸시를 놓쳐도 여기서 다시 볼 수 있다. */
@Entity
@Table(name = "notifications", indexes = [Index(name = "idx_notifications_user", columnList = "user_id, id")])
class Notification(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: NotificationType,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false, length = 500)
    var body: String,

    /** 알림이 가리키는 대상(인증·이의신청·구역)의 id. */
    var refId: Long? = null,

    var readAt: LocalDateTime? = null,
) : BaseEntity()
