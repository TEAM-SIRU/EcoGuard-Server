package team.siru.ecoguard.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import team.siru.ecoguard.user.Role
import team.siru.ecoguard.user.User

@Schema(description = "로그인 요청")
data class LoginRequest(
    @field:Schema(
        description = "dataGSM 이 딥링크 `ecoguard://auth/callback?code=...` 로 넘겨 준 `code` 값 그대로. 1회용",
        example = "c0ffee12-3456-7890-abcd-ef0123456789",
    )
    @field:NotBlank
    val authCode: String,
    /** 인가 요청 때 쓴 redirect_uri. 웹처럼 서버 기본 주소가 아닌 곳에서 로그인할 때만 보낸다(서버 허용 목록에 있어야 함). */
    @field:Schema(
        description = "**앱은 보내지 않는다(생략).** 생략하면 서버에 등록된 콜백 주소로 토큰을 교환한다. " +
            "교사 웹처럼 서버 허용 목록에 있는 다른 redirect_uri 로 인가를 받았을 때만 그 값을 그대로 보낸다. " +
            "허용되지 않은 값(예: `ecoguard://auth/callback`)이면 `401 OAUTH_FAILED`",
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
    )
    val redirectUri: String? = null,
)

@Schema(description = "토큰 재발급 요청")
data class RefreshRequest(
    @field:Schema(description = "로그인 또는 이전 재발급 때 받은 refreshToken (accessToken 아님)", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHlwZSI6InJlZnJlc2gifQ.sig")
    @field:NotBlank
    val refreshToken: String,
)

@Schema(description = "새로 발급된 토큰 쌍. 둘 다 교체해 저장한다")
data class TokenResponse(
    @field:Schema(description = "API 호출용 JWT. `Authorization: Bearer {accessToken}`", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHlwZSI6ImFjY2VzcyJ9.sig")
    val accessToken: String,
    @field:Schema(description = "재발급용 JWT. `POST /auth/refresh` 에만 쓴다. 재발급할 때마다 새 값으로 바뀐다", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHlwZSI6InJlZnJlc2gifQ.sig")
    val refreshToken: String,
)

@Schema(description = "로그인한 사용자 요약")
data class UserSummaryResponse(
    @field:Schema(description = "EcoGuard 사용자 ID", example = "12")
    val userId: Long,
    @field:Schema(description = "이름 (dataGSM 기준)", example = "홍길동")
    val name: String,
    @field:Schema(description = "역할. 앱은 이 값으로 학생/교사 화면을 나눈다")
    val role: Role,
) {
    companion object {
        fun from(user: User) = UserSummaryResponse(user.id, user.name, user.role)
    }
}

@Schema(description = "로그인 응답")
data class LoginResponse(
    @field:Schema(description = "API 호출용 JWT. `Authorization: Bearer {accessToken}`", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHlwZSI6ImFjY2VzcyJ9.sig")
    val accessToken: String,
    @field:Schema(description = "재발급용 JWT. `POST /auth/refresh` 에만 쓴다", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHlwZSI6InJlZnJlc2gifQ.sig")
    val refreshToken: String,
    val user: UserSummaryResponse,
)
