package team.siru.ecoguard.auth

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import team.siru.ecoguard.auth.dto.GsmAccountInfoResponse
import team.siru.ecoguard.auth.dto.GsmTokenRequest
import team.siru.ecoguard.auth.dto.GsmTokenResponse
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.user.Role

/**
 * 실제 dataGSM OAuth 연동 구현체. `gsm.oauth.mock=false`일 때 활성화된다.
 *
 * 필요한 설정(application.yaml / 환경변수):
 * - gsm.oauth.client-id       (GSM_OAUTH_CLIENT_ID)   : datagsm.kr/clients 에서 발급받은 Client ID
 * - gsm.oauth.client-secret   (GSM_OAUTH_CLIENT_SECRET) : 같은 화면에서 발급받은 API 키(Client Secret)
 * - gsm.oauth.redirect-uri    (GSM_OAUTH_REDIRECT_URI)  : 클라이언트 등록 시 함께 등록한 콜백 주소
 *
 * 클라이언트(프론트/앱)가 dataGSM 로그인 페이지에서 인가 코드를 받아 우리 서버의
 * POST /api/v1/auth/login 에 `authCode`로 전달하면, 이 클래스가 그 코드를
 * access token으로 교환하고 사용자 정보를 조회한다.
 */
@Component
@ConditionalOnProperty(prefix = "gsm.oauth", name = ["mock"], havingValue = "false", matchIfMissing = true)
class GsmOAuthRealClient(
    private val gsmAuthorizationRestClient: RestClient,
    private val gsmResourceRestClient: RestClient,
    private val properties: GsmOAuthProperties,
) : GsmOAuthClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun authenticate(authCode: String, redirectUri: String?): GsmUserInfo {
        val token = exchangeToken(authCode, resolveRedirectUri(redirectUri))
        val account = fetchAccountInfo(token.accessToken)
        return toGsmUserInfo(account)
    }

    /** 요청이 보낸 redirectUri는 서버 기본 주소나 허용 목록에 있을 때만 쓴다. 임의 주소로 토큰 교환을 시도하지 못하게 한다. */
    private fun resolveRedirectUri(requested: String?): String {
        if (requested.isNullOrBlank()) return properties.redirectUri
        if (requested != properties.redirectUri && requested !in properties.allowedRedirectUris) {
            throw BusinessException(ErrorCode.OAUTH_FAILED, "허용되지 않은 redirectUri 입니다.")
        }
        return requested
    }

    private fun exchangeToken(authCode: String, redirectUri: String): GsmTokenResponse {
        val request = GsmTokenRequest(
            clientId = properties.clientId,
            clientSecret = properties.clientSecret,
            code = authCode,
            redirectUri = redirectUri,
        )
        return try {
            gsmAuthorizationRestClient.post()
                .uri("/v1/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GsmTokenResponse::class.java)
                ?: throw BusinessException(ErrorCode.OAUTH_FAILED)
        } catch (e: BusinessException) {
            throw e
        } catch (e: HttpClientErrorException.Forbidden) {
            log.warn("dataGSM 토큰 교환 거부(403)", e)
            throw BusinessException(ErrorCode.OAUTH_FAILED, FORBIDDEN_MESSAGE)
        } catch (e: Exception) {
            log.warn("dataGSM 토큰 교환 실패", e)
            throw BusinessException(ErrorCode.OAUTH_FAILED)
        }
    }

    private fun fetchAccountInfo(accessToken: String): GsmAccountInfoResponse {
        return try {
            gsmResourceRestClient.get()
                .uri("/userinfo")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(GsmAccountInfoResponse::class.java)
                ?: throw BusinessException(ErrorCode.OAUTH_FAILED)
        } catch (e: BusinessException) {
            throw e
        } catch (e: HttpClientErrorException.Forbidden) {
            log.warn("dataGSM 사용자 정보 조회 거부(403)", e)
            throw BusinessException(ErrorCode.OAUTH_FAILED, FORBIDDEN_MESSAGE)
        } catch (e: Exception) {
            log.warn("dataGSM 사용자 정보 조회 실패", e)
            throw BusinessException(ErrorCode.OAUTH_FAILED)
        }
    }

    private fun toGsmUserInfo(account: GsmAccountInfoResponse): GsmUserInfo {
        val role = runCatching { Role.valueOf(account.objectType ?: "") }
            .getOrElse { throw BusinessException(ErrorCode.OAUTH_FAILED, "학생/교사 정보가 연결되지 않은 계정입니다.") }

        return when (role) {
            Role.STUDENT -> {
                val student = account.student ?: throw BusinessException(ErrorCode.OAUTH_FAILED)
                GsmUserInfo(
                    gsmAccountId = account.id,
                    email = account.email,
                    name = student.name,
                    role = Role.STUDENT,
                    studentNumber = student.studentNumber?.toString(),
                    grade = student.grade,
                    classNo = student.classNum,
                )
            }

            Role.TEACHER -> {
                val teacher = account.teacher ?: throw BusinessException(ErrorCode.OAUTH_FAILED)
                GsmUserInfo(
                    gsmAccountId = account.id,
                    email = account.email,
                    name = teacher.name,
                    role = Role.TEACHER,
                    studentNumber = null,
                    grade = null,
                    classNo = null,
                )
            }
        }
    }

    private companion object {
        /** dataGSM은 어드민 승인 전(PENDING) 선생님 계정에 403을 준다. 다른 사유의 403일 수도 있어 단정하지 않는다. */
        const val FORBIDDEN_MESSAGE = "dataGSM에서 접근을 거부했습니다. 선생님 계정이라면 관리자 승인 대기 중일 수 있습니다."
    }
}
