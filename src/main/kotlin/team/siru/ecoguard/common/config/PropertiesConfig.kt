package team.siru.ecoguard.common.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import team.siru.ecoguard.aireview.AiServerProperties
import team.siru.ecoguard.auth.GsmOAuthProperties
import team.siru.ecoguard.common.security.JwtProperties
import team.siru.ecoguard.common.storage.FileStorageProperties

@Configuration
@EnableConfigurationProperties(
    JwtProperties::class,
    FileStorageProperties::class,
    GsmOAuthProperties::class,
    AiServerProperties::class,
)
class PropertiesConfig
