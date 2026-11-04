package team.siru.ecoguard.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import team.siru.ecoguard.common.BaseEntity
import java.time.LocalDateTime

/** 푸시를 받을 기기. 토큰 하나는 항상 한 사용자의 것이다(다른 계정으로 로그인하면 주인이 바뀐다). */
@Entity
@Table(name = "device_tokens")
class DeviceToken(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(nullable = false, unique = true, length = 512)
    var token: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var platform: DevicePlatform,

    /** 앱이 마지막으로 토큰을 등록한 시각. 오래 갱신되지 않은 토큰을 정리하는 기준이다. */
    @Column(nullable = false)
    var lastSeenAt: LocalDateTime,
) : BaseEntity()
