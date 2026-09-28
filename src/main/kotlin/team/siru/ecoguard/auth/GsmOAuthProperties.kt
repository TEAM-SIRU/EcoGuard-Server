package team.siru.ecoguard.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gsm.oauth")
data class GsmOAuthProperties(
    val mock: Boolean = true,
    val baseUrl: String = "",
)
