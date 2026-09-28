package team.siru.ecoguard.aireview

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class AiServerConfig(
    private val properties: AiServerProperties,
) {

    @Bean
    fun aiServerRestClient(): RestClient {
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
