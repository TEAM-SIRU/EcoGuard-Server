package team.siru.ecoguard.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableAsync
@EnableScheduling
class AsyncConfig
