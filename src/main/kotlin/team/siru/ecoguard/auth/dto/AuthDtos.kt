package team.siru.ecoguard.auth.dto

import jakarta.validation.constraints.NotBlank
import team.siru.ecoguard.user.Role
import team.siru.ecoguard.user.User

data class LoginRequest(
    @field:NotBlank
    val authCode: String,
    /** 인가 요청 때 쓴 redirect_uri. 웹처럼 서버 기본 주소가 아닌 곳에서 로그인할 때만 보낸다(서버 허용 목록에 있어야 함). */
    val redirectUri: String? = null,
)

data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String,
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
)

data class UserSummaryResponse(
    val userId: Long,
    val name: String,
    val role: Role,
) {
    companion object {
        fun from(user: User) = UserSummaryResponse(user.id, user.name, user.role)
    }
}

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserSummaryResponse,
)
