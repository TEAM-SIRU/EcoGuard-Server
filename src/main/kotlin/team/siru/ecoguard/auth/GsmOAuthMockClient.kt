package team.siru.ecoguard.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.user.Role

/**
 * 실제 dataGSM 연동 전까지 사용하는 임시 구현체.
 *
 * authCode 형식: "STUDENT|10101|홍길동|1|2" 또는 "TEACHER|20001|김선생"
 * (role|studentNumber|name|grade|classNo, grade/classNo는 교사인 경우 생략 가능)
 * 형식에 맞지 않으면 OAUTH_FAILED 로 처리한다.
 */
@Component
@ConditionalOnProperty(prefix = "gsm.oauth", name = ["mock"], havingValue = "true", matchIfMissing = true)
class GsmOAuthMockClient : GsmOAuthClient {

    override fun authenticate(authCode: String): GsmUserInfo {
        if (authCode.isBlank()) {
            throw BusinessException(ErrorCode.OAUTH_FAILED)
        }

        val parts = authCode.split("|")
        val role = runCatching { Role.valueOf(parts.getOrNull(0)?.uppercase() ?: "") }
            .getOrElse { throw BusinessException(ErrorCode.OAUTH_FAILED) }
        val studentNumber = parts.getOrNull(1) ?: throw BusinessException(ErrorCode.OAUTH_FAILED)
        val name = parts.getOrNull(2) ?: throw BusinessException(ErrorCode.OAUTH_FAILED)
        val grade = parts.getOrNull(3)?.toIntOrNull()
        val classNo = parts.getOrNull(4)?.toIntOrNull()

        return GsmUserInfo(studentNumber, name, role, grade, classNo)
    }
}
