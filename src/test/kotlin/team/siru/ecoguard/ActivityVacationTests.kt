package team.siru.ecoguard

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.stubbing.Answer
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
import team.siru.ecoguard.activity.ServiceTimeLogRepository
import team.siru.ecoguard.appeal.AppealRepository
import team.siru.ecoguard.cleaningarea.Assignment
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.cleaningarea.CleaningAreaRepository
import team.siru.ecoguard.notice.NoticeRepository
import team.siru.ecoguard.recruitment.RecruitmentApplicationRepository
import team.siru.ecoguard.recruitment.RecruitmentRepository
import team.siru.ecoguard.schoolcalendar.VacationService
import team.siru.ecoguard.user.User
import team.siru.ecoguard.user.UserRepository
import team.siru.ecoguard.verification.Verification
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val VACATION: ClosedRange<LocalDate> = LocalDate.of(2026, 9, 14)..LocalDate.of(2026, 9, 18)

/**
 * 기준 시각 2026-09-16(수) 09:00, 9/14~9/18 이 방학이다. 9/7 승인, 9/8 반려만 제출했다.
 *
 * 방학은 인증이 막혀 있으므로 활동 기록에서 미제출/예정으로 세지 않고 필요 일수에서도 뺀다.
 * 월 07 승인 / 화 08 반려 / 수 09·목 10·금 11 미제출 / 9/14~9/18 방학 / 9/21~ 예정
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActivityVacationTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
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
    class VacationConfig {
        @Bean
        @Primary
        fun mutableClock(): MutableClock = MutableClock(vacationAt(2026, 9, 16, 9, 0))

        /** 진짜 NEIS 대신 [VACATION] 기간만 방학으로 답하는 가짜 서비스. */
        @Bean
        @Primary
        fun vacationService(): VacationService =
            Mockito.mock(
                VacationService::class.java,
                Answer { invocation ->
                    if (invocation.method.name == "isVacation") invocation.getArgument<LocalDate>(0) in VACATION else null
                },
            )
    }

    private lateinit var studentToken: String
    private lateinit var student: User

    @BeforeEach
    fun setUp() {
        cleanDatabase()
        studentToken = login("STUDENT|9402|s@test.local|Student|1101|1|1")
        student = userRepository.findByGsmAccountId(9402)!!

        val area = cleaningAreaRepository.save(
            CleaningArea(zoneCode = "zone_V", name = "Hall", cleanTime = "08:00~08:10", isActive = true),
        )
        assignmentRepository.save(Assignment(area = area, student = student))
        verify(area, LocalDate.of(2026, 9, 7), VerificationStatus.APPROVED)
        verify(area, LocalDate.of(2026, 9, 8), VerificationStatus.REJECTED)
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
    fun `vacation weekdays are neither missed nor required in the monthly record`() {
        mockMvc.get("/api/v1/service-times/me") { bearer(studentToken) }.andExpect {
            status { isOk() }
            // 9/7 이후 9월 평일 18일 중 방학 5일(9/14~9/18)을 뺀 13일
            jsonPath("$.summary.requiredDays") { value(13) }
            jsonPath("$.summary.approvedCount") { value(1) }
            jsonPath("$.summary.rejectedCount") { value(1) }
            // 방학 전 미제출(9/9, 9/10, 9/11)만 센다. 방학 중 9/14~9/16 은 세지 않는다.
            jsonPath("$.summary.notSubmittedCount") { value(3) }
            jsonPath("$.records.length()") { value(5) }
            jsonPath("$.records[0].date") { value("2026-09-11") }
        }
    }

    @Test
    fun `a submission made on a vacation day is still shown`() {
        val area = cleaningAreaRepository.findAll().first()
        verify(area, LocalDate.of(2026, 9, 15), VerificationStatus.APPROVED)

        mockMvc.get("/api/v1/service-times/me") { bearer(studentToken) }.andExpect {
            status { isOk() }
            jsonPath("$.summary.approvedCount") { value(2) }
            jsonPath("$.records[0].date") { value("2026-09-15") }
            jsonPath("$.records[0].result") { value("APPROVED") }
        }
    }

    @Test
    fun `weekly required days exclude vacation days`() {
        mockMvc.get("/api/v1/service-times/me/weekly") { bearer(studentToken) }.andExpect {
            status { isOk() }
            jsonPath("$.weekStart") { value("2026-09-14") }
            // 이번 주 전체가 방학이라 필요 일수 0, 요일별 항목도 없다.
            jsonPath("$.requiredDays") { value(0) }
            jsonPath("$.completedDays") { value(0) }
            jsonPath("$.days.length()") { value(0) }
        }
    }

    private fun verify(area: CleaningArea, date: LocalDate, status: VerificationStatus) {
        verificationRepository.save(
            Verification(student = student, area = area, photoUrl = "/files/$date.jpg", verificationDate = date, status = status),
        )
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

private fun vacationAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
    LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.of("Asia/Seoul")).toInstant()
