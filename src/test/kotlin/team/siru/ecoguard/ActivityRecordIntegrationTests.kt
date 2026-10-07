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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import team.siru.ecoguard.activity.ServiceTimeLog
import team.siru.ecoguard.activity.ServiceTimeLogRepository
import team.siru.ecoguard.activity.ServiceTimeReason
import team.siru.ecoguard.appeal.AppealRepository
import team.siru.ecoguard.cleaningarea.Assignment
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.cleaningarea.CleaningAreaRepository
import team.siru.ecoguard.notice.NoticeRepository
import team.siru.ecoguard.recruitment.RecruitmentApplicationRepository
import team.siru.ecoguard.recruitment.RecruitmentRepository
import team.siru.ecoguard.user.User
import team.siru.ecoguard.user.UserRepository
import team.siru.ecoguard.verification.Verification
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 기준 시각을 2026-09-16(수)로 고정하고 9월 활동 기록을 검증한다.
 *
 * 월 07 승인 / 화 08 반려 / 수 09 미제출 / 목 10 승인 / 금 11 미제출
 * 월 14 승인 / 화 15 미제출 / 수 16 (오늘) / 목 17~ 예정
 * 8월에 적립된 10분은 누적 시간에만 포함된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActivityRecordIntegrationTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val clock: MutableClock,
    private val userRepository: UserRepository,
    private val noticeRepository: NoticeRepository,
    private val recruitmentRepository: RecruitmentRepository,
    private val applicationRepository: RecruitmentApplicationRepository,
    private val cleaningAreaRepository: CleaningAreaRepository,
    private val assignmentRepository: AssignmentRepository,
    private val verificationRepository: VerificationRepository,
    private val appealRepository: AppealRepository,
    private val serviceTimeLogRepository: ServiceTimeLogRepository,
) {

    @TestConfiguration
    class FixedClockConfig {
        @Bean
        @Primary
        fun mutableClock(): MutableClock = MutableClock(at(2026, 9, 16, 9, 0))
    }

    private lateinit var studentToken: String
    private lateinit var teacherToken: String
    private lateinit var student: User

    @BeforeEach
    fun setUp() {
        cleanDatabase()
        clock.set(at(2026, 9, 16, 9, 0))
        teacherToken = login("TEACHER|9301|t@test.local|Teacher|||")
        studentToken = login("STUDENT|9302|s@test.local|Student|1101|1|1")
        student = userRepository.findByGsmAccountId(9302)!!

        val area = cleaningAreaRepository.save(
            CleaningArea(zoneCode = "zone_R", name = "Hall", cleanTime = "08:00~08:10", isActive = true),
        )
        assignmentRepository.save(Assignment(area = area, student = student))

        verify(area, LocalDate.of(2026, 9, 7), VerificationStatus.APPROVED)
        verify(area, LocalDate.of(2026, 9, 8), VerificationStatus.REJECTED)
        verify(area, LocalDate.of(2026, 9, 10), VerificationStatus.APPROVED)
        verify(area, LocalDate.of(2026, 9, 14), VerificationStatus.APPROVED)
        serviceTimeLogRepository.save(
            ServiceTimeLog(student = student, minutes = 10, reason = ServiceTimeReason.VERIFICATION, date = LocalDate.of(2026, 8, 20)),
        )
    }

    @AfterEach
    fun cleanDatabase() {
        appealRepository.deleteAll()
        serviceTimeLogRepository.deleteAll()
        verificationRepository.deleteAll()
        assignmentRepository.deleteAll()
        applicationRepository.deleteAll()
        recruitmentRepository.deleteAll()
        cleaningAreaRepository.deleteAll()
        noticeRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `monthly record counts approved, rejected and not-submitted weekdays after the window closes`() {
        mockMvc.get("/api/v1/service-times/me") { bearer(studentToken) }.andExpect {
            status { isOk() }
            jsonPath("$.year") { value(2026) }
            jsonPath("$.month") { value(9) }
            jsonPath("$.totalMinutes") { value(40) }
            jsonPath("$.monthlyMinutes") { value(30) }
            jsonPath("$.summary.completedDays") { value(3) }
            // 활동 시작(9/7) 이후 9월 평일 수
            jsonPath("$.summary.requiredDays") { value(18) }
            jsonPath("$.summary.approvedCount") { value(3) }
            jsonPath("$.summary.rejectedCount") { value(1) }
            jsonPath("$.summary.notSubmittedCount") { value(4) }
            jsonPath("$.records.length()") { value(8) }
            jsonPath("$.records[0].date") { value("2026-09-16") }
            jsonPath("$.records[0].result") { value("NOT_SUBMITTED") }
            jsonPath("$.records[7].date") { value("2026-09-07") }
            jsonPath("$.records[7].result") { value("APPROVED") }
            jsonPath("$.records[7].minutes") { value(10) }
        }
    }

    @Test
    fun `today is not counted as missed before the certification window ends`() {
        clock.set(at(2026, 9, 16, 8, 5))

        mockMvc.get("/api/v1/service-times/me") { bearer(studentToken) }.andExpect {
            status { isOk() }
            jsonPath("$.summary.notSubmittedCount") { value(3) }
            jsonPath("$.records.length()") { value(7) }
            jsonPath("$.records[0].date") { value("2026-09-15") }
        }

        mockMvc.get("/api/v1/service-times/me/weekly") { bearer(studentToken) }.andExpect {
            status { isOk() }
            jsonPath("$.days[2].date") { value("2026-09-16") }
            jsonPath("$.days[2].result") { value("UPCOMING") }
        }
    }

    @Test
    fun `other months are selectable and days before activity started are excluded`() {
        mockMvc.get("/api/v1/service-times/me?year=2026&month=8") { bearer(studentToken) }.andExpect {
            status { isOk() }
            jsonPath("$.month") { value(8) }
            jsonPath("$.totalMinutes") { value(40) }
            jsonPath("$.records.length()") { value(0) }
        }

        mockMvc.get("/api/v1/service-times/me?year=2026&month=13") { bearer(studentToken) }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `weekly status shows approved days out of five`() {
        mockMvc.get("/api/v1/service-times/me/weekly") { bearer(studentToken) }.andExpect {
            status { isOk() }
            jsonPath("$.weekStart") { value("2026-09-14") }
            jsonPath("$.weekEnd") { value("2026-09-18") }
            jsonPath("$.completedDays") { value(1) }
            jsonPath("$.requiredDays") { value(5) }
            jsonPath("$.days.length()") { value(5) }
            jsonPath("$.days[0].result") { value("APPROVED") }
            jsonPath("$.days[1].result") { value("NOT_SUBMITTED") }
            jsonPath("$.days[2].result") { value("NOT_SUBMITTED") }
            jsonPath("$.days[3].result") { value("UPCOMING") }
            jsonPath("$.days[4].result") { value("UPCOMING") }
        }
    }

    @Test
    fun `teacher sees attendance and a student's monthly record`() {
        mockMvc.get("/api/v1/students/activities?keyword=1101") { bearer(teacherToken) }.andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("Student") }
            jsonPath("$[0].area") { value("Hall") }
            jsonPath("$[0].attendanceDays") { value(3) }
            jsonPath("$[0].totalMinutes") { value(40) }
        }

        mockMvc.get("/api/v1/students/${student.id}/activities?year=2026&month=9") { bearer(teacherToken) }.andExpect {
            status { isOk() }
            jsonPath("$.summary.approvedCount") { value(3) }
            jsonPath("$.summary.notSubmittedCount") { value(4) }
        }

        mockMvc.get("/api/v1/service-times/me") { bearer(teacherToken) }
            .andExpect { status { isForbidden() } }
    }

    private fun verify(area: CleaningArea, date: LocalDate, status: VerificationStatus) {
        verificationRepository.save(
            Verification(student = student, area = area, photoUrl = "/files/$date.jpg", verificationDate = date, status = status),
        )
        if (status == VerificationStatus.APPROVED) {
            serviceTimeLogRepository.save(
                ServiceTimeLog(student = student, minutes = 10, reason = ServiceTimeReason.VERIFICATION, date = date, areaName = area.name),
            )
        }
    }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.bearer(token: String) {
        header("Authorization", "Bearer $token")
    }

    private fun login(authCode: String): String {
        val result = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("authCode" to authCode))
        }.andExpect { status { isOk() } }.andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asString()
    }
}

private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
    LocalDateTime.of(year, month, day, hour, minute).atZone(ZONE).toInstant()

class MutableClock(private var instant: Instant) : Clock() {
    fun set(instant: Instant) {
        this.instant = instant
    }

    override fun getZone(): ZoneId = ZONE
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = instant
}
