package team.siru.ecoguard.common.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import team.siru.ecoguard.user.Role
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties,
) {
    private val key: SecretKey by lazy { Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray()) }

    fun generateTokenPair(userId: Long, role: Role): TokenPair {
        return TokenPair(
            accessToken = generateToken(userId, role, jwtProperties.accessTokenValiditySeconds, TOKEN_TYPE_ACCESS),
            refreshToken = generateToken(userId, role, jwtProperties.refreshTokenValiditySeconds, TOKEN_TYPE_REFRESH),
        )
    }

    private fun generateToken(userId: Long, role: Role, validitySeconds: Long, tokenType: String): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("role", role.name)
            .claim("type", tokenType)
            // iat는 초 단위라 같은 초 안의 로그아웃과 구분되지 않으므로 밀리초 값을 따로 넣는다.
            .claim(CLAIM_ISSUED_AT_MS, now.toEpochMilli())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(validitySeconds)))
            .signWith(key)
            .compact()
    }

    fun parseClaims(token: String): Claims? {
        return try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        } catch (e: ExpiredJwtException) {
            null
        } catch (e: JwtException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun isAccessToken(claims: Claims): Boolean = claims["type"] == TOKEN_TYPE_ACCESS

    fun issuedAtMillis(claims: Claims): Long = (claims[CLAIM_ISSUED_AT_MS] as? Number)?.toLong() ?: 0L

    fun isRefreshToken(claims: Claims): Boolean = claims["type"] == TOKEN_TYPE_REFRESH

    companion object {
        private const val CLAIM_ISSUED_AT_MS = "iatMs"
        const val TOKEN_TYPE_ACCESS = "access"
        const val TOKEN_TYPE_REFRESH = "refresh"
    }
}
