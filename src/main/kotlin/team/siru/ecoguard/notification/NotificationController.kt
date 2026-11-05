package team.siru.ecoguard.notification

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.siru.ecoguard.common.exception.ApiErrors
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.SecurityUtils
import team.siru.ecoguard.notification.dto.NotificationListResponse
import team.siru.ecoguard.notification.dto.NotificationResponse
import team.siru.ecoguard.notification.dto.RegisterTokenRequest
import team.siru.ecoguard.notification.dto.RemoveTokenRequest
import team.siru.ecoguard.notification.dto.NotificationSettingResponse
import team.siru.ecoguard.notification.dto.UpdateNotificationSettingRequest
import org.springframework.web.bind.annotation.PutMapping

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService,
) {

    /** 앱을 켜거나 로그인할 때마다 호출한다. 이미 등록된 토큰이면 현재 로그인한 계정의 것으로 갱신한다. */
    @PostMapping("/tokens")
    fun registerToken(@Valid @RequestBody request: RegisterTokenRequest): ResponseEntity<Void> {
        notificationService.registerToken(SecurityUtils.currentUserId(), request.token!!, request.platform!!)
        return ResponseEntity.ok().build()
    }

    /** 로그아웃 직전에 호출해 이 기기로 알림이 오지 않게 한다. */
    @DeleteMapping("/tokens")
    @ApiErrors(success = 204)
    fun removeToken(@Valid @RequestBody request: RemoveTokenRequest): ResponseEntity<Void> {
        notificationService.removeToken(SecurityUtils.currentUserId(), request.token!!)
        return ResponseEntity.noContent().build()
    }

    /** 최신순 알림 목록과 읽지 않은 알림 수. */
    @GetMapping
    fun getList(@RequestParam(defaultValue = "50") limit: Int): ResponseEntity<NotificationListResponse> {
        val userId = SecurityUtils.currentUserId()
        return ResponseEntity.ok(
            NotificationListResponse(
                unreadCount = notificationService.countUnread(userId),
                notifications = notificationService.getMyNotifications(userId, limit).map(NotificationResponse::from),
            ),
        )
    }

    /** 알림 종류별 푸시 수신 여부. 설정한 적 없는 종류는 켜져 있다. 꺼도 알림함에는 계속 쌓인다. */
    @GetMapping("/settings")
    fun getSettings(): ResponseEntity<List<NotificationSettingResponse>> =
        ResponseEntity.ok(
            notificationService.getSettings(SecurityUtils.currentUserId())
                .map { (category, enabled) -> NotificationSettingResponse(category, category.label, enabled) },
        )

    @PutMapping("/settings")
    fun updateSetting(@Valid @RequestBody request: UpdateNotificationSettingRequest): ResponseEntity<Void> {
        notificationService.updateSetting(SecurityUtils.currentUserId(), request.category!!, request.enabled!!)
        return ResponseEntity.ok().build()
    }

    @PatchMapping("/{notificationId}/read")
    @ApiErrors(ErrorCode.NOTIFICATION_NOT_FOUND)
    fun markRead(@PathVariable notificationId: Long): ResponseEntity<Void> {
        notificationService.markRead(SecurityUtils.currentUserId(), notificationId)
        return ResponseEntity.ok().build()
    }

    @PatchMapping("/read-all")
    fun markAllRead(): ResponseEntity<Void> {
        notificationService.markAllRead(SecurityUtils.currentUserId())
        return ResponseEntity.ok().build()
    }
}
