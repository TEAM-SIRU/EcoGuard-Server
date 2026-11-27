package team.siru.ecoguard.common.config

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import team.siru.ecoguard.auth.GsmOAuthProperties
import team.siru.ecoguard.common.exception.ErrorResponse
import team.siru.ecoguard.common.security.JwtProperties

const val BEARER_SCHEME = "bearerAuth"

/** 날짜·시간 필드의 예시. 서버는 시간대 표시(Z) 없이 한국 시간(KST) 그대로 내려 준다. */
private const val LOCAL_DATE_TIME_EXAMPLE = "2026-10-07T07:30:00"

private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_DAY = 86400

@Configuration
class OpenApiConfig(
    private val gsmOAuthProperties: GsmOAuthProperties,
    private val jwtProperties: JwtProperties,
) {

    init {
        // enum 을 별도 스키마로 빼서, 필드마다 설명이 있어도 enum 값별 의미(클래스의 @Schema 설명)가 함께 보이게 한다.
        ModelResolver.enumsAsRef = true
    }

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("EcoGuard API")
                .description(apiGuide())
                .version("v1"),
        )
        .components(
            Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("`POST /api/v1/auth/login` 응답의 `accessToken`. `Bearer ` 접두사 없이 토큰만 입력하세요."),
                )
                .also { components ->
                    ModelConverters.getInstance(true).read(ErrorResponse::class.java).forEach(components::addSchemas)
                },
        )
        .addSecurityItem(SecurityRequirement().addList(BEARER_SCHEME))

    /** springdoc 이 날짜-시간 예시에 붙이는 `Z`(UTC 표시)를 실제 응답 형식에 맞게 바꾼다. */
    @Bean
    fun dateTimeExamples(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        openApi.components?.schemas?.values?.forEach { schema ->
            schema.properties?.values?.forEach { property ->
                if (property.format == "date-time" && property.example == null) {
                    property.example = LOCAL_DATE_TIME_EXAMPLE
                }
            }
        }
    }

    /** 태그를 이름 앞 번호(1. 인증, 2. 사용자 …) 순서로 정렬해 Swagger UI 에 그 순서로 보이게 한다. */
    @Bean
    fun tagOrder(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        openApi.tags = openApi.tags?.sortedBy { it.name.substringBefore(".").toIntOrNull() ?: Int.MAX_VALUE }
    }

    /**
     * Swagger 첫 화면에 보이는 공통 안내. 로그인 흐름의 실제 값(client_id, redirect_uri, 토큰 유효기간)은
     * 이 서버의 설정에서 읽어 오므로 문서와 서버 동작이 어긋나지 않는다.
     */
    private fun apiGuide(): String {
        val clientId = gsmOAuthProperties.clientId.ifBlank { "{CLIENT_ID}" }
        val redirectUri = gsmOAuthProperties.redirectUri.ifBlank { "https://{서버 주소}/api/v1/auth/callback" }
        val authorizeUrl = "${gsmOAuthProperties.authorizationBaseUrl}/v1/oauth/authorize"
        val accessValidity = describeDuration(jwtProperties.accessTokenValiditySeconds)
        val refreshValidity = describeDuration(jwtProperties.refreshTokenValiditySeconds)

        return """
청소 구역 배정, 청소 인증 사진 제출, AI/교사 검수, 이의신청, 봉사시간 조회를 위한 API입니다.
모든 경로는 `/api/v1` 로 시작하고, 요청·응답 바디는 따로 적지 않았으면 JSON(UTF-8)입니다.

---

## 1. 로그인 흐름 (dataGSM OAuth)

> ⚠️ **서버에는 `/auth/authorize` API가 없습니다.** 앱이 dataGSM 인가 페이지를 직접 열고, 받은 인가 코드만 서버에 넘깁니다.
> ⚠️ **PKCE(`code_challenge`, `code_verifier`)를 쓰지 않습니다.** 서버가 토큰 교환 때 `code_verifier` 를 보내지 않으므로, 인가 요청에 `code_challenge` 를 붙이면 토큰 교환이 실패합니다.

| 단계 | 누가 | 무엇을 |
|---|---|---|
| 1 | 앱 | 시스템 브라우저(Android Custom Tabs / iOS `ASWebAuthenticationSession`)로 아래 **인가 URL** 을 연다 |
| 2 | 사용자 | dataGSM 에서 로그인·동의 |
| 3 | dataGSM → 서버 | `GET /api/v1/auth/callback?code=...&state=...` 로 리다이렉트 |
| 4 | 서버 → 앱 | 받은 값을 그대로 `302 Location: ecoguard://auth/callback?code=...&state=...` 로 넘김 |
| 5 | 앱 | `state` 가 1단계에서 만든 값과 같은지 확인하고 `code` 를 꺼냄 |
| 6 | 앱 → 서버 | `POST /api/v1/auth/login` `{"authCode": "<code>"}` → `accessToken`, `refreshToken`, 사용자 정보 |

**1단계 인가 URL** (쿼리 값은 URL 인코딩)
```
GET $authorizeUrl
    ?client_id=$clientId
    &redirect_uri=$redirectUri
    &response_type=code
    &state={앱이 만든 임의 값, UUID 권장}
```
- `redirect_uri` 는 **앱 스킴(`ecoguard://...`)이 아니라 서버 콜백 주소** 이며, 위 값과 글자 하나까지 같아야 합니다.
- `state` 는 dataGSM 이 인코딩 없이 붙여 돌려주므로 URL 에 안전한 값(UUID 등)만 쓰세요.
- 앱은 `ecoguard://auth/callback` 딥링크(Android intent-filter: scheme `ecoguard`, host `auth`, path `/callback`)를 받을 수 있어야 합니다.
- 콜백에 `error`(와 `error_description`)가 있으면 로그인 실패/취소입니다. 이때는 `code` 가 없습니다.

**6단계 로그인 요청**
- 앱은 **`redirectUri` 를 보내지 않습니다**(생략 또는 `null`). 생략하면 서버가 위 `redirect_uri` 로 토큰을 교환합니다.
  `ecoguard://auth/callback` 같은 다른 값을 보내면 `401 OAUTH_FAILED`("허용되지 않은 redirectUri 입니다.")가 납니다.
- 인가 코드는 한 번만 쓸 수 있습니다. 로그인이 실패하면 같은 코드로 재시도하지 말고 1단계부터 다시 하세요.

---

## 2. 토큰

| 토큰 | 유효기간 | 쓰는 곳 |
|---|---|---|
| `accessToken` | $accessValidity | 모든 API 의 `Authorization: Bearer {accessToken}` 헤더 |
| `refreshToken` | $refreshValidity | `POST /api/v1/auth/refresh` 바디에만 |

- 토큰이 없거나 만료·위조·로그아웃된 토큰이면 **`401` 이 바디 없이** 옵니다.
- 권장 처리: API 가 `401` → `POST /auth/refresh` 를 **한 번** 호출 → 성공하면 새 토큰 쌍을 저장하고 원래 요청 재시도 → refresh 도 `401` 이면 저장된 토큰을 지우고 로그인 화면으로.
- refresh 는 호출할 때마다 **새 `refreshToken`** 을 주므로(유효기간이 다시 시작) 반드시 둘 다 교체해 저장하세요. 앱을 계속 쓰는 동안은 로그인이 풀리지 않습니다.
- 로그아웃(`POST /auth/logout`)하면 **그 사용자의 모든 기기** 토큰이 무효가 되고 푸시 토큰도 지워집니다.
- 역할(`STUDENT` / `TEACHER`)은 로그인 응답의 `user.role` 로 구분합니다. 역할에 맞지 않는 API 를 부르면 `403 FORBIDDEN` 입니다.
  각 API 설명 맨 앞의 **[학생]**, **[교사]**, **[공통]** 표시가 호출 가능한 역할입니다.

---

## 3. 에러 응답

`401` 을 제외한 에러는 모두 아래 형식입니다. 화면 분기는 **`code`** 로 하고, `message` 는 사용자에게 그대로 보여 줘도 되는 한국어 문구입니다.
```json
{ "code": "ALREADY_SUBMITTED_TODAY", "message": "오늘 이미 제출했습니다.", "submittedAt": "2026-10-07T07:31:12" }
```
- `submittedAt` 은 `ALREADY_SUBMITTED_TODAY` 일 때만 있고, 나머지는 필드 자체가 없습니다.
- `VALIDATION_ERROR` 의 `message` 는 `"필드명: 사유"` 형태(여러 개면 `, ` 로 이어짐)일 수 있습니다.
- 각 API 의 응답 목록에 그 API 가 돌려줄 수 있는 `code` 를 상태 코드별로 모두 적어 두었습니다(Examples 드롭다운에서 실제 바디 확인).
- 공통: 없는 경로 `404 NOT_FOUND`, 잘못된 메서드 `405 METHOD_NOT_ALLOWED`, 잘못된 Content-Type `415 UNSUPPORTED_MEDIA_TYPE`,
  JSON 형식 오류·타입 불일치 `400 VALIDATION_ERROR`, 서버 오류 `500 INTERNAL_SERVER_ERROR`.

---

## 4. 값 형식

| 타입 | 형식 | 예 |
|---|---|---|
| 날짜-시간 | `yyyy-MM-ddTHH:mm:ss` (한국 시간, **시간대 표시 없음**, `Z` 없음) | `2026-10-07T07:30:00` |
| 날짜 | `yyyy-MM-dd` | `2026-10-07` |
| 시각 | 응답은 `HH:mm:ss`, 요청은 `HH:mm` 또는 `HH:mm:ss` | `07:20:00` |
| ID | 64비트 정수 | `42` |

- 날짜-시간 응답 값을 UTC 로 해석하지 마세요(9시간 어긋납니다). 그대로 KST 로 표시하면 됩니다.
- 값이 없는 필드는 `null` 로 옵니다(스키마에서 `nullable` 표시). 목록은 비어 있으면 `[]` 입니다.

## 5. 사진 URL

- `photoUrl`, `photoUrls` 는 `/files/verifications/{uuid}.jpg` 처럼 **서버 기준 상대 경로** 입니다. 서버 주소를 앞에 붙여서 쓰세요.
- `/files/**` 는 인증 헤더 없이 열립니다.
- 업로드는 JPG·PNG 만 받으며 사진 한 장 10MB, 요청 전체 32MB 까지입니다. 넘으면 `400 INVALID_IMAGE`.

## 6. Swagger 에서 직접 호출해 보기

1. `POST /api/v1/auth/login` 을 실행해 `accessToken` 을 복사합니다(실제 인가 코드가 필요합니다).
2. 오른쪽 위 **Authorize** 를 눌러 토큰만 붙여 넣습니다(`Bearer ` 는 자동으로 붙습니다).
        """.trimIndent()
    }

    private fun describeDuration(seconds: Long): String = when {
        seconds % SECONDS_PER_DAY == 0L -> "${seconds / SECONDS_PER_DAY}일 (${seconds}초)"
        seconds % SECONDS_PER_HOUR == 0L -> "${seconds / SECONDS_PER_HOUR}시간 (${seconds}초)"
        else -> "${seconds}초"
    }
}
