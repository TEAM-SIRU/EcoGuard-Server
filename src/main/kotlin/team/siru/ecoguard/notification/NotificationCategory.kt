package team.siru.ecoguard.notification

/** 학생이 켜고 끌 수 있는 알림 묶음. 앱의 알림 설정 화면에 이 단위로 보여 준다. */
enum class NotificationCategory(val label: String) {
    VERIFICATION("청소 인증 결과"),
    APPEAL("이의신청 결과"),
    AREA("청소 구역 배정"),
    NOTICE("공지"),
    RECRUITMENT("청소 모집"),
    REMINDER("청소 인증 리마인더"),
}
