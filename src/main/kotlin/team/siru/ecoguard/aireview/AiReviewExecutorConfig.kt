package team.siru.ecoguard.aireview

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

const val AI_REVIEW_EXECUTOR = "aiReviewExecutor"

@Configuration
class AiReviewExecutorConfig(
    private val properties: AiReviewQueueProperties,
) {

    /**
     * AI 검수 전용 작업 실행기. 작업 스레드 수와 대기열 길이에 상한을 둔다.
     * 기본 실행기는 대기열이 무제한이라 제출이 몰리면 사진이 메모리에 계속 쌓일 수 있다.
     * 대기열이 가득 차면 새 작업은 거부(TaskRejectedException)되고, 제출 쪽에서 수동 검토로 보낸다.
     */
    @Bean(name = [AI_REVIEW_EXECUTOR])
    fun aiReviewExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = properties.workers
        maxPoolSize = properties.workers
        queueCapacity = properties.capacity
        setThreadNamePrefix("ai-review-")
    }
}
