package team.siru.ecoguard.notification

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TokenCleanupScheduler(
    private val notificationService: NotificationService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    fun purge() {
        val deleted = notificationService.purgeStaleTokens()
        if (deleted > 0) log.info("오래된 푸시 토큰 {}개를 삭제했습니다.", deleted)
    }
}
