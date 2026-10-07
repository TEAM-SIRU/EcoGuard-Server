package team.siru.ecoguard.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.user.Role

/**
 * 실제 dataGSM 연동(GsmOAuthRealClient) 전까지, 또는 실 연동 없이 로컬에서
 * 테스트하고 싶을 때 사용하는 목(mock) 구현체. `gsm.oauth.mock=true`일 때만 활성화된다. 기본값은 false 이고,
 * 인가 코드 문자열만으로 교사를 포함해 누구로든 로그인되므로 운영에서는 절대 켜지 않는다.
 *
 * authCode 형식(파이프 구분): "role|gsmAccountId|email|name|studentNumber|grade|classNo"
 * - 학생 예시: "STUDENT|1|10101@gsm.hs.kr|홍길동|1101|1|1"
 * - 교사 예시: "TEACHER|2|teacher@gsm.hs.kr|김선생||"
 * (studentNumber/grade/classNo는 교사인 경우 빈 값으로 둔다)
 * 형식에 맞지 않으면 OAUTH_FAILED 로 처리한다.
 */
@Component
@ConditionalOnProperty(prefix = "gsm.oauth", name = ["mock"], havingValue = "true")
class GsmOAuthMockClient : GsmOAuthClient {

    override fun authenticate(authCode: String): GsmUserInfo {
        if (authCode.isBlank()) {
            throw BusinessException(ErrorCode.OAUTH_FAILED)
        }

        val parts = authCode.split("|")
        val role = runCatching { Role.valueOf(parts.getOrNull(0)?.uppercase() ?: "") }
            .getOrElse { throw BusinessException(ErrorCode.OAUTH_FAILED) }
        val gsmAccountId = parts.getOrNull(1)?.toLongOrNull() ?: throw BusinessException(ErrorCode.OAUTH_FAILED)
        val email = parts.getOrNull(2) ?: throw BusinessException(ErrorCode.OAUTH_FAILED)
        val name = parts.getOrNull(3) ?: throw BusinessException(ErrorCode.OAUTH_FAILED)
        val studentNumber = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
        val grade = parts.getOrNull(5)?.toIntOrNull()
        val classNo = parts.getOrNull(6)?.toIntOrNull()

        return GsmUserInfo(gsmAccountId, email, name, role, studentNumber, grade, classNo)
    }
}
