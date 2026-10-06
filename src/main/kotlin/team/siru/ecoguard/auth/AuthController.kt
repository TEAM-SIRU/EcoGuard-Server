package team.siru.ecoguard.auth

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.siru.ecoguard.auth.dto.LoginRequest
import team.siru.ecoguard.auth.dto.LoginResponse
import team.siru.ecoguard.auth.dto.RefreshRequest
import team.siru.ecoguard.auth.dto.TokenResponse
import team.siru.ecoguard.common.security.SecurityUtils
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val APP_CALLBACK_URI = "ecoguard://auth/callback"

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {

    /**
     * dataGSM 이 인가 후 redirect 하는 주소. 받은 값을 그대로 앱 callback(`ecoguard://auth/callback`)으로 다시 302 redirect 한다.
     * 성공이면 code/state, 실패면 error/error_description 이 온다. 값은 바꾸지 않고 쿼리 파라미터로만 안전하게 인코딩한다.
     */
    @GetMapping("/callback")
    fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
        @RequestParam(name = "error_description", required = false) errorDescription: String?,
    ): ResponseEntity<Void> {
        val query = listOf("code" to code, "state" to state, "error" to error, "error_description" to errorDescription)
            .filter { it.second != null }
            .joinToString("&") { (name, value) -> "$name=${encodeQueryValue(value!!)}" }
        val location = if (query.isEmpty()) APP_CALLBACK_URI else "$APP_CALLBACK_URI?$query"
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build()
    }

    private fun encodeQueryValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

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
