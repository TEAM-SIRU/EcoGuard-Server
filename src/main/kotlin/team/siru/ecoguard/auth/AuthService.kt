package team.siru.ecoguard.auth

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.auth.dto.LoginResponse
import team.siru.ecoguard.auth.dto.TokenResponse
import team.siru.ecoguard.auth.dto.UserSummaryResponse
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.JwtTokenProvider
import team.siru.ecoguard.user.User
import team.siru.ecoguard.user.UserRepository

@Service
class AuthService(
    private val gsmOAuthClient: GsmOAuthClient,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
) {

    @Transactional
    fun login(authCode: String): LoginResponse {
        val userInfo = gsmOAuthClient.authenticate(authCode)

        val user = userRepository.findByGsmAccountId(userInfo.gsmAccountId)
            ?.apply {
                email = userInfo.email
                name = userInfo.name
                role = userInfo.role
                studentNumber = userInfo.studentNumber
                grade = userInfo.grade
                classNo = userInfo.classNo
            }
            ?: userRepository.save(
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

        val tokenPair = jwtTokenProvider.generateTokenPair(user.id, user.role)
        return LoginResponse(tokenPair.accessToken, tokenPair.refreshToken, UserSummaryResponse.from(user))
    }

    /**
     * 리프레시 토큰을 검증하고 새 토큰 쌍을 발급한다(회전). 리프레시 토큰의 14일 유효기간이 사용할 때마다
     * 새로 시작되므로, 앱을 계속 쓰는 한 로그인이 풀리지 않는다. 역할은 토큰이 아닌 DB 기준으로 다시 정한다.
     */
    @Transactional(readOnly = true)
    fun refresh(refreshToken: String): TokenResponse {
        val claims = jwtTokenProvider.parseClaims(refreshToken)
            ?.takeIf { jwtTokenProvider.isRefreshToken(it) }
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val userId = claims.subject.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val user = userRepository.findById(userId).orElseThrow { BusinessException(ErrorCode.UNAUTHORIZED) }

        val tokenPair = jwtTokenProvider.generateTokenPair(user.id, user.role)
        return TokenResponse(tokenPair.accessToken, tokenPair.refreshToken)
    }

    fun logout() {
        // 서버는 상태를 저장하지 않는 JWT를 사용하므로 별도의 무효화 처리가 없다.
        // 추후 리프레시 토큰 블랙리스트가 도입되면 이 지점에서 처리한다.
    }
}
