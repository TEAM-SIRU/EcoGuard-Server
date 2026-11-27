package team.siru.ecoguard.notification

import io.swagger.v3.oas.annotations.media.Schema

/** 알림 종류. 앱은 이 값과 `refId` 로 이동할 화면을 정하고, [category] 로 수신 설정을 확인한다. */
@Schema(description = "알림 종류. 앱은 이 값과 refId 로 눌렀을 때 이동할 화면을 정한다. 값별 의미·refId 는 10. 알림 태그 설명의 표 참고")
enum class NotificationType(val category: NotificationCategory) {
    VERIFICATION_APPROVED(NotificationCategory.VERIFICATION),
    VERIFICATION_MANUAL_REVIEW(NotificationCategory.VERIFICATION),
    VERIFICATION_REJECTED(NotificationCategory.VERIFICATION),
    APPEAL_APPROVED(NotificationCategory.APPEAL),
    APPEAL_REJECTED(NotificationCategory.APPEAL),
    AREA_ASSIGNED(NotificationCategory.AREA),
    AREA_UNASSIGNED(NotificationCategory.AREA),
    NOTICE_CREATED(NotificationCategory.NOTICE),
    RECRUITMENT_CREATED(NotificationCategory.RECRUITMENT),
    CLEANING_REMINDER(NotificationCategory.REMINDER),
    MANUAL_REVIEW_PENDING(NotificationCategory.REVIEW_QUEUE),
    APPEAL_RECEIVED(NotificationCategory.APPEAL_INBOX),
}
