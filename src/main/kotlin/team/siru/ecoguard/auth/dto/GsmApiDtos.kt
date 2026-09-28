package team.siru.ecoguard.auth.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * dataGSM의 POST /v1/oauth/token 요청 바디.
 * https://github.com/themoment-team/datagsm-server 의
 * Oauth2TokenReqDto 스펙을 따른다.
 */
data class GsmTokenRequest(
    @JsonProperty("grant_type")
    val grantType: String = "authorization_code",
    @JsonProperty("client_id")
    val clientId: String,
    @JsonProperty("client_secret")
    val clientSecret: String,
    val code: String,
    @JsonProperty("redirect_uri")
    val redirectUri: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GsmTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("token_type")
    val tokenType: String? = null,
    @JsonProperty("expires_in")
    val expiresIn: Long? = null,
)

/** dataGSM GET /userinfo 응답(AccountInfoResDto)에서 필요한 필드만 매핑한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GsmAccountInfoResponse(
    val id: Long,
    val email: String,
    val objectType: String? = null,
    val student: GsmStudentInfo? = null,
    val teacher: GsmTeacherInfo? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GsmStudentInfo(
    val name: String,
    val grade: Int? = null,
    val classNum: Int? = null,
    val studentNumber: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GsmTeacherInfo(
    val name: String,
)
