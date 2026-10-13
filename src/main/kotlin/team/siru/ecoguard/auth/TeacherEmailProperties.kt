package team.siru.ecoguard.auth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 선생님 웹 화면을 시연·개발하는 팀원용 설정. dataGSM이 인증해 준 이메일이 이 목록에 있으면
 * 학생 계정이어도 교사 권한으로 로그인된다. 기본값은 빈 목록(아무에게도 부여하지 않음)이다.
 */
@ConfigurationProperties(prefix = "teacher-emails")
data class TeacherEmailProperties(
    val emails: List<String> = emptyList(),
)
