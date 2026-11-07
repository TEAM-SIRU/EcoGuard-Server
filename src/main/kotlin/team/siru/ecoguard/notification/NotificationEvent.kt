package team.siru.ecoguard.notification

import team.siru.ecoguard.user.Role
import java.time.LocalTime

/**
 * 알림을 보내 달라는 요청. 서비스는 이 이벤트만 발행하고, 트랜잭션이 커밋된 뒤에 [NotificationListener] 가 처리한다.
 * 문구는 여기 팩토리에 모아 둔다. 푸시는 잠금화면에 보이므로 반려 사유 같은 상세 내용은 넣지 않는다.
 */
data class NotificationEvent(
    val userIds: List<Long>,
    val type: NotificationType,
    val title: String,
    val body: String,
    val refId: Long? = null,
    /** 지정하면 [userIds] 에 더해 이 역할의 모든 사용자에게 보낸다(예: 교사 전원). */
    val audience: Role? = null,
) {
    companion object {
        private const val NOTICE_TITLE_MAX = 100

        fun verificationApproved(studentId: Long, verificationId: Long) = NotificationEvent(
            listOf(studentId), NotificationType.VERIFICATION_APPROVED,
            "청소 인증 승인", "청소 인증이 승인되어 봉사시간이 적립됐어요.", verificationId,
        )

        fun verificationManualReview(studentId: Long, verificationId: Long) = NotificationEvent(
            listOf(studentId), NotificationType.VERIFICATION_MANUAL_REVIEW,
            "선생님이 확인 중이에요", "제출한 청소 인증을 선생님이 직접 확인하고 있어요.", verificationId,
        )

        fun verificationRejected(studentId: Long, verificationId: Long) = NotificationEvent(
            listOf(studentId), NotificationType.VERIFICATION_REJECTED,
            "청소 인증 반려", "청소 인증이 반려됐어요. 앱에서 사유를 확인해 주세요.", verificationId,
        )

        fun appealApproved(studentId: Long, appealId: Long) = NotificationEvent(
            listOf(studentId), NotificationType.APPEAL_APPROVED,
            "이의신청 승인", "이의신청이 승인됐어요.", appealId,
        )

        fun appealRejected(studentId: Long, appealId: Long) = NotificationEvent(
            listOf(studentId), NotificationType.APPEAL_REJECTED,
            "이의신청 반려", "이의신청이 반려됐어요. 앱에서 답변을 확인해 주세요.", appealId,
        )

        fun areaAssigned(studentIds: List<Long>, areaId: Long, areaName: String) = NotificationEvent(
            studentIds, NotificationType.AREA_ASSIGNED,
            "청소 구역 배정", "${areaName}에 배정됐어요.", areaId,
        )

        fun noticeCreated(studentIds: List<Long>, noticeId: Long, title: String) = NotificationEvent(
            studentIds, NotificationType.NOTICE_CREATED,
            "새 공지", title.take(NOTICE_TITLE_MAX), noticeId,
        )

        fun recruitmentCreated(studentIds: List<Long>, recruitmentId: Long, grade: Int, classNo: Int) = NotificationEvent(
            studentIds, NotificationType.RECRUITMENT_CREATED,
            "청소 모집 시작", "${grade}학년 ${classNo}반 청소 모집이 시작됐어요.", recruitmentId,
        )

        /** 청소 인증 시작 직전 안내. 끝나는 시각을 알려 준다. */
        fun cleaningStartReminder(studentIds: List<Long>, areaId: Long, areaName: String, endTime: LocalTime) = NotificationEvent(
            studentIds, NotificationType.CLEANING_REMINDER,
            "오늘 청소 인증 시간이에요", "${areaName} 청소 후 ${endTime}까지 사진을 인증해 주세요.", areaId,
        )

        /** 마감 직전 안내. 아직 제출하지 않은 학생에게만 간다. */
        fun cleaningDeadlineReminder(studentIds: List<Long>, areaId: Long, areaName: String, endTime: LocalTime) = NotificationEvent(
            studentIds, NotificationType.CLEANING_REMINDER,
            "청소 인증 마감이 다가와요", "${endTime}까지 인증하지 않으면 오늘은 인증할 수 없어요. (${areaName})", areaId,
        )

        /** 교사 전원에게: 수동 검토가 필요한 인증이 생겼다. */
        fun manualReviewPending(verificationId: Long, studentName: String, areaName: String) = NotificationEvent(
            emptyList(), NotificationType.MANUAL_REVIEW_PENDING,
            "검토할 인증이 있어요", "$studentName · ${areaName} 인증을 확인해 주세요.", verificationId, Role.TEACHER,
        )

        /** 교사 전원에게: 시간 초과 등으로 수동 검토로 넘어간 인증이 여러 건 생겼다. */
        fun manualReviewPendingBatch(count: Int) = NotificationEvent(
            emptyList(), NotificationType.MANUAL_REVIEW_PENDING,
            "검토할 인증이 있어요", "수동 검토를 기다리는 인증이 ${count}건 늘었어요.", null, Role.TEACHER,
        )

        /** 교사 전원에게: 학생이 이의신청을 냈다. */
        fun appealReceived(appealId: Long, studentName: String) = NotificationEvent(
            emptyList(), NotificationType.APPEAL_RECEIVED,
            "새 이의신청이 있어요", "${studentName} 학생이 이의신청을 보냈어요.", appealId, Role.TEACHER,
        )

        fun areaUnassigned(studentId: Long, areaId: Long, areaName: String) = NotificationEvent(
            listOf(studentId), NotificationType.AREA_UNASSIGNED,
            "청소 구역 배정 해제", "${areaName} 배정이 해제됐어요.", areaId,
        )
    }
}
