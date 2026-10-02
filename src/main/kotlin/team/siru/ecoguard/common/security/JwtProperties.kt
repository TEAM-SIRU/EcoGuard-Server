package team.siru.ecoguard.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenValiditySeconds: Long = 60 * 60 * 2,
    val refreshTokenValiditySeconds: Long = 60 * 60 * 24 * 7,
)
