package team.siru.ecoguard.auth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 앱 스토어 심사용 데모 계정 설정. 심사자는 학교(GSM) 계정이 없어 OAuth 로그인을 할 수 없으므로,
 * 이 인가 코드로 로그인하면 고정된 데모 학생 계정으로 들어온다. 기본값은 비활성이다.
 */
@ConfigurationProperties(prefix = "demo-account")
data class DemoAccountProperties(
    val enabled: Boolean = false,
    /** 심사자에게만 전달하는 비밀 코드. 추측할 수 없도록 충분히 길어야 한다. */
    val authCode: String = "",
) {
    init {
        check(!enabled || authCode.length >= MIN_AUTH_CODE_LENGTH) {
            "demo-account.auth-code 는 ${MIN_AUTH_CODE_LENGTH}자 이상이어야 데모 계정을 켤 수 있습니다."
        }
    }

    companion object {
        const val MIN_AUTH_CODE_LENGTH = 16
    }
}
