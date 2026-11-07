package team.siru.ecoguard

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import team.siru.ecoguard.cleaningarea.Assignment
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.cleaningarea.CleaningAreaRepository
import team.siru.ecoguard.notification.DevicePlatform
import team.siru.ecoguard.notification.DeviceTokenRepository
import team.siru.ecoguard.notification.NotificationRepository
import team.siru.ecoguard.notification.NotificationService
import team.siru.ecoguard.notification.NotificationType
import team.siru.ecoguard.notification.ReminderService
import team.siru.ecoguard.user.Role
import team.siru.ecoguard.user.User
import team.siru.ecoguard.user.UserRepository
import team.siru.ecoguard.verification.Verification
import team.siru.ecoguard.verification.VerificationRepository
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 구역별 인증 시간에 맞춰 리마인더가 나가는지, 주말·제출자·비활성 구역이 빠지는지 확인한다. */
@SpringBootTest
@Import(NotificationIntegrationTests.PushConfig::class)
class ReminderIntegrationTests @Autowired constructor(
    private val reminderService: ReminderService,
    private val notificationService: NotificationService,
    private val notificationRepository: NotificationRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val userRepository: UserRepository,
    private val cleaningAreaRepository: CleaningAreaRepository,
    private val assignmentRepository: AssignmentRepository,
    private val verificationRepository: VerificationRepository,
    private val pushSender: NotificationIntegrationTests.RecordingPushSender,
) {

    private lateinit var pending: User
    private lateinit var submitted: User
    private lateinit var inInactiveArea: User

    private val monday = LocalDate.of(2026, 10, 5)
    private val saturday = LocalDate.of(2026, 10, 10)

    @BeforeEach
    fun setUp() {
        cleanUp()
        val area = cleaningAreaRepository.save(
            CleaningArea(zoneCode = "rem_zone", name = "본관 계단", cleanTime = "07:20~08:10", isActive = true),
        )
        val inactive = cleaningAreaRepository.save(
            CleaningArea(zoneCode = "rem_zone_off", name = "닫힌 구역", cleanTime = "07:20~08:10", isActive = false),
        )
        pending = student(9101L, "1101")
        submitted = student(9102L, "1102")
        inInactiveArea = student(9103L, "1103")
        assignmentRepository.save(Assignment(area, pending))
        assignmentRepository.save(Assignment(area, submitted))
        assignmentRepository.save(Assignment(inactive, inInactiveArea))
        verificationRepository.save(Verification(submitted, area, "/files/x.jpg", monday))

        listOf(pending, submitted, inInactiveArea).forEach {
            notificationService.registerToken(it.id, "token-${it.gsmAccountId}", DevicePlatform.ANDROID)
        }
    }

    @AfterEach
    fun cleanUp() {
        verificationRepository.deleteAll()
        assignmentRepository.deleteAll()
        cleaningAreaRepository.findAll().filter { it.zoneCode.startsWith("rem_zone") }.forEach(cleaningAreaRepository::delete)
        notificationRepository.deleteAll()
        deviceTokenRepository.deleteAll()
        userRepository.findAll().filter { it.gsmAccountId in 9101L..9104L }.forEach(userRepository::delete)
        pushSender.sent.clear()
    }

    private fun student(accountId: Long, number: String) =
        userRepository.save(User(accountId, "$accountId@gsm.hs.kr", "학생$accountId", Role.STUDENT, number, 1, 1))

    private fun inbox(user: User) = notificationService.getMyNotifications(user.id, 50)

    private fun awaitInbox(user: User) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && inbox(user).isEmpty()) Thread.sleep(50)
        Thread.sleep(100)
    }

    @Test
    fun `reminder before the start goes to every assigned student of an active area`() {
        reminderService.sendReminders(LocalDateTime.of(monday, java.time.LocalTime.of(7, 10, 30)))
        awaitInbox(pending)
        awaitInbox(submitted)

        assertEquals(NotificationType.CLEANING_REMINDER, inbox(pending).single().type)
        assertEquals(1, inbox(submitted).size)
        assertTrue(inbox(inInactiveArea).isEmpty())

        val (tokens, message) = pushSender.sent.single()
        assertEquals(setOf("token-9101", "token-9102"), tokens.toSet())
        assertEquals("CLEANING_REMINDER", message.data["type"])
    }

    @Test
    fun `deadline reminder skips students who already submitted`() {
        reminderService.sendReminders(LocalDateTime.of(monday, java.time.LocalTime.of(7, 55)))
        awaitInbox(pending)

        assertEquals(1, inbox(pending).size)
        assertTrue(inbox(submitted).isEmpty())
        assertEquals(listOf("token-9101"), pushSender.sent.single().first)
    }

    @Test
    fun `nothing is sent at other times or on weekends`() {
        assertEquals(0, reminderService.sendReminders(LocalDateTime.of(monday, java.time.LocalTime.of(7, 30))))
        assertEquals(0, reminderService.sendReminders(LocalDateTime.of(saturday, java.time.LocalTime.of(7, 10))))
        assertEquals(0, reminderService.sendReminders(LocalDateTime.of(saturday, java.time.LocalTime.of(7, 55))))
        Thread.sleep(300)

        assertEquals(0, notificationRepository.count())
        assertTrue(pushSender.sent.isEmpty())
    }

    @Test
    fun `start and deadline reminders both go out when they fall on the same minute`() {
        // 인증 시간이 5분이면 시작 10분 전과 마감 15분 전이 같은 07:10 이다.
        val shortArea = cleaningAreaRepository.save(
            CleaningArea(zoneCode = "rem_zone_short", name = "짧은 구역", cleanTime = "07:20~07:25", isActive = true),
        )
        val shortStudent = student(9104L, "1104")
        assignmentRepository.save(Assignment(shortArea, shortStudent))

        reminderService.sendReminders(LocalDateTime.of(monday, java.time.LocalTime.of(7, 10)))
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && inbox(shortStudent).size < 2) Thread.sleep(50)

        assertEquals(2, inbox(shortStudent).size)
    }
}
