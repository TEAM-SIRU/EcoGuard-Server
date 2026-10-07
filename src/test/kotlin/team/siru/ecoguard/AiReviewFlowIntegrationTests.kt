package team.siru.ecoguard

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import team.siru.ecoguard.activity.ServiceTimeLogRepository
import team.siru.ecoguard.aireview.AiEvaluateOutcome
import team.siru.ecoguard.aireview.AiEvaluateResponse
import team.siru.ecoguard.aireview.AiEvaluator
import team.siru.ecoguard.aireview.AiReviewRepository
import team.siru.ecoguard.aireview.EvaluateRequest
import team.siru.ecoguard.aireview.ManualReviewReason
import team.siru.ecoguard.appeal.AppealRepository
import team.siru.ecoguard.cleaningarea.Assignment
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.cleaningarea.CleaningAreaRepository
import team.siru.ecoguard.user.UserRepository
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus
import tools.jackson.databind.ObjectMapper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 제출 → 대기열 → 사진 읽기 → AI 검수 → 결과 반영까지 실제 스프링 연결(전용 실행기, 비동기, 트랜잭션)로 확인한다.
 * 작업 스레드 1개, 대기열 1칸으로 줄여서 "대기열이 가득 찬" 상황을 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["ai-review.queue.workers=1", "ai-review.queue.capacity=1"])
class AiReviewFlowIntegrationTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val evaluator: ControllableEvaluator,
    private val userRepository: UserRepository,
    private val cleaningAreaRepository: CleaningAreaRepository,
    private val assignmentRepository: AssignmentRepository,
    private val verificationRepository: VerificationRepository,
    private val aiReviewRepository: AiReviewRepository,
    private val appealRepository: AppealRepository,
    private val serviceTimeLogRepository: ServiceTimeLogRepository,
) {

    /** 호출을 기록하고, [gate] 가 있으면 열릴 때까지 붙잡아 두는 가짜 AI. 항상 통과로 판정한다. */
    class ControllableEvaluator : AiEvaluator {
        @Volatile
        var gate: CountDownLatch? = null
        val seen = CopyOnWriteArrayList<EvaluateRequest>()

        override fun evaluate(request: EvaluateRequest): AiEvaluateOutcome {
            seen += request
            gate?.await(30, TimeUnit.SECONDS)
            return AiEvaluateOutcome.Success(AiEvaluateResponse("PASS", true, emptyList()), """{"is_passed":true}""")
        }
    }

    @TestConfiguration
    class FlowConfig {
        // 2026-10-05 월요일 07:30 (서울) - 인증 가능 시간
        @Bean
        @Primary
        fun flowClock(): MutableClock = MutableClock(
            LocalDateTime.of(2026, 10, 5, 7, 30).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
        )

        @Bean
        @Primary
        fun controllableEvaluator() = ControllableEvaluator()
    }

    private lateinit var area: CleaningArea

    @BeforeEach
    fun setUp() {
        cleanDatabase()
        evaluator.gate = null
        evaluator.seen.clear()
        area = cleaningAreaRepository.save(
            CleaningArea(zoneCode = "zone_Q", name = "Hall", cleanTime = "07:20~08:10", isActive = true),
        )
    }

    @AfterEach
    fun cleanDatabase() {
        evaluator.gate?.countDown()
        aiReviewRepository.deleteAll()
        appealRepository.deleteAll()
        serviceTimeLogRepository.deleteAll()
        verificationRepository.deleteAll()
        assignmentRepository.deleteAll()
        cleaningAreaRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `a submitted photo goes through the queue and is approved`() {
        val token = newStudent(9701)

        val id = submit(token)

        awaitStatus(id, VerificationStatus.APPROVED)
        val received = evaluator.seen.single()
        assertTrue(received.imageBytes.isNotEmpty(), "대기열 차례에 파일에서 읽은 사진이 AI 로 전달된다")
        assertEquals("zone_Q", received.zoneId)
        assertEquals(10L, serviceTimeLogRepository.findAll().sumOf { it.minutes.toLong() })
        assertEquals(true, aiReviewRepository.findAll().single().isPassed)
    }

    @Test
    fun `when the queue is full the extra submission goes to manual review and the rest are still reviewed`() {
        val tokens = listOf(newStudent(9711), newStudent(9712), newStudent(9713))
        evaluator.gate = CountDownLatch(1)

        val first = submit(tokens[0]) // 작업 스레드 1개가 잡고 AI 응답을 기다린다.
        awaitUntil("첫 요청이 AI 까지 도달") { evaluator.seen.size == 1 }
        val second = submit(tokens[1]) // 대기열 한 칸에 들어간다.
        val third = submit(tokens[2]) // 대기열이 가득 차 거부된다.

        // 제출 응답이 돌아온 시점에 이미 수동 검토로 넘어가 있다.
        val rejected = verificationRepository.findById(third).get()
        assertEquals(VerificationStatus.MANUAL_REVIEW, rejected.status)
        assertEquals(ManualReviewReason.QUEUE_FULL.name, rejected.manualReviewReason)

        evaluator.gate!!.countDown()
        awaitStatus(first, VerificationStatus.APPROVED)
        awaitStatus(second, VerificationStatus.APPROVED)
        assertEquals(2, evaluator.seen.size, "수동 검토로 보낸 세 번째 건은 AI 를 호출하지 않는다")
    }

    private fun newStudent(gsmAccountId: Long): String {
        val token = login("STUDENT|$gsmAccountId|s$gsmAccountId@test.local|S$gsmAccountId|11$gsmAccountId|1|1")
        assignmentRepository.save(Assignment(area = area, student = userRepository.findByGsmAccountId(gsmAccountId)!!))
        return token
    }

    private fun submit(token: String): Long {
        val body = mockMvc.multipart("/api/v1/verifications") {
            file(MockMultipartFile("photo", "photo.png", "image/png", pngBytes()))
            header("Authorization", "Bearer $token")
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("verificationId").asLong()
    }

    private fun awaitStatus(id: Long, expected: VerificationStatus) =
        awaitUntil("인증 $id 이(가) $expected") { verificationRepository.findById(id).get().status == expected }

    private fun awaitUntil(what: String, timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "${timeoutMillis}ms 안에 조건이 충족되지 않았습니다: $what" }
            Thread.sleep(25)
        }
    }

    private fun login(authCode: String): String {
        val result = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("authCode" to authCode))
        }.andExpect { status { isOk() } }.andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asString()
    }

    private fun pngBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }
}
