package team.siru.ecoguard.auth

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.auth.dto.LoginResponse
import team.siru.ecoguard.auth.dto.TokenResponse
import team.siru.ecoguard.auth.dto.UserSummaryResponse
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.JwtTokenProvider
import team.siru.ecoguard.common.security.TokenRevocationChecker
import team.siru.ecoguard.user.User
import team.siru.ecoguard.user.UserRepository

@Service
class AuthService(
    private val gsmOAuthClient: GsmOAuthClient,
    private val demoAccountAuthenticator: DemoAccountAuthenticator,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val tokenRevocationChecker: TokenRevocationChecker,
    private val transactionTemplate: TransactionTemplate,
) {

    fun login(authCode: String): LoginResponse {
        // 외부 OAuth 호출은 트랜잭션(DB 커넥션) 밖에서 한다.
        val userInfo = demoAccountAuthenticator.authenticate(authCode) ?: gsmOAuthClient.authenticate(authCode)

        // 같은 계정의 첫 로그인 요청이 동시에 오면 둘 다 새로 가입하려다 유니크 제약에 걸린다. 한 번 더 시도하면 이미 가입된 사용자를 찾는다.
        val user = try {
            transactionTemplate.execute { upsertUser(userInfo) }!!
        } catch (e: DataIntegrityViolationException) {
            transactionTemplate.execute { upsertUser(userInfo) }!!
        }

        val tokenPair = jwtTokenProvider.generateTokenPair(user.id, user.role)
        return LoginResponse(tokenPair.accessToken, tokenPair.refreshToken, UserSummaryResponse.from(user))
    }

    private fun upsertUser(userInfo: GsmUserInfo): User =
        userRepository.findByGsmAccountId(userInfo.gsmAccountId)
            ?.apply {
                email = userInfo.email
                name = userInfo.name
                role = userInfo.role
                studentNumber = userInfo.studentNumber
                grade = userInfo.grade
                classNo = userInfo.classNo
            }
            ?: userRepository.saveAndFlush(
                User(
                    gsmAccountId = userInfo.gsmAccountId,
                    email = userInfo.email,
                    name = userInfo.name,
                    role = userInfo.role,
                    studentNumber = userInfo.studentNumber,
                    grade = userInfo.grade,
                    classNo = userInfo.classNo,
                ),
            )

    /**
     * 리프레시 토큰을 검증하고 새 토큰 쌍을 발급한다(회전). 리프레시 토큰의 7일 유효기간이 사용할 때마다
     * 새로 시작되므로, 앱을 계속 쓰는 한 로그인이 풀리지 않는다. 역할은 토큰이 아닌 DB 기준으로 다시 정한다.
     */
    @Transactional(readOnly = true)
    fun refresh(refreshToken: String): TokenResponse {
        val claims = jwtTokenProvider.parseClaims(refreshToken)
            ?.takeIf { jwtTokenProvider.isRefreshToken(it) }
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val userId = claims.subject.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val user = userRepository.findById(userId).orElseThrow { BusinessException(ErrorCode.UNAUTHORIZED) }
        if (tokenRevocationChecker.isRevoked(user.id, claims)) throw BusinessException(ErrorCode.UNAUTHORIZED)

        val tokenPair = jwtTokenProvider.generateTokenPair(user.id, user.role)
        return TokenResponse(tokenPair.accessToken, tokenPair.refreshToken)
    }

    /**
     * 이 시점 이전에 발급된 이 사용자의 모든 토큰(액세스/리프레시)을 무효로 만든다.
     * 기기 단위로 구분하지 않으므로 다른 기기의 로그인도 함께 풀린다. 분실/유출 대응에도 같은 방식으로 쓴다.
     */
    @Transactional
    fun logout(userId: Long) {
        val user = userRepository.findById(userId).orElseThrow { BusinessException(ErrorCode.UNAUTHORIZED) }
        user.tokensValidAfter = System.currentTimeMillis()
    }
}
