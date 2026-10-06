package team.siru.ecoguard.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class ClockConfig {

    /** 서버 OS 시간대와 무관하게 항상 KST 기준으로 동작하도록 고정한다. */
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
