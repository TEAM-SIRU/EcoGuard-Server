package team.siru.ecoguard.common.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import team.siru.ecoguard.aireview.AiReviewQueueProperties
import team.siru.ecoguard.aireview.AiServerProperties
import team.siru.ecoguard.aireview.GeminiProperties
import team.siru.ecoguard.auth.DemoAccountProperties
import team.siru.ecoguard.auth.GsmOAuthProperties
import team.siru.ecoguard.cleaningarea.CleaningAreaSeedProperties
import team.siru.ecoguard.common.security.JwtProperties
import team.siru.ecoguard.common.storage.FileStorageProperties
import team.siru.ecoguard.schoolcalendar.NeisProperties
import team.siru.ecoguard.verification.VerificationProperties

@Configuration
@EnableConfigurationProperties(
    JwtProperties::class,
    FileStorageProperties::class,
    GsmOAuthProperties::class,
    AiServerProperties::class,
    GeminiProperties::class,
    AiReviewQueueProperties::class,
    DemoAccountProperties::class,
    CleaningAreaSeedProperties::class,
    NeisProperties::class,
    VerificationProperties::class,
)
class PropertiesConfig
