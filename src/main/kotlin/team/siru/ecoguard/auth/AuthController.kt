package team.siru.ecoguard.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
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
import team.siru.ecoguard.common.exception.ApiErrors
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.SecurityUtils
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val APP_CALLBACK_URI = "ecoguard://auth/callback"

@Tag(
    name = "1. 인증 (Auth)",
    description = "dataGSM OAuth 로그인, 토큰 재발급, 로그아웃. 전체 흐름은 문서 맨 위 **1. 로그인 흐름** 을 먼저 읽어 주세요. " +
        "서버에는 `/auth/authorize` 가 없고 PKCE 를 쓰지 않습니다.",
)
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {

    /**
     * dataGSM 이 인가 후 redirect 하는 주소. 받은 값을 그대로 앱 callback(`ecoguard://auth/callback`)으로 다시 302 redirect 한다.
     * 성공이면 code/state, 실패면 error/error_description 이 온다. 값은 바꾸지 않고 쿼리 파라미터로만 안전하게 인코딩한다.
     */
    @Operation(
        summary = "[앱이 호출하지 않음] dataGSM 콜백 → 앱 딥링크로 전달",
        description = """
dataGSM 인가 URL 의 `redirect_uri` 로 등록된 주소입니다. **앱이 직접 호출하지 않습니다.**

dataGSM 이 로그인 후 브라우저를 이 주소로 보내면, 서버는 받은 쿼리 값을 **바꾸지 않고** 그대로
`ecoguard://auth/callback` 으로 `302` 리다이렉트합니다. 앱은 이 딥링크를 받아 `code` 를 꺼낸 뒤 `POST /api/v1/auth/login` 을 호출합니다.

| 들어온 요청 | 앱이 받는 딥링크 |
|---|---|
| `?code=abc&state=xyz` | `ecoguard://auth/callback?code=abc&state=xyz` |
| `?error=access_denied&error_description=...` | `ecoguard://auth/callback?error=access_denied&error_description=...` |
| (쿼리 없음) | `ecoguard://auth/callback` |

- 값에 공백이 있으면 `%20` 으로 인코딩됩니다(`+` 아님).
- 앱은 딥링크의 `state` 가 인가 요청 때 만든 값과 같은지 반드시 확인하세요.
""",
        responses = [
            ApiResponse(
                responseCode = "302",
                description = "앱 딥링크로 리다이렉트 (바디 없음)",
                headers = [
                    Header(
                        name = "Location",
                        description = "`ecoguard://auth/callback?code={code}&state={state}` 또는 `ecoguard://auth/callback?error=...&error_description=...`",
                    ),
                ],
            ),
        ],
    )
    @SecurityRequirements
    @GetMapping("/callback")
    fun callback(
        @Parameter(description = "dataGSM 이 발급한 인가 코드(성공 시)", example = "c0ffee12-3456-7890-abcd-ef0123456789")
        @RequestParam(required = false) code: String?,
        @Parameter(description = "앱이 인가 요청 때 보낸 state 값 그대로", example = "8f14e45f-ceea-467a-9575-1f7c3b0e5a2d")
        @RequestParam(required = false) state: String?,
        @Parameter(description = "실패 시 OAuth 에러 코드 (예: access_denied)")
        @RequestParam(required = false) error: String?,
        @Parameter(description = "실패 시 에러 설명")
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

    @Operation(
        summary = "로그인 (인가 코드 → 토큰 발급)",
        description = """
**[공통]** 토큰 없이 호출합니다. 딥링크 `ecoguard://auth/callback?code=...` 로 받은 `code` 를 `authCode` 에 넣어 보냅니다.

서버가 dataGSM 에 인가 코드를 넘겨 토큰을 교환하고 사용자 정보(이름, 학년/반/번호, 학생·교사 여부)를 가져와
가입 또는 정보 갱신을 한 뒤 EcoGuard 토큰을 발급합니다. 처음 로그인하면 자동으로 가입됩니다.

**요청 예**
```json
{ "authCode": "c0ffee12-3456-7890-abcd-ef0123456789" }
```
- **앱은 `redirectUri` 를 보내지 않습니다.** 생략하면 서버에 등록된 콜백 주소(`/api/v1/auth/callback`)로 교환합니다.
  `redirectUri` 는 서버 허용 목록에 있는 다른 주소(교사 웹 등)로 인가를 받은 경우에만 그 주소를 그대로 보냅니다.
- PKCE 를 쓰지 않으므로 `code_verifier` 같은 필드는 없습니다.
- 인가 코드는 1회용입니다. 실패하면 같은 코드로 재시도하지 말고 인가 URL 부터 다시 여세요.

**`401 OAUTH_FAILED` 의 `message` 별 원인**

| message | 원인 / 조치 |
|---|---|
| `OAuth 인증에 실패했습니다.` | 인가 코드가 틀렸거나 만료·이미 사용됨, 또는 인가 요청의 `redirect_uri` 가 서버 등록 값과 다름 → 인가 URL 값을 확인하고 처음부터 다시 |
| `허용되지 않은 redirectUri 입니다.` | 바디에 허용되지 않은 `redirectUri` 를 보냄 → 앱은 `redirectUri` 를 빼고 보내기 |
| `dataGSM에서 접근을 거부했습니다. 선생님 계정이라면 관리자 승인 대기 중일 수 있습니다.` | dataGSM 이 403 을 줌(승인 대기 교사 계정 등) |
| `학생/교사 정보가 연결되지 않은 계정입니다.` | dataGSM 계정에 학생·교사 정보가 연결되지 않음 |
""",
    )
    @SecurityRequirements
    @PostMapping("/login")
    @ApiErrors(ErrorCode.OAUTH_FAILED)
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        return ResponseEntity.ok(authService.login(request.authCode, request.redirectUri))
    }

    /** 리프레시 토큰으로 새 토큰 쌍을 발급한다. 쓸 때마다 갱신되므로 계속 사용하는 동안 로그인이 유지된다. */
    @Operation(
        summary = "토큰 재발급 (refresh token 회전)",
        description = """
**[공통]** 토큰 없이 호출합니다(`Authorization` 헤더 불필요). 다른 API 가 `401` 을 줬을 때 한 번 호출하세요.

- 성공하면 **새 `accessToken` 과 새 `refreshToken`** 을 줍니다. 둘 다 교체해 저장하세요.
  refresh token 의 유효기간이 이때 다시 시작되므로, 앱을 계속 쓰는 동안은 로그인이 유지됩니다.
- 역할(`role`)은 토큰이 아니라 DB 기준으로 다시 정해집니다.
- `401`(바디 없음): refresh token 이 만료·위조됐거나, access token 을 잘못 넣었거나, 로그아웃/탈퇴 이후의 토큰입니다 → 저장된 토큰을 지우고 로그인 화면으로.
""",
    )
    @SecurityRequirements
    @PostMapping("/refresh")
    @ApiErrors(ErrorCode.UNAUTHORIZED)
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<TokenResponse> {
        return ResponseEntity.ok(authService.refresh(request.refreshToken))
    }

    @Operation(
        summary = "로그아웃 (모든 기기)",
        description = """
**[공통]** 이 사용자에게 지금까지 발급된 **모든** access/refresh 토큰을 무효로 만들고, 등록된 푸시(FCM) 토큰도 모두 지웁니다.
기기 단위가 아니므로 다른 기기의 로그인도 함께 풀립니다.

호출 후 앱은 저장된 토큰을 지우고 로그인 화면으로 이동하면 됩니다. 응답 바디는 없습니다(`200`).
""",
    )
    @PostMapping("/logout")
    fun logout(): ResponseEntity<Void> {
        authService.logout(SecurityUtils.currentUserId())
        return ResponseEntity.ok().build()
    }
}
