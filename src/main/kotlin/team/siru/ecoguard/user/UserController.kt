package team.siru.ecoguard.user

import org.springframework.http.ResponseEntity
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
}
