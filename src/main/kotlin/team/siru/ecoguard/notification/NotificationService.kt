package team.siru.ecoguard.notification

import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import java.time.Clock
import java.time.LocalDateTime

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val pushSender: PushSender,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 알림함에 기록하고 푸시를 보낸다. FCM 호출이 DB 트랜잭션을 붙잡지 않도록 이 메서드 자체는 트랜잭션이 아니다. */
    fun deliver(event: NotificationEvent) {
        val userIds = event.userIds.distinct()
        if (userIds.isEmpty()) return

        val saved = notificationRepository.saveAll(
            userIds.map { Notification(it, event.type, event.title, event.body, event.refId) },
        )
        val idByUser = saved.associate { it.userId to it.id }

        val tokensByUser = deviceTokenRepository.findByUserIdIn(userIds).groupBy({ it.userId }, { it.token })
        // 푸시 data 에는 알림 id 도 실어서, 앱이 알림을 눌렀을 때 알림함의 읽음 처리를 같이 할 수 있다.
        tokensByUser.forEach { (userId, tokens) ->
            val message = PushMessage(
                title = event.title,
                body = event.body,
                data = buildMap {
                    put("type", event.type.name)
                    event.refId?.let { put("refId", it.toString()) }
                    idByUser[userId]?.let { put("notificationId", it.toString()) }
                },
            )
            val dead = pushSender.send(tokens, message)
            if (dead.isNotEmpty()) {
                deviceTokenRepository.deleteByTokenIn(dead)
                log.info("사용할 수 없는 푸시 토큰 {}개를 삭제했습니다.", dead.size)
            }
        }
    }

    /** 같은 토큰이 이미 있으면 주인과 플랫폼을 새 값으로 바꾼다(기기를 다른 계정이 이어 쓰는 경우). */
    @Transactional
    fun registerToken(userId: Long, token: String, platform: DevicePlatform) {
        val now = LocalDateTime.now(clock)
        val existing = deviceTokenRepository.findByToken(token)
        if (existing == null) {
            deviceTokenRepository.save(DeviceToken(userId, token, platform, now))
        } else {
            existing.userId = userId
            existing.platform = platform
            existing.lastSeenAt = now
        }
    }

    fun removeToken(userId: Long, token: String) = deviceTokenRepository.deleteByUserIdAndToken(userId, token)

    fun removeAllTokens(userId: Long) = deviceTokenRepository.deleteByUserId(userId)

    /** 60일 넘게 갱신되지 않은 토큰을 지운다. */
    fun purgeStaleTokens(): Int = deviceTokenRepository.deleteStale(LocalDateTime.now(clock).minusDays(STALE_TOKEN_DAYS))

    @Transactional(readOnly = true)
    fun getMyNotifications(userId: Long, limit: Int): List<Notification> =
        notificationRepository.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, limit.coerceIn(1, MAX_LIMIT)))

    @Transactional(readOnly = true)
    fun countUnread(userId: Long): Long = notificationRepository.countByUserIdAndReadAtIsNull(userId)

    @Transactional
    fun markRead(userId: Long, notificationId: Long) {
        val notification = notificationRepository.findByIdAndUserId(notificationId, userId)
            ?: throw BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND)
        if (notification.readAt == null) notification.readAt = LocalDateTime.now(clock)
    }

    fun markAllRead(userId: Long) {
        notificationRepository.markAllRead(userId, LocalDateTime.now(clock))
    }

    private companion object {
        const val STALE_TOKEN_DAYS = 60L
        const val MAX_LIMIT = 100
    }
}
