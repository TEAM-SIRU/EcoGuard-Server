package team.siru.ecoguard.notification

/** 알림 종류. 앱은 이 값과 `refId` 로 이동할 화면을 정한다. */
enum class NotificationType {
    VERIFICATION_APPROVED,
    VERIFICATION_MANUAL_REVIEW,
    VERIFICATION_REJECTED,
    APPEAL_APPROVED,
    APPEAL_REJECTED,
    AREA_ASSIGNED,
    AREA_UNASSIGNED,
    NOTICE_CREATED,
    RECRUITMENT_CREATED,
}
