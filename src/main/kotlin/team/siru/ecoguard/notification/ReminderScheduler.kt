package team.siru.ecoguard.notification

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime

@Component
class ReminderScheduler(
    private val reminderService: ReminderService,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 평일 매분 확인한다. 구역별 알림 시각(구역마다 인증 시간이 다를 수 있다)과 맞는 분에만 실제로 보낸다. */
    @Scheduled(cron = "0 * * * * MON-FRI", zone = "Asia/Seoul")
    fun run() {
        try {
            reminderService.sendReminders(LocalDateTime.now(clock))
        } catch (e: Exception) {
            log.error("청소 리마인더 처리에 실패했습니다", e)
        }
    }
}
