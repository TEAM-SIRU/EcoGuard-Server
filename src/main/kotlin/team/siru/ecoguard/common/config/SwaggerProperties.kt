package team.siru.ecoguard.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * API 문서(Swagger UI, `/v3/api-docs`) 설정. 기본은 꺼져 있고, 켜면 아래 아이디·비밀번호로만 볼 수 있다.
 * 문서에는 모든 API 구조가 들어 있으므로 비밀번호 없이 공개하지 않는다.
 */
@ConfigurationProperties(prefix = "swagger")
data class SwaggerProperties(
    val enabled: Boolean = false,
    val username: String = "",
    /** 문서를 보는 팀원에게만 전달하는 비밀번호. 추측할 수 없도록 충분히 길어야 한다. */
    val password: String = "",
) {
    init {
        check(!enabled || username.isNotBlank()) {
            "swagger.username 이 비어 있으면 API 문서를 켤 수 없습니다."
        }
        check(!enabled || password.length >= MIN_PASSWORD_LENGTH) {
            "swagger.password 는 ${MIN_PASSWORD_LENGTH}자 이상이어야 API 문서를 켤 수 있습니다."
        }
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 12
    }
}
