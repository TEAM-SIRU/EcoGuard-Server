package team.siru.ecoguard.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private const val BEARER_SCHEME = "bearerAuth"

/** 날짜·시간 필드의 예시. 서버는 시간대 표시(Z) 없이 한국 시간(KST) 그대로 내려 준다. */
private const val LOCAL_DATE_TIME_EXAMPLE = "2026-10-07T07:30:00"

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("EcoGuard API")
                .description("로그인(`/auth/login`, `/auth/refresh`, `/auth/callback`)을 제외한 모든 API는 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.")
                .version("v1"),
        )
        .components(
            Components().addSecuritySchemes(
                BEARER_SCHEME,
                SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
            ),
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
}
