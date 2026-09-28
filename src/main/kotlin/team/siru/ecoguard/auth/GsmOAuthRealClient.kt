package team.siru.ecoguard.auth

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
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
@ConditionalOnProperty(prefix = "gsm.oauth", name = ["mock"], havingValue = "false")
class GsmOAuthRealClient(
    private val gsmAuthorizationRestClient: RestClient,
    private val gsmResourceRestClient: RestClient,
    private val properties: GsmOAuthProperties,
) : GsmOAuthClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun authenticate(authCode: String): GsmUserInfo {
        val token = exchangeToken(authCode)
        val account = fetchAccountInfo(token.accessToken)
        return toGsmUserInfo(account)
    }

    private fun exchangeToken(authCode: String): GsmTokenResponse {
        val request = GsmTokenRequest(
            clientId = properties.clientId,
            clientSecret = properties.clientSecret,
            code = authCode,
            redirectUri = properties.redirectUri,
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
}
