package team.siru.ecoguard.auth

import org.springframework.stereotype.Component
import team.siru.ecoguard.user.Role

/**
 * dataGSM 로그인으로 확인된 사용자의 이메일이 허용 목록에 있으면 교사 권한으로 바꾼다.
 * 데모 계정에는 적용하지 않는다(교사 권한 절대 불가). 역할은 로그인 때마다 다시 정해지므로
 * 목록에서 빼면 다음 로그인부터 원래 역할로 돌아간다.
 */
@Component
class TeacherEmailPromoter(
    properties: TeacherEmailProperties,
) {

    private val emails: Set<String> = properties.emails
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    fun promote(userInfo: GsmUserInfo): GsmUserInfo {
        if (userInfo.role == Role.TEACHER || userInfo.email.trim().lowercase() !in emails) return userInfo
        return userInfo.copy(role = Role.TEACHER, studentNumber = null, grade = null, classNo = null)
    }
}
