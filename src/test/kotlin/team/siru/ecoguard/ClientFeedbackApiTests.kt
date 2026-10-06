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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import team.siru.ecoguard.activity.ServiceTimeLogRepository
import team.siru.ecoguard.appeal.AppealRepository
import team.siru.ecoguard.cleaningarea.Assignment
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.cleaningarea.CleaningAreaRepository
import team.siru.ecoguard.notice.NoticeReadRepository
import team.siru.ecoguard.notice.NoticeRepository
import team.siru.ecoguard.recruitment.RecruitmentRepository
import team.siru.ecoguard.user.UserRepository
import team.siru.ecoguard.verification.Verification
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** iOS 팀 피드백으로 추가된 API: /users/me, /verifications/today, 이의신청 사진, 시각 필드. */
@SpringBootTest
@AutoConfigureMockMvc
class ClientFeedbackApiTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val clock: MutableClock,
    private val userRepository: UserRepository,
    private val cleaningAreaRepository: CleaningAreaRepository,
    private val assignmentRepository: AssignmentRepository,
    private val verificationRepository: VerificationRepository,
    private val appealRepository: AppealRepository,
    private val serviceTimeLogRepository: ServiceTimeLogRepository,
    private val noticeRepository: NoticeRepository,
    private val noticeReadRepository: NoticeReadRepository,
    private val recruitmentRepository: RecruitmentRepository,
) {

    @TestConfiguration
    class FeedbackClockConfig {
        // 2026-10-05 월요일 07:30 (서울)
        @Bean
        @Primary
        fun feedbackMutableClock(): MutableClock = MutableClock(seoul(2026, 10, 5, 7, 30))
    }

    private lateinit var token: String
    private lateinit var area: CleaningArea

    @BeforeEach
    fun setUp() {
        cleanDatabase()
        clock.set(seoul(2026, 10, 5, 7, 30))
        token = login("STUDENT|9601|f@test.local|Feedback|1101|1|1")
        val student = userRepository.findByGsmAccountId(9601)!!
        area = cleaningAreaRepository.save(
            CleaningArea(zoneCode = "zone_F", name = "Hall", cleanTime = "07:20~08:10", isActive = true),
        )
        assignmentRepository.save(Assignment(area = area, student = student))
    }

    @AfterEach
    fun cleanDatabase() {
        appealRepository.deleteAll()
        serviceTimeLogRepository.deleteAll()
        verificationRepository.deleteAll()
        assignmentRepository.deleteAll()
        recruitmentRepository.deleteAll()
        noticeReadRepository.deleteAll()
        noticeRepository.deleteAll()
        cleaningAreaRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `users me returns profile with grade class and student number`() {
        mockMvc.get("/api/v1/users/me") { header("Authorization", "Bearer $token") }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Feedback") }
            jsonPath("$.studentNumber") { value("1101") }
            jsonPath("$.grade") { value(1) }
            jsonPath("$.classNo") { value(1) }
            jsonPath("$.role") { value("STUDENT") }
        }
    }

    @Test
    fun `today verification info reflects the window and the submission`() {
        mockMvc.get("/api/v1/verifications/today") { header("Authorization", "Bearer $token") }.andExpect {
            status { isOk() }
            jsonPath("$.areaName") { value("Hall") }
            jsonPath("$.startTime") { value("07:20:00") }
            jsonPath("$.endTime") { value("08:10:00") }
            jsonPath("$.canSubmit") { value(true) }
            jsonPath("$.submitted") { value(false) }
            jsonPath("$.unavailableReason") { doesNotExist() }
        }

        clock.set(seoul(2026, 10, 5, 8, 30))
        mockMvc.get("/api/v1/verifications/today") { header("Authorization", "Bearer $token") }.andExpect {
            jsonPath("$.canSubmit") { value(false) }
            jsonPath("$.unavailableReason") { value("AFTER_END") }
        }

        clock.set(seoul(2026, 10, 3, 7, 30))
        mockMvc.get("/api/v1/verifications/today") { header("Authorization", "Bearer $token") }.andExpect {
            jsonPath("$.unavailableReason") { value("WEEKEND") }
        }

        clock.set(seoul(2026, 10, 5, 7, 40))
        val response = mockMvc.multipart("/api/v1/verifications") {
            file(MockMultipartFile("photo", "photo.png", "image/png", pngBytes()))
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.submittedAt") { exists() }
        }.andReturn().response.contentAsString
        val verificationId = objectMapper.readTree(response).get("verificationId").asLong()

        mockMvc.get("/api/v1/verifications/today") { header("Authorization", "Bearer $token") }.andExpect {
            jsonPath("$.submitted") { value(true) }
            jsonPath("$.canSubmit") { value(false) }
            jsonPath("$.unavailableReason") { value("ALREADY_SUBMITTED") }
            jsonPath("$.verificationId") { value(verificationId) }
            jsonPath("$.submittedAt") { exists() }
        }
        mockMvc.get("/api/v1/verifications/me") { header("Authorization", "Bearer $token") }.andExpect {
            jsonPath("$[0].submittedAt") { exists() }
        }
    }

    @Test
    fun `appeal accepts up to three photos and exposes them with awarded minutes`() {
        val student = userRepository.findByGsmAccountId(9601)!!
        val rejected = verificationRepository.save(
            Verification(
                student = student, area = area, photoUrl = "/files/a.jpg",
                verificationDate = LocalDate.of(2026, 10, 2), status = VerificationStatus.REJECTED,
            ),
        )

        mockMvc.multipart("/api/v1/verifications/${rejected.id}/appeals") {
            param("content", "too many")
            repeat(4) { file(MockMultipartFile("photos", "p$it.png", "image/png", pngBytes())) }
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("TOO_MANY_APPEAL_PHOTOS") }
        }
        assertEquals(0, appealRepository.count())

        mockMvc.multipart("/api/v1/verifications/${rejected.id}/appeals") {
            param("content", "retaken")
            repeat(3) { file(MockMultipartFile("photos", "p$it.png", "image/png", pngBytes())) }
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.round") { value(1) }
        }

        mockMvc.get("/api/v1/appeals/me") { header("Authorization", "Bearer $token") }.andExpect {
            jsonPath("$[0].photoUrls.length()") { value(3) }
            jsonPath("$[0].awardedMinutes") { doesNotExist() }
        }
    }

    @Test
    fun `resubmission with the same idempotency key returns the first result`() {
        fun submit(key: String?, startedAt: String? = null) = mockMvc.multipart("/api/v1/verifications") {
            file(MockMultipartFile("photo", "photo.png", "image/png", pngBytes()))
            header("Authorization", "Bearer $token")
            key?.let { header("Idempotency-Key", it) }
            startedAt?.let { header("X-Submit-Started-At", it) }
        }

        val first = submit("key-1").andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val firstId = objectMapper.readTree(first).get("verificationId").asLong()

        submit("key-1").andExpect {
            status { isCreated() }
            jsonPath("$.verificationId") { value(firstId) }
        }
        assertEquals(1, verificationRepository.count())

        // 키 없이 또 보내면 이미 제출한 것으로 처리하고, 기존 제출 시각을 알려 준다.
        submit(null).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ALREADY_SUBMITTED_TODAY") }
            jsonPath("$.submittedAt") { exists() }
        }
    }

    @Test
    fun `retry started before the deadline is accepted shortly after it`() {
        fun submit(key: String?, startedAt: String?) = mockMvc.multipart("/api/v1/verifications") {
            file(MockMultipartFile("photo", "photo.png", "image/png", pngBytes()))
            header("Authorization", "Bearer $token")
            key?.let { header("Idempotency-Key", it) }
            startedAt?.let { header("X-Submit-Started-At", it) }
        }

        clock.set(seoul(2026, 10, 5, 8, 12))
        // 시작 시각 정보가 없거나 키가 없으면 마감 뒤에는 받지 않는다.
        submit("k", null).andExpect { status { isForbidden() } }
        submit(null, "2026-10-05T08:09:00+09:00").andExpect { status { isForbidden() } }
        // 마감 전에 시작했고 유예 시간(5분) 안이면 받는다.
        submit("k", "2026-10-05T08:09:00+09:00").andExpect { status { isCreated() } }

        // 유예 시간이 지나면 받지 않는다.
        verificationRepository.deleteAll()
        clock.set(seoul(2026, 10, 5, 8, 20))
        submit("k2", "2026-10-05T08:09:00+09:00").andExpect { status { isForbidden() } }
        // 마감 뒤에 시작한 요청은 받지 않는다.
        clock.set(seoul(2026, 10, 5, 8, 12))
        submit("k3", "2026-10-05T08:11:00+09:00").andExpect { status { isForbidden() } }
    }

    @Test
    fun `notice list tracks read state and appeal reply has a separate title`() {
        val teacher = login("TEACHER|9602|t@test.local|Teacher|||")
        val noticeId = objectMapper.readTree(
            mockMvc.post("/api/v1/notices") {
                header("Authorization", "Bearer $teacher")
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"Hello","content":"first   line\nsecond"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("noticeId").asLong()

        mockMvc.get("/api/v1/notices") { header("Authorization", "Bearer $token") }.andExpect {
            jsonPath("$[0].isRead") { value(false) }
            jsonPath("$[0].preview") { value("first line second") }
        }
        mockMvc.get("/api/v1/notices/$noticeId") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }
        mockMvc.get("/api/v1/notices") { header("Authorization", "Bearer $token") }
            .andExpect { jsonPath("$[0].isRead") { value(true) } }
        // 읽음은 사용자별이다.
        mockMvc.get("/api/v1/notices") { header("Authorization", "Bearer $teacher") }
            .andExpect { jsonPath("$[0].isRead") { value(false) } }

        val student = userRepository.findByGsmAccountId(9601)!!
        val rejected = verificationRepository.save(
            Verification(
                student = student, area = area, photoUrl = "/files/a.jpg",
                verificationDate = LocalDate.of(2026, 10, 2), status = VerificationStatus.REJECTED,
            ),
        )
        val appealId = objectMapper.readTree(
            mockMvc.post("/api/v1/verifications/${rejected.id}/appeals") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"content":"please"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("appealId").asLong()
        mockMvc.patch("/api/v1/appeals/$appealId") {
            header("Authorization", "Bearer $teacher")
            contentType = MediaType.APPLICATION_JSON
            content = """{"decision":"REJECTED","replyTitle":"Photo unclear","reply":"Retake it"}"""
        }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/appeals/me") { header("Authorization", "Bearer $token") }.andExpect {
            jsonPath("$[0].replyTitle") { value("Photo unclear") }
            jsonPath("$[0].reply") { value("Retake it") }
        }

        mockMvc.delete("/api/v1/notices/$noticeId") { header("Authorization", "Bearer $teacher") }
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `recruitment exposes activity time with a default and validates it`() {
        val teacher = login("TEACHER|9603|t2@test.local|Teacher2|||")
        fun create(extra: String) = mockMvc.post("/api/v1/recruitments") {
            header("Authorization", "Bearer $teacher")
            contentType = MediaType.APPLICATION_JSON
            content = """{"semester":"2026-2","grade":1,"classNo":1,"maxCount":3,
                "startDate":"2026-10-01T00:00:00","endDate":"2026-10-30T00:00:00"$extra}"""
        }

        create(""","activityStartTime":"08:10","activityEndTime":"07:20"""")
            .andExpect { status { isBadRequest() }; jsonPath("$.code") { value("INVALID_ACTIVITY_TIME") } }
        create(""","activityStartTime":"07:00","activityEndTime":"07:50"""").andExpect { status { isCreated() } }

        mockMvc.get("/api/v1/recruitments/current") { header("Authorization", "Bearer $token") }.andExpect {
            jsonPath("$.activityTime.start") { value("07:00:00") }
            jsonPath("$.activityTime.end") { value("07:50:00") }
        }
    }

    private fun login(authCode: String): String {
        val result = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("authCode" to authCode))
        }.andExpect { status { isOk() } }.andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    private fun pngBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        assertTrue(ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", out))
        return out.toByteArray()
    }
}

private fun seoul(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
    LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.of("Asia/Seoul")).toInstant()
