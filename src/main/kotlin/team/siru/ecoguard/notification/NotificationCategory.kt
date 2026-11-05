package team.siru.ecoguard.notification

import team.siru.ecoguard.user.Role

/** 켜고 끌 수 있는 알림 묶음. 앱·웹의 알림 설정 화면에 이 단위로 보여 주며, [audience] 역할의 사용자에게만 보인다. */
enum class NotificationCategory(val label: String, val audience: Role) {
    // 학생
    VERIFICATION("청소 인증 결과", Role.STUDENT),
    APPEAL("이의신청 결과", Role.STUDENT),
    AREA("청소 구역 배정", Role.STUDENT),
    NOTICE("공지", Role.STUDENT),
    RECRUITMENT("청소 모집", Role.STUDENT),
    REMINDER("청소 인증 리마인더", Role.STUDENT),

    // 교사
    REVIEW_QUEUE("검토 대기 인증", Role.TEACHER),
    APPEAL_INBOX("이의신청 접수", Role.TEACHER),
}
