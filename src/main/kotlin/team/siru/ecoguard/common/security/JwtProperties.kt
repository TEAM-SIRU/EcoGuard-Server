package team.siru.ecoguard.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenValiditySeconds: Long = 60 * 60 * 2,
    val refreshTokenValiditySeconds: Long = 60 * 60 * 24 * 7,
) {
    init {
        // 서명 키가 32바이트보다 짧으면 서버는 뜨지만 로그인이 전부 실패한다. 배포 헬스체크도 통과해 버리므로 시작할 때 막는다.
        check(secret.toByteArray().size >= MIN_SECRET_BYTES) {
            "jwt.secret 은 ${MIN_SECRET_BYTES}바이트 이상이어야 합니다. (JWT_SECRET 환경변수를 확인하세요)"
        }
    }

    companion object {
        const val MIN_SECRET_BYTES = 32
    }
}
