package team.siru.ecoguard.auth

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.siru.ecoguard.auth.dto.LoginRequest
import team.siru.ecoguard.auth.dto.LoginResponse
import team.siru.ecoguard.auth.dto.RefreshRequest
import team.siru.ecoguard.auth.dto.TokenResponse
import team.siru.ecoguard.common.security.SecurityUtils

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        return ResponseEntity.ok(authService.login(request.authCode))
    }

    /** 리프레시 토큰으로 새 토큰 쌍을 발급한다. 쓸 때마다 갱신되므로 계속 사용하는 동안 로그인이 유지된다. */
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<TokenResponse> {
        return ResponseEntity.ok(authService.refresh(request.refreshToken))
    }

    @PostMapping("/logout")
    fun logout(): ResponseEntity<Void> {
        authService.logout(SecurityUtils.currentUserId())
        return ResponseEntity.ok().build()
    }
}
