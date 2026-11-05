package team.siru.ecoguard.notification

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class NotificationListener(
    private val notificationService: NotificationService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 알림을 만든 트랜잭션이 커밋된 뒤에, 별도 스레드에서 처리한다. 롤백되면 알림도 나가지 않고,
     * 알림이 실패해도 본래 업무(승인·적립 등)는 영향받지 않는다.
     */
    @Async(NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: NotificationEvent) {
        try {
            notificationService.deliver(event)
        } catch (e: Exception) {
            log.error("알림 처리에 실패했습니다 (type={}, 대상 {}명)", event.type, event.userIds.size, e)
        }
    }
}
