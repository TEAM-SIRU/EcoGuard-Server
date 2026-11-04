package team.siru.ecoguard.notification.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import team.siru.ecoguard.notification.DevicePlatform
import team.siru.ecoguard.notification.Notification
import team.siru.ecoguard.notification.NotificationType
import java.time.LocalDateTime

data class RegisterTokenRequest(
    @field:NotBlank
    @field:Size(max = 512)
    val token: String?,

    @field:NotNull
    val platform: DevicePlatform?,
)

data class RemoveTokenRequest(
    @field:NotBlank
    val token: String?,
)

data class NotificationResponse(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    val refId: Long?,
    val isRead: Boolean,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(n: Notification) = NotificationResponse(n.id, n.type, n.title, n.body, n.refId, n.readAt != null, n.createdAt)
    }
}

data class NotificationListResponse(
    val unreadCount: Long,
    val notifications: List<NotificationResponse>,
)
