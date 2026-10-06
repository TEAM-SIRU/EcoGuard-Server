package team.siru.ecoguard.user

import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.siru.ecoguard.common.security.SecurityUtils
import team.siru.ecoguard.user.dto.MyProfileResponse

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {

    @GetMapping("/me")
    fun getMyProfile(): ResponseEntity<MyProfileResponse> =
        ResponseEntity.ok(userService.getMyProfile(SecurityUtils.currentUserId()))

    /** 회원 탈퇴(학생). 개인정보를 익명화하고 모든 토큰을 무효화한다. */
    @DeleteMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    fun withdraw(): ResponseEntity<Void> {
        userService.withdraw(SecurityUtils.currentUserId())
        return ResponseEntity.ok().build()
    }
}
