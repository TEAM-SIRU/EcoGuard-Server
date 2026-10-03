package team.siru.ecoguard.aireview

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
@ConditionalOnProperty(prefix = "ai-review", name = ["provider"], havingValue = "gemini", matchIfMissing = true)
class GeminiConfig(
    private val properties: GeminiProperties,
) {

    @Bean
    fun geminiRestClient(): RestClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.timeoutMillis))
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofMillis(properties.timeoutMillis))
        }
        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .build()
    }
}
