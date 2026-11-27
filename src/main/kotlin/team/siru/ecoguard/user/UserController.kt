package team.siru.ecoguard.user

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.siru.ecoguard.common.exception.ApiErrors
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.SecurityUtils
import team.siru.ecoguard.user.dto.MyProfileResponse

@Tag(name = "2. 사용자 (User)", description = "내 정보 조회, 회원 탈퇴")
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {

    @Operation(
        summary = "내 정보 조회",
        description = """
**[공통]** 로그인한 사용자의 정보입니다. 앱 시작 시 저장된 토큰이 유효한지 확인하는 용도로도 씁니다.

- 학생: `studentNumber`, `grade`, `classNo` 가 채워집니다(dataGSM 에 없으면 `null` 일 수 있음).
- 교사: 세 값이 모두 `null` 입니다.
- 학년/반은 로그인할 때마다 dataGSM 기준으로 갱신됩니다.
""",
    )
    @GetMapping("/me")
    @ApiErrors(ErrorCode.USER_NOT_FOUND)
    fun getMyProfile(): ResponseEntity<MyProfileResponse> =
        ResponseEntity.ok(userService.getMyProfile(SecurityUtils.currentUserId()))

    /** 회원 탈퇴(학생). 개인정보를 익명화하고 모든 토큰을 무효화한다. */
    @Operation(
        summary = "회원 탈퇴",
        description = """
**[학생]** 개인정보(이름, 이메일, 학번, 학년/반)를 익명화하고 이 사용자의 모든 토큰을 무효로 만듭니다. 응답 바디는 없습니다(`200`).

- 청소 구역 배정과 모집 신청은 지워집니다(정원을 비우기 위해).
- 인증·이의신청·봉사시간 기록은 학교 기록이라 익명 상태로 남습니다.
- 같은 dataGSM 계정으로 다시 로그인하면 **새 사용자** 로 가입됩니다(이전 기록과 연결되지 않음).
- 호출 후 앱은 저장된 토큰을 지우고 로그인 화면으로 이동하세요. 이후 기존 토큰으로 부르는 API 는 모두 `401` 입니다.
""",
    )
    @DeleteMapping("/me")
    @ApiErrors(ErrorCode.USER_NOT_FOUND)
    @PreAuthorize("hasRole('STUDENT')")
    fun withdraw(): ResponseEntity<Void> {
        userService.withdraw(SecurityUtils.currentUserId())
        return ResponseEntity.ok().build()
    }
}
