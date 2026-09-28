package team.siru.ecoguard.aireview

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ai-server")
data class AiServerProperties(
    val baseUrl: String = "http://localhost:9000",
    val evaluatePath: String = "/api/v1/cleaning/evaluate",
    val timeoutMillis: Long = 15000,
)
