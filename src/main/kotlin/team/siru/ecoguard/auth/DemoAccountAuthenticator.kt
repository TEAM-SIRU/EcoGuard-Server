package team.siru.ecoguard.auth

import org.springframework.stereotype.Component
import team.siru.ecoguard.user.Role
import java.security.MessageDigest

/**
 * 데모 인가 코드가 맞으면 고정된 데모 학생 정보를 돌려준다. 역할은 항상 STUDENT이며
 * 교사 권한은 절대 부여하지 않는다.
 */
@Component
class DemoAccountAuthenticator(
    private val properties: DemoAccountProperties,
) {

    fun authenticate(authCode: String): GsmUserInfo? {
        if (!properties.enabled) return null
        // 코드 길이/내용을 응답 시간으로 유추하지 못하도록 상수 시간으로 비교한다.
        val matches = MessageDigest.isEqual(authCode.toByteArray(), properties.authCode.toByteArray())
        return if (matches) DEMO_USER else null
    }

    companion object {
        /** 실제 GSM 계정 ID와 겹치지 않도록 음수를 쓴다. */
        private val DEMO_USER = GsmUserInfo(
            gsmAccountId = -1L,
            email = "review-demo@ecoguard.invalid",
            name = "심사용 계정",
            role = Role.STUDENT,
            studentNumber = null,
            grade = null,
            classNo = null,
        )
    }
}
