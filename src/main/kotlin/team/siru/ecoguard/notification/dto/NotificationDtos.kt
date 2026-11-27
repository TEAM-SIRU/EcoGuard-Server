package team.siru.ecoguard.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import team.siru.ecoguard.notification.DevicePlatform
import team.siru.ecoguard.notification.Notification
import team.siru.ecoguard.notification.NotificationCategory
import team.siru.ecoguard.notification.NotificationType
import java.time.LocalDateTime

@Schema(description = "푸시 토큰 등록 요청")
data class RegisterTokenRequest(
    @field:Schema(description = "FCM 등록 토큰 (최대 512자)", example = "fGx1c2VyLXRva2VuOkFQQTkxYkg...", maxLength = 512, requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotBlank
    @field:Size(max = 512)
    val token: String?,

    @field:Schema(description = "기기 플랫폼", example = "ANDROID", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull
    val platform: DevicePlatform?,
)

@Schema(description = "푸시 토큰 삭제 요청")
data class RemoveTokenRequest(
    @field:Schema(description = "삭제할 FCM 등록 토큰", example = "fGx1c2VyLXRva2VuOkFQQTkxYkg...", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotBlank
    val token: String?,
)

@Schema(description = "알림함 항목")
data class NotificationResponse(
    @field:Schema(description = "알림 ID. 읽음 처리에 쓴다", example = "230")
    val id: Long,
    @field:Schema(description = "알림 종류. 눌렀을 때 이동할 화면을 정한다 (태그 설명의 표 참고)", example = "VERIFICATION_APPROVED")
    val type: NotificationType,
    @field:Schema(description = "제목", example = "청소 인증 승인")
    val title: String,
    @field:Schema(description = "본문", example = "청소 인증이 승인되어 봉사시간이 적립됐어요.")
    val body: String,
    @field:Schema(description = "이동할 대상의 ID (type 에 따라 verificationId / appealId / areaId / noticeId / recruitmentId). 없을 수 있음", example = "101", nullable = true)
    val refId: Long?,
    @field:Schema(description = "읽었는지", example = "false")
    val isRead: Boolean,
    @field:Schema(description = "생성 시각 (KST)")
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(n: Notification) = NotificationResponse(n.id, n.type, n.title, n.body, n.refId, n.readAt != null, n.createdAt)
    }
}

@Schema(description = "알림함")
data class NotificationListResponse(
    @field:Schema(description = "읽지 않은 알림 전체 개수 (limit 과 무관, 배지 표시용)", example = "3")
    val unreadCount: Long,
    @field:Schema(description = "최신순 알림 목록. 없으면 []")
    val notifications: List<NotificationResponse>,
)

@Schema(description = "알림 종류별 푸시 수신 설정")
data class NotificationSettingResponse(
    @field:Schema(description = "알림 카테고리", example = "VERIFICATION")
    val category: NotificationCategory,
    /** 앱 설정 화면에 그대로 보여 줄 이름 */
    @field:Schema(description = "설정 화면에 그대로 보여 줄 이름", example = "청소 인증 결과")
    val label: String,
    @field:Schema(description = "푸시 수신 여부 (한 번도 바꾸지 않았으면 true)", example = "true")
    val enabled: Boolean,
)

@Schema(description = "푸시 수신 설정 변경 요청")
data class UpdateNotificationSettingRequest(
    @field:Schema(description = "바꿀 카테고리. 내 역할에 해당하는 것만 가능", example = "REMINDER", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull
    val category: NotificationCategory?,

    @field:Schema(description = "true: 푸시 받기, false: 끄기 (알림함에는 계속 쌓임)", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull
    val enabled: Boolean?,
)
