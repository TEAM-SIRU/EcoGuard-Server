package team.siru.ecoguard.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class GsmOAuthConfig(
    private val properties: GsmOAuthProperties,
) {

    /** 실 연동(mock=false)일 때만 만든다. mock 모드에서는 client-id/secret 이 비어 있어 SDK가 만들어지지 않는다. */
    @Bean
    @ConditionalOnProperty(prefix = "gsm.oauth", name = ["mock"], havingValue = "false", matchIfMissing = true)
    fun dataGsmOAuthClient(): DataGsmOAuthClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.timeoutMillis))
            .build()
        val restClient = RestClient.builder()
            .requestFactory(
                JdkClientHttpRequestFactory(httpClient).apply {
                    setReadTimeout(Duration.ofMillis(properties.timeoutMillis))
                },
            )
            .build()
        return DataGsmOAuthClient.builder(properties.clientId, properties.clientSecret)
            .authorizationBaseUrl(properties.authorizationBaseUrl)
            .userInfoBaseUrl(properties.resourceBaseUrl)
            .httpClient(RestClientDataGsmHttpClient(restClient))
            .build()
    }
}
