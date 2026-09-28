package team.siru.ecoguard.auth

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class GsmOAuthConfig(
    private val properties: GsmOAuthProperties,
) {

    private fun requestFactory(): JdkClientHttpRequestFactory {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.timeoutMillis))
            .build()
        return JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofMillis(properties.timeoutMillis))
        }
    }

    @Bean
    fun gsmAuthorizationRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(properties.authorizationBaseUrl)
            .requestFactory(requestFactory())
            .build()

    @Bean
    fun gsmResourceRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(properties.resourceBaseUrl)
            .requestFactory(requestFactory())
            .build()
}
