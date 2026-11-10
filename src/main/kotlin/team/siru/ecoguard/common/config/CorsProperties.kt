package team.siru.ecoguard.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cors")
data class CorsProperties(
    /** 우리 서버를 호출해도 되는 웹 주소 목록. 환경변수로 줄 때는 쉼표로 구분한다. */
    val allowedOrigins: List<String> = listOf("http://localhost:3000"),
)
