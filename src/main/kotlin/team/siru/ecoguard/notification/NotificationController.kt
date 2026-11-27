package team.siru.ecoguard.notification

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.siru.ecoguard.common.exception.ApiErrors
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.security.SecurityUtils
import team.siru.ecoguard.notification.dto.NotificationListResponse
import team.siru.ecoguard.notification.dto.NotificationResponse
import team.siru.ecoguard.notification.dto.NotificationSettingResponse
import team.siru.ecoguard.notification.dto.RegisterTokenRequest
import team.siru.ecoguard.notification.dto.RemoveTokenRequest
import team.siru.ecoguard.notification.dto.UpdateNotificationSettingRequest
import team.siru.ecoguard.user.Role

@Tag(
    name = "10. 알림 (Notification)",
    description = """
앱 안 알림함과 FCM 푸시. 모든 알림은 알림함에 쌓이고, 푸시는 수신 설정을 켠 종류만 보냅니다.

**FCM 메시지 형식** — `notification`(title, body)과 함께 아래 `data` 가 옵니다(값은 모두 문자열).

| data 키 | 항상 | 설명 |
|---|---|---|
| `type` | O | 알림 종류 (아래 표) |
| `refId` | 종류에 따라 | 눌렀을 때 이동할 대상의 ID |
| `notificationId` | 한 명에게 보낸 알림만 | 알림함 ID. 눌렀을 때 `PATCH /notifications/{notificationId}/read` 에 쓴다. 공지·모집처럼 여러 명에게 보낸 알림에는 없다 |

| type | 받는 사람 | refId | 이동 화면 예 |
|---|---|---|---|
| `VERIFICATION_APPROVED` | 학생 | verificationId | 인증 결과 |
| `VERIFICATION_MANUAL_REVIEW` | 학생 | verificationId | 인증 결과(선생님 확인 중) |
| `VERIFICATION_REJECTED` | 학생 | verificationId | 인증 결과(반려 사유·이의신청) |
| `APPEAL_APPROVED` | 학생 | appealId | 내 이의신청 |
| `APPEAL_REJECTED` | 학생 | appealId | 내 이의신청 |
| `AREA_ASSIGNED` | 학생 | areaId | 내 구역 |
| `AREA_UNASSIGNED` | 학생 | areaId | 홈 |
| `NOTICE_CREATED` | 학생 전체 | noticeId | 공지 상세 |
| `RECRUITMENT_CREATED` | 해당 반 학생 | recruitmentId | 모집 공고 |
| `CLEANING_REMINDER` | 배정 학생 | areaId | 인증 화면 (시작 직전 / 마감 직전 미제출자) |
| `MANUAL_REVIEW_PENDING` | 교사 전체 | verificationId (여러 건 묶음이면 없음) | 수동 검토 목록 |
| `APPEAL_RECEIVED` | 교사 전체 | appealId | 이의신청 목록 |
""",
)
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService,
) {

    /** 앱을 켜거나 로그인할 때마다 호출한다. 이미 등록된 토큰이면 현재 로그인한 계정의 것으로 갱신한다. */
    @Operation(
        summary = "푸시(FCM) 토큰 등록",
        description = """
**[공통]** 로그인 직후, 앱을 켤 때마다, FCM 토큰이 바뀔 때(`onNewToken`) 호출하세요. 응답 바디는 없습니다(`200`).

- 이미 등록된 토큰이면 지금 로그인한 계정의 것으로 옮기고 마지막 사용 시각을 갱신합니다(한 기기를 다른 계정이 이어 쓰는 경우).
- 한 사용자가 여러 기기 토큰을 가질 수 있습니다. 60일 넘게 갱신되지 않은 토큰은 자동으로 지워집니다.
""",
    )
    @PostMapping("/tokens")
    fun registerToken(@Valid @RequestBody request: RegisterTokenRequest): ResponseEntity<Void> {
        notificationService.registerToken(SecurityUtils.currentUserId(), request.token!!, request.platform!!)
        return ResponseEntity.ok().build()
    }

    /** 로그아웃 직전에 호출해 이 기기로 알림이 오지 않게 한다. */
    @Operation(
        summary = "푸시(FCM) 토큰 삭제",
        description = """
**[공통]** 이 기기로 푸시가 오지 않게 합니다. 응답 바디는 없습니다(`204`). 내 계정에 없는 토큰이어도 `204` 입니다.

`POST /auth/logout` 은 모든 기기의 토큰을 함께 지우므로, 로그아웃할 때는 따로 부르지 않아도 됩니다.
""",
    )
    @DeleteMapping("/tokens")
    @ApiErrors(success = 204)
    fun removeToken(@Valid @RequestBody request: RemoveTokenRequest): ResponseEntity<Void> {
        notificationService.removeToken(SecurityUtils.currentUserId(), request.token!!)
        return ResponseEntity.noContent().build()
    }

    /** 최신순 알림 목록과 읽지 않은 알림 수. */
    @Operation(
        summary = "알림함 목록",
        description = """
**[공통]** 내 알림을 최신순으로 최대 `limit` 개 주고, 전체 읽지 않은 알림 수(`unreadCount`, 배지용)를 함께 줍니다.
`limit` 은 1~100 으로 맞춰집니다(범위 밖이면 가까운 값으로).
""",
    )
    @GetMapping
    fun getList(
        @Parameter(description = "가져올 개수 (기본 50, 1~100)", example = "50")
        @RequestParam(defaultValue = "50") limit: Int,
    ): ResponseEntity<NotificationListResponse> {
        val userId = SecurityUtils.currentUserId()
        return ResponseEntity.ok(
            NotificationListResponse(
                unreadCount = notificationService.countUnread(userId),
                notifications = notificationService.getMyNotifications(userId, limit).map(NotificationResponse::from),
            ),
        )
    }

    /** 알림 종류별 푸시 수신 여부. 설정한 적 없는 종류는 켜져 있다. 꺼도 알림함에는 계속 쌓인다. */
    @Operation(
        summary = "푸시 수신 설정 조회",
        description = """
**[공통]** 알림 종류(카테고리)별 푸시 수신 여부입니다. 설정 화면에 그대로 나열하면 됩니다(`label` 이 표시 이름).

- 학생: `VERIFICATION`, `APPEAL`, `AREA`, `NOTICE`, `RECRUITMENT`, `REMINDER` / 교사: `REVIEW_QUEUE`, `APPEAL_INBOX`.
- 한 번도 바꾸지 않은 종류는 `enabled=true` 입니다. 꺼도 알림함에는 계속 쌓이고 푸시만 오지 않습니다.
""",
    )
    @GetMapping("/settings")
    fun getSettings(): ResponseEntity<List<NotificationSettingResponse>> =
        ResponseEntity.ok(
            notificationService.getSettings(SecurityUtils.currentUserId(), currentRole())
                .map { (category, enabled) -> NotificationSettingResponse(category, category.label, enabled) },
        )

    /** 설정 화면에 보이는 알림 종류는 역할(학생·교사)마다 다르다. */
    private fun currentRole() = if (SecurityUtils.hasRole(Role.TEACHER)) Role.TEACHER else Role.STUDENT

    @Operation(
        summary = "푸시 수신 설정 변경",
        description = """
**[공통]** 한 종류의 푸시를 켜거나 끕니다. 응답 바디는 없습니다(`200`).
내 역할이 설정할 수 없는 종류(예: 학생이 `REVIEW_QUEUE`)면 `400 INVALID_NOTIFICATION_CATEGORY`.
""",
    )
    @ApiErrors(ErrorCode.INVALID_NOTIFICATION_CATEGORY)
    @PutMapping("/settings")
    fun updateSetting(@Valid @RequestBody request: UpdateNotificationSettingRequest): ResponseEntity<Void> {
        notificationService.updateSetting(SecurityUtils.currentUserId(), currentRole(), request.category!!, request.enabled!!)
        return ResponseEntity.ok().build()
    }

    @Operation(
        summary = "알림 하나 읽음 처리",
        description = """
**[공통]** 알림함에서 알림을 누르거나 푸시를 눌러 들어왔을 때(`data.notificationId`) 호출합니다. 이미 읽은 알림이어도 `200` 입니다.
내 알림이 아니거나 없으면 `404 NOTIFICATION_NOT_FOUND`.
""",
    )
    @PatchMapping("/{notificationId}/read")
    @ApiErrors(ErrorCode.NOTIFICATION_NOT_FOUND)
    fun markRead(
        @Parameter(description = "알림 ID (목록의 `id` 또는 푸시 data 의 `notificationId`)", example = "230")
        @PathVariable notificationId: Long,
    ): ResponseEntity<Void> {
        notificationService.markRead(SecurityUtils.currentUserId(), notificationId)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "알림 모두 읽음 처리", description = "**[공통]** 내 알림을 모두 읽음으로 바꿉니다. 응답 바디는 없습니다(`200`).")
    @PatchMapping("/read-all")
    fun markAllRead(): ResponseEntity<Void> {
        notificationService.markAllRead(SecurityUtils.currentUserId())
        return ResponseEntity.ok().build()
    }
}
