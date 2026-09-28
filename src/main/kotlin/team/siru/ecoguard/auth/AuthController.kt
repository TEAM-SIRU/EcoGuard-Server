package team.siru.ecoguard.auth

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.siru.ecoguard.auth.dto.LoginRequest
import team.siru.ecoguard.auth.dto.LoginResponse

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        return ResponseEntity.ok(authService.login(request.authCode))
    }

    @PostMapping("/logout")
    fun logout(): ResponseEntity<Void> {
        authService.logout()
        return ResponseEntity.ok().build()
    }
}
