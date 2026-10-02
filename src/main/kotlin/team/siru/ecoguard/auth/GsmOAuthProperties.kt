package team.siru.ecoguard.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gsm.oauth")
data class GsmOAuthProperties(
    /** true면 GsmOAuthMockClient, false면 GsmOAuthRealClient가 활성화된다. */
    val mock: Boolean = false,
    /** datagsm.kr/clients 에서 OAuth 클라이언트를 등록하고 발급받은 값. */
    val clientId: String = "",
    val clientSecret: String = "",
    /** 클라이언트 등록 시 함께 등록한 콜백 주소와 반드시 동일해야 한다. */
    val redirectUri: String = "",
    val authorizationBaseUrl: String = "https://oauth.authorization.datagsm.kr",
    val resourceBaseUrl: String = "https://oauth.resource.datagsm.kr",
    val timeoutMillis: Long = 10000,
)
