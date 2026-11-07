package team.siru.ecoguard.notification

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

const val NOTIFICATION_EXECUTOR = "notificationExecutor"

@Configuration
class NotificationExecutorConfig {

    /**
     * 알림 전용 실행기. FCM 호출이 느려져도 다른 비동기 작업(AI 검수 등)에 영향을 주지 않고,
     * 대기열이 가득 차면 알림을 버리지 않도록 호출한 스레드가 직접 처리한다.
     */
    @Bean(name = [NOTIFICATION_EXECUTOR])
    fun notificationExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 4
        queueCapacity = 1000
        setThreadNamePrefix("notification-")
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
    }
}
