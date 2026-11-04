package team.siru.ecoguard

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionTemplate
import team.siru.ecoguard.auth.AuthService
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.notification.DevicePlatform
import team.siru.ecoguard.notification.DeviceTokenRepository
import team.siru.ecoguard.notification.NotificationEvent
import team.siru.ecoguard.notification.NotificationRepository
import team.siru.ecoguard.notification.NotificationService
import team.siru.ecoguard.notification.NotificationType
import team.siru.ecoguard.notification.PushMessage
import team.siru.ecoguard.notification.PushSender
import team.siru.ecoguard.user.Role
import team.siru.ecoguard.user.User
import team.siru.ecoguard.user.UserRepository
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 이벤트 발행 → 커밋 후 비동기 처리 → 알림함 기록과 푸시 발송까지 실제 스프링 연결로 확인한다. */
@SpringBootTest
class NotificationIntegrationTests @Autowired constructor(
    private val publisher: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate,
    private val notificationService: NotificationService,
    private val notificationRepository: NotificationRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val userRepository: UserRepository,
    private val authService: AuthService,
    private val pushSender: RecordingPushSender,
) {

    /** 보낸 푸시를 기록하는 가짜 발송기. [dead] 에 든 토큰은 만료된 토큰으로 돌려준다. */
    class RecordingPushSender : PushSender {
        val sent = CopyOnWriteArrayList<Pair<List<String>, PushMessage>>()
        val dead = CopyOnWriteArrayList<String>()

        override fun send(tokens: List<String>, message: PushMessage): List<String> {
            sent += tokens to message
            return tokens.filter { it in dead }
        }
    }

    @TestConfiguration
    class PushConfig {
        @Bean
        @Primary
        fun recordingPushSender() = RecordingPushSender()
    }

    private lateinit var student: User
    private lateinit var other: User

    @BeforeEach
    fun setUp() {
        cleanUp()
        student = userRepository.save(User(9001L, "n1@gsm.hs.kr", "학생1", Role.STUDENT, "1101", 1, 1))
        other = userRepository.save(User(9002L, "n2@gsm.hs.kr", "학생2", Role.STUDENT, "1102", 1, 1))
    }

    @AfterEach
    fun cleanUp() {
        notificationRepository.deleteAll()
        deviceTokenRepository.deleteAll()
        userRepository.findAll().filter { it.gsmAccountId in listOf(9001L, 9002L) }.forEach(userRepository::delete)
        pushSender.sent.clear()
        pushSender.dead.clear()
    }

    private fun awaitNotifications(userId: Long, count: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (notificationRepository.countByUserIdAndReadAtIsNull(userId) >= count) return
            Thread.sleep(50)
        }
    }

    @Test
    fun `event after commit is stored in the inbox and pushed to the user's devices`() {
        notificationService.registerToken(student.id, "token-a", DevicePlatform.ANDROID)
        notificationService.registerToken(student.id, "token-b", DevicePlatform.IOS)
        notificationService.registerToken(other.id, "token-other", DevicePlatform.ANDROID)

        transactionTemplate.executeWithoutResult {
            publisher.publishEvent(NotificationEvent.verificationRejected(student.id, 77L))
        }
        awaitNotifications(student.id, 1)

        val inbox = notificationService.getMyNotifications(student.id, 50)
        assertEquals(1, inbox.size)
        assertEquals(NotificationType.VERIFICATION_REJECTED, inbox[0].type)
        assertEquals(77L, inbox[0].refId)
        assertEquals(0, notificationService.getMyNotifications(other.id, 50).size)

        assertEquals(1, pushSender.sent.size)
        val (tokens, message) = pushSender.sent[0]
        assertEquals(setOf("token-a", "token-b"), tokens.toSet())
        assertEquals("VERIFICATION_REJECTED", message.data["type"])
        assertEquals("77", message.data["refId"])
        assertEquals(inbox[0].id.toString(), message.data["notificationId"])
    }

    @Test
    fun `a rolled back transaction sends nothing`() {
        notificationService.registerToken(student.id, "token-a", DevicePlatform.ANDROID)

        runCatching {
            transactionTemplate.executeWithoutResult {
                publisher.publishEvent(NotificationEvent.verificationApproved(student.id, 1L))
                error("rollback")
            }
        }
        Thread.sleep(300)

        assertEquals(0, notificationRepository.count())
        assertTrue(pushSender.sent.isEmpty())
    }

    @Test
    fun `user without devices still gets an inbox entry`() {
        transactionTemplate.executeWithoutResult {
            publisher.publishEvent(NotificationEvent.appealApproved(student.id, 5L))
        }
        awaitNotifications(student.id, 1)

        assertEquals(1, notificationService.getMyNotifications(student.id, 50).size)
        assertTrue(pushSender.sent.isEmpty())
    }

    @Test
    fun `tokens that FCM reports as dead are deleted`() {
        notificationService.registerToken(student.id, "token-live", DevicePlatform.ANDROID)
        notificationService.registerToken(student.id, "token-dead", DevicePlatform.ANDROID)
        pushSender.dead += "token-dead"

        transactionTemplate.executeWithoutResult {
            publisher.publishEvent(NotificationEvent.verificationApproved(student.id, 1L))
        }
        awaitNotifications(student.id, 1)
        Thread.sleep(200)

        assertNotNull(deviceTokenRepository.findByToken("token-live"))
        assertNull(deviceTokenRepository.findByToken("token-dead"))
    }

    @Test
    fun `registering an existing token moves it to the new owner`() {
        notificationService.registerToken(student.id, "shared", DevicePlatform.ANDROID)
        notificationService.registerToken(other.id, "shared", DevicePlatform.ANDROID)

        assertEquals(1, deviceTokenRepository.count())
        assertEquals(other.id, deviceTokenRepository.findByToken("shared")!!.userId)
    }

    @Test
    fun `removing a token only works for its owner`() {
        notificationService.registerToken(student.id, "mine", DevicePlatform.ANDROID)

        notificationService.removeToken(other.id, "mine")
        assertNotNull(deviceTokenRepository.findByToken("mine"))

        notificationService.removeToken(student.id, "mine")
        assertNull(deviceTokenRepository.findByToken("mine"))
    }

    @Test
    fun `logout removes the user's device tokens`() {
        notificationService.registerToken(student.id, "token-a", DevicePlatform.ANDROID)
        notificationService.registerToken(other.id, "token-other", DevicePlatform.ANDROID)

        authService.logout(student.id)

        assertNull(deviceTokenRepository.findByToken("token-a"))
        assertNotNull(deviceTokenRepository.findByToken("token-other"))
    }

    @Test
    fun `marking read only works on the owner's notification`() {
        transactionTemplate.executeWithoutResult {
            publisher.publishEvent(NotificationEvent.appealRejected(student.id, 5L))
        }
        awaitNotifications(student.id, 1)
        val id = notificationService.getMyNotifications(student.id, 50)[0].id

        val error = assertFailsWith<BusinessException> { notificationService.markRead(other.id, id) }
        assertEquals(ErrorCode.NOTIFICATION_NOT_FOUND, error.errorCode)
        assertEquals(1, notificationService.countUnread(student.id))

        notificationService.markRead(student.id, id)
        assertEquals(0, notificationService.countUnread(student.id))
    }
}
