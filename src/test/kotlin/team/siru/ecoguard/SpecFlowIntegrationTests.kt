package team.siru.ecoguard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import team.siru.ecoguard.activity.ServiceTimeLogRepository
import team.siru.ecoguard.appeal.AppealRepository
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.cleaningarea.CleaningAreaRepository
import team.siru.ecoguard.notice.NoticeRepository
import team.siru.ecoguard.recruitment.RecruitmentApplicationRepository
import team.siru.ecoguard.recruitment.RecruitmentRepository
import team.siru.ecoguard.user.UserRepository
import team.siru.ecoguard.verification.Verification
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class SpecFlowIntegrationTests @Autowired constructor(
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
    @BeforeEach
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
    fun `recruitment is per class, approved immediately first-come`() {
        val teacher = login("TEACHER|9101|t@test.local|Teacher|||")
        val s1 = login("STUDENT|9102|s1@test.local|S1|1101|1|1")
        val s2 = login("STUDENT|9103|s2@test.local|S2|1102|1|1")
        val otherClass = login("STUDENT|9104|s3@test.local|S3|1201|1|2")

        val recruitmentId = post(teacher, "/api/v1/recruitments", """
            {"semester":"2026-2","grade":1,"classNo":1,"maxCount":1,
             "startDate":"${LocalDateTime.now().minusDays(1)}","endDate":"${LocalDateTime.now().plusDays(1)}"}
        """).get("recruitmentId").asLong()

        mockMvc.get("/api/v1/recruitments/current") { bearer(s1) }.andExpect {
            status { isOk() }
            jsonPath("$.periodStatus") { value("OPEN") }
            jsonPath("$.alreadyApplied") { value(false) }
        }

        mockMvc.post("/api/v1/recruitments/$recruitmentId/applications") {
            bearer(otherClass); json("""{"motivation":"x"}""")
        }.andExpect { status { isForbidden() } }

        mockMvc.post("/api/v1/recruitments/$recruitmentId/applications") {
            bearer(s1); json("""{"motivation":"x"}""")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.order") { value(1) }
            jsonPath("$.status") { value("APPROVED") }
        }

        mockMvc.post("/api/v1/recruitments/$recruitmentId/applications") {
            bearer(s2); json("""{"motivation":"x"}""")
        }.andExpect { status { isConflict() } }

        mockMvc.get("/api/v1/applications/me") { bearer(s1) }.andExpect {
            status { isOk() }
            jsonPath("$.recruitmentId") { value(recruitmentId) }
            jsonPath("$.status") { value("APPROVED") }
            jsonPath("$.order") { value(1) }
            jsonPath("$.waitingForAssignment") { value(true) }
        }

        mockMvc.get("/api/v1/recruitments") { bearer(teacher) }
            .andExpect {
                status { isOk() }
                jsonPath("$[0].applicantCount") { value(1) }
                jsonPath("$[0].isFull") { value(true) }
            }

        mockMvc.post("/api/v1/recruitments") {
            bearer(teacher)
            json("""{"semester":"2026-2","grade":1,"classNo":3,"maxCount":7,
                "startDate":"${LocalDateTime.now()}","endDate":"${LocalDateTime.now().plusDays(1)}"}""")
        }.andExpect { status { isBadRequest() }; jsonPath("$.code") { value("INVALID_MAX_COUNT") } }
    }

    @Test
    fun `assignment exposes co-assigned students and appeal only works on own rejected verification`() {
        val teacher = login("TEACHER|9201|t@test.local|Teacher|||")
        val s1 = login("STUDENT|9202|s1@test.local|S1|1101|1|1")
        val s2 = login("STUDENT|9203|s2@test.local|S2|1102|1|1")
        val student1 = userRepository.findByGsmAccountId(9202)!!
        val student2 = userRepository.findByGsmAccountId(9203)!!
        val area = cleaningAreaRepository.save(CleaningArea(zoneCode = "zone_T", name = "Test hall", cleanTime = "08:00~08:10"))

        mockMvc.patch("/api/v1/cleaning-areas/${area.id}") { bearer(teacher); json("""{"isActive":true}""") }
            .andExpect { status { isOk() } }
        mockMvc.post("/api/v1/cleaning-areas/${area.id}/assignments") {
            bearer(teacher); json("""{"studentIds":[${student1.id},${student2.id}]}""")
        }.andExpect { status { isCreated() } }

        mockMvc.get("/api/v1/assignments/me") { bearer(s1) }.andExpect {
            status { isOk() }
            jsonPath("$.areaId") { value(area.id) }
            jsonPath("$.members.length()") { value(2) }
        }

        val processing = verificationRepository.save(
            Verification(student = student1, area = area, photoUrl = "/files/a.jpg", verificationDate = LocalDate.now().minusDays(1)),
        )
        mockMvc.post("/api/v1/verifications/${processing.id}/appeals") { bearer(s1); json("""{"content":"x"}""") }
            .andExpect { status { isConflict() } }

        processing.status = VerificationStatus.REJECTED
        verificationRepository.save(processing)

        mockMvc.post("/api/v1/verifications/${processing.id}/appeals") { bearer(s2); json("""{"content":"x"}""") }
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("APPEAL_NOT_ALLOWED") } }
        mockMvc.post("/api/v1/verifications/999999/appeals") { bearer(s1); json("""{"content":"x"}""") }
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("VERIFICATION_NOT_FOUND") } }

        val appealId = post(s1, "/api/v1/verifications/${processing.id}/appeals", """{"content":"clean"}""")
            .get("appealId").asLong()
        mockMvc.post("/api/v1/verifications/${processing.id}/appeals") { bearer(s1); json("""{"content":"again"}""") }
            .andExpect { status { isConflict() } }

        mockMvc.patch("/api/v1/appeals/$appealId") { bearer(teacher); json("""{"decision":"PENDING"}""") }
            .andExpect { status { isBadRequest() }; jsonPath("$.code") { value("INVALID_DECISION") } }
        mockMvc.patch("/api/v1/appeals/$appealId") { bearer(teacher); json("""{"decision":"MAYBE"}""") }
            .andExpect { status { isBadRequest() }; jsonPath("$.code") { value("INVALID_DECISION") } }
        mockMvc.patch("/api/v1/appeals/$appealId") { bearer(teacher); json("""{"decision":"REJECTED","reply":"photo unclear"}""") }
            .andExpect { status { isOk() } }

        val secondId = post(s1, "/api/v1/verifications/${processing.id}/appeals", """{"content":"retry"}""")
            .get("appealId").asLong()
        mockMvc.patch("/api/v1/appeals/$secondId") { bearer(teacher); json("""{"decision":"APPROVED"}""") }
            .andExpect { status { isOk() } }

        mockMvc.get("/api/v1/appeals/me") { bearer(s1) }.andExpect {
            status { isOk() }
            jsonPath("$[0].round") { value(2) }
            jsonPath("$[0].status") { value("APPROVED") }
            jsonPath("$[1].reply") { value("photo unclear") }
        }
        assertEquals(10L, serviceTimeLogRepository.sumMinutesByStudentId(student1.id))

        mockMvc.get("/api/v1/verifications/${processing.id}/review") { bearer(s2) }
            .andExpect { status { isNotFound() } }
        mockMvc.get("/api/v1/verifications/${processing.id}/review") { bearer(teacher) }
            .andExpect { status { isOk() }; jsonPath("$.status") { value("APPROVED") } }
    }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.bearer(token: String) {
        header("Authorization", "Bearer $token")
    }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun post(token: String, url: String, body: String) =
        objectMapper.readTree(
            mockMvc.post(url) { bearer(token); json(body) }
                .andExpect { status { isCreated() } }
                .andReturn().response.contentAsString,
        )

    private fun login(authCode: String): String {
        val result = mockMvc.post("/api/v1/auth/login") {
            json(objectMapper.writeValueAsString(mapOf("authCode" to authCode)))
        }.andExpect { status { isOk() } }.andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }
}
