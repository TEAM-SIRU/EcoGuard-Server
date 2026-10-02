package team.siru.ecoguard.auth.dto

import jakarta.validation.constraints.NotBlank
import team.siru.ecoguard.user.Role
import team.siru.ecoguard.user.User

data class LoginRequest(
    @field:NotBlank
    val authCode: String,
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
