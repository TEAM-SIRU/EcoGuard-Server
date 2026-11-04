package team.siru.ecoguard.notification

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

        fun areaUnassigned(studentId: Long, areaId: Long, areaName: String) = NotificationEvent(
            listOf(studentId), NotificationType.AREA_UNASSIGNED,
            "청소 구역 배정 해제", "${areaName} 배정이 해제됐어요.", areaId,
        )
    }
}
