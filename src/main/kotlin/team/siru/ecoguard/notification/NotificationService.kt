package team.siru.ecoguard.notification

import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.user.Role
import team.siru.ecoguard.user.UserRepository
import java.time.Clock
import java.time.LocalDateTime

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val settingRepository: NotificationSettingRepository,
    private val userRepository: UserRepository,
    private val pushSender: PushSender,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 알림함에 기록하고 푸시를 보낸다. FCM 호출이 DB 트랜잭션을 붙잡지 않도록 이 메서드 자체는 트랜잭션이 아니다. */
    fun deliver(event: NotificationEvent) {
        val audienceIds = event.audience?.let { userRepository.findIdsByRole(it) }.orEmpty()
        val userIds = (event.userIds + audienceIds).distinct()
        if (userIds.isEmpty()) return

        val saved = notificationRepository.saveAll(
            userIds.map { Notification(it, event.type, event.title, event.body, event.refId) },
        )
        val idByUser = saved.associate { it.userId to it.id }

        // 알림함에는 모두 남기고, 이 종류의 푸시를 끈 사용자에게만 푸시를 보내지 않는다.
        val muted = settingRepository.findDisabledUserIds(event.type.category, userIds).toSet()
        val pushTargets = userIds.filterNot { it in muted }
        if (pushTargets.isEmpty()) return

        val tokensByUser = deviceTokenRepository.findByUserIdIn(pushTargets).groupBy({ it.userId }, { it.token })
        if (userIds.size == 1) {
            // 한 명에게 가는 알림은 알림함 id 도 실어서, 앱이 알림을 눌렀을 때 읽음 처리를 같이 할 수 있다.
            tokensByUser.forEach { (userId, tokens) -> push(tokens, event, idByUser[userId]) }
        } else {
            // 여러 명에게 가는 알림(공지·모집·여러 학생 배정)은 FCM 호출을 줄이려고 토큰을 모아 한 번에 보낸다.
            push(tokensByUser.values.flatten(), event, notificationId = null)
        }
    }

    private fun push(tokens: List<String>, event: NotificationEvent, notificationId: Long?) {
        if (tokens.isEmpty()) return
        val message = PushMessage(
            title = event.title,
            body = event.body,
            data = buildMap {
                put("type", event.type.name)
                event.refId?.let { put("refId", it.toString()) }
                notificationId?.let { put("notificationId", it.toString()) }
            },
        )
        val dead = pushSender.send(tokens, message)
        if (dead.isNotEmpty()) {
            deviceTokenRepository.deleteByTokenIn(dead)
            log.info("사용할 수 없는 푸시 토큰 {}개를 삭제했습니다.", dead.size)
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

    /** [role] 이 설정할 수 있는 카테고리의 수신 여부. 설정한 적 없는 카테고리는 켜진 것으로 돌려준다. */
    @Transactional(readOnly = true)
    fun getSettings(userId: Long, role: Role): Map<NotificationCategory, Boolean> {
        val saved = settingRepository.findByUserId(userId).associate { it.category to it.enabled }
        return NotificationCategory.entries.filter { it.audience == role }.associateWith { saved[it] ?: true }
    }

    @Transactional
    fun updateSetting(userId: Long, role: Role, category: NotificationCategory, enabled: Boolean) {
        if (category.audience != role) throw BusinessException(ErrorCode.INVALID_NOTIFICATION_CATEGORY)
        val existing = settingRepository.findByUserIdAndCategory(userId, category)
        if (existing == null) {
            settingRepository.save(NotificationSetting(userId, category, enabled))
        } else {
            existing.enabled = enabled
        }
    }

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
