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
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import team.siru.ecoguard.cleaningarea.Assignment
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.cleaningarea.CleaningAreaRepository
import team.siru.ecoguard.user.UserRepository
import team.siru.ecoguard.verification.CleaningTimeWindow
import team.siru.ecoguard.verification.VerificationRepository
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 인증 시간(07:20~08:10) 안이어도 주말에는 인증을 제출할 수 없다. */
@SpringBootTest
@AutoConfigureMockMvc
class WeekendVerificationTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val clock: MutableClock,
    private val userRepository: UserRepository,
    private val cleaningAreaRepository: CleaningAreaRepository,
    private val assignmentRepository: AssignmentRepository,
    private val verificationRepository: VerificationRepository,
) {

    @TestConfiguration
    class WeekendClockConfig {
        @Bean
        @Primary
        fun weekendMutableClock(): MutableClock = MutableClock(atSeoul(2026, 10, 3, 7, 30))
    }

    private lateinit var studentToken: String

    @BeforeEach
    fun setUp() {
        cleanDatabase()
        studentToken = login("STUDENT|9402|w@test.local|Student|1101|1|1")
        val student = userRepository.findByGsmAccountId(9402)!!
        val area = cleaningAreaRepository.save(
            CleaningArea(zoneCode = "zone_W", name = "Hall", cleanTime = "07:20~08:10", isActive = true),
        )
        assignmentRepository.save(Assignment(area = area, student = student))
    }

    @AfterEach
    fun cleanDatabase() {
        verificationRepository.deleteAll()
        assignmentRepository.deleteAll()
        cleaningAreaRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `submission inside the time window is rejected on Saturday and Sunday`() {
        listOf(atSeoul(2026, 10, 3, 7, 30), atSeoul(2026, 10, 4, 7, 30)).forEach { instant ->
            clock.set(instant)

            mockMvc.multipart("/api/v1/verifications") {
                file(MockMultipartFile("photo", "photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3)))
                header("Authorization", "Bearer $studentToken")
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("OUT_OF_CERTIFICATION_TIME") }
            }
        }
        assertEquals(0, verificationRepository.count())
    }

    @Test
    fun `only weekdays are certification days`() {
        val monday = LocalDate.of(2026, 9, 28)
        (0L..4L).forEach { assertTrue(CleaningTimeWindow.isCertificationDay(monday.plusDays(it))) }
        assertFalse(CleaningTimeWindow.isCertificationDay(monday.plusDays(5)))
        assertFalse(CleaningTimeWindow.isCertificationDay(monday.plusDays(6)))
    }

    private fun login(authCode: String): String {
        val result = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("authCode" to authCode))
        }.andExpect { status { isOk() } }.andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asString()
    }
}

private fun atSeoul(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
    LocalDateTime.of(year, month, day, hour, minute).atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant()
