package team.siru.ecoguard.common.security

import io.jsonwebtoken.Claims
import org.springframework.stereotype.Component
import team.siru.ecoguard.user.UserRepository

/**
 * 서명과 만료가 유효한 토큰이라도 사용자가 삭제됐거나, 로그아웃/세션 폐기로
 * `tokensValidAfter` 이전에 발급된 토큰이면 거부한다.
 */
@Component
class TokenRevocationChecker(
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
) {

    fun isRevoked(userId: Long, claims: Claims): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return true
        val validAfter = user.tokensValidAfter ?: return false
        // 폐기와 같은 밀리초에 발급된 토큰도 무효로 본다(폐기 직전 발급분이 살아남지 않도록).
        return jwtTokenProvider.issuedAtMillis(claims) <= validAfter
    }
}
