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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import team.siru.ecoguard.activity.ServiceTimeLogRepository
import team.siru.ecoguard.appeal.AppealRepository
import team.siru.ecoguard.cleaningarea.Assignment
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.cleaningarea.CleaningAreaRepository
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
