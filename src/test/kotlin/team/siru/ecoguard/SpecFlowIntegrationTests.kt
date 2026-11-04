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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
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
    fun `teacher can delete a recruitment only while it has no applicants`() {
        val teacher = login("TEACHER|9201|t@test.local|Teacher|||")
        val student = login("STUDENT|9202|s1@test.local|S1|1101|1|1")
        fun create(classNo: Int) = post(teacher, "/api/v1/recruitments", """
            {"semester":"2026-2","grade":1,"classNo":$classNo,"maxCount":2,
             "startDate":"${LocalDateTime.now().minusDays(1)}","endDate":"${LocalDateTime.now().plusDays(1)}"}
        """).get("recruitmentId").asLong()
        val withApplicant = create(1)
        val empty = create(2)

        mockMvc.post("/api/v1/recruitments/$withApplicant/applications") {
            bearer(student); json("""{"motivation":"x"}""")
        }.andExpect { status { isCreated() } }

        mockMvc.delete("/api/v1/recruitments/$empty") { bearer(student) }
            .andExpect { status { isForbidden() } }

        mockMvc.delete("/api/v1/recruitments/$withApplicant") { bearer(teacher) }
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("RECRUITMENT_HAS_APPLICANTS") } }
        assertEquals(true, recruitmentRepository.existsById(withApplicant))

        mockMvc.delete("/api/v1/recruitments/$empty") { bearer(teacher) }
            .andExpect { status { isNoContent() } }
        assertEquals(false, recruitmentRepository.existsById(empty))

        mockMvc.delete("/api/v1/recruitments/$empty") { bearer(teacher) }
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("RECRUITMENT_NOT_FOUND") } }
    }

    @Test
    fun `simultaneous assign requests for the same student create a single assignment`() {
        val teacher = login("TEACHER|9321|t@test.local|Teacher|||")
        login("STUDENT|9322|s1@test.local|S1|1101|1|1")
        val student = userRepository.findByGsmAccountId(9322)!!
        val area = cleaningAreaRepository.save(CleaningArea(zoneCode = "zone_C", name = "Concurrent hall"))
        mockMvc.patch("/api/v1/cleaning-areas/${area.id}") { bearer(teacher); json("""{"isActive":true}""") }
            .andExpect { status { isOk() } }

        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        val start = java.util.concurrent.CountDownLatch(1)
        val statuses = (1..4).map {
            pool.submit<Int> {
                start.await()
                mockMvc.post("/api/v1/cleaning-areas/${area.id}/assignments") {
                    bearer(teacher); json("""{"studentIds":[${student.id}]}""")
                }.andReturn().response.status
            }
        }
        start.countDown()
        val results = statuses.map { it.get() }
        pool.shutdown()

        assertEquals(1, results.count { it == 201 })
        assertEquals(3, results.count { it == 409 })
        assertEquals(1, assignmentRepository.findByStudentId(student.id).size)
    }

    @Test
    fun `teacher can unassign a student from an area`() {
        val teacher = login("TEACHER|9301|t@test.local|Teacher|||")
        val s1 = login("STUDENT|9302|s1@test.local|S1|1101|1|1")
        val s2 = login("STUDENT|9303|s2@test.local|S2|1102|1|1")
        val student1 = userRepository.findByGsmAccountId(9302)!!
        val student2 = userRepository.findByGsmAccountId(9303)!!
        val area = cleaningAreaRepository.save(CleaningArea(zoneCode = "zone_U", name = "Unassign hall"))
        mockMvc.patch("/api/v1/cleaning-areas/${area.id}") { bearer(teacher); json("""{"isActive":true}""") }
            .andExpect { status { isOk() } }
        mockMvc.post("/api/v1/cleaning-areas/${area.id}/assignments") {
            bearer(teacher); json("""{"studentIds":[${student1.id},${student2.id}]}""")
        }.andExpect { status { isCreated() } }

        mockMvc.delete("/api/v1/cleaning-areas/${area.id}/assignments/${student1.id}") { bearer(s1) }
            .andExpect { status { isForbidden() } }
        mockMvc.delete("/api/v1/cleaning-areas/${area.id}/assignments/${student1.id}") { bearer(teacher) }
            .andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/assignments/me") { bearer(s1) }
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("NO_ASSIGNMENT") } }
        mockMvc.get("/api/v1/assignments/me") { bearer(s2) }
            .andExpect { status { isOk() }; jsonPath("$.members.length()") { value(1) } }
        mockMvc.delete("/api/v1/cleaning-areas/${area.id}/assignments/${student1.id}") { bearer(teacher) }
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("ASSIGNMENT_NOT_FOUND") } }
    }

    @Test
    fun `unassigning removes duplicated assignment rows of the same area at once`() {
        val teacher = login("TEACHER|9311|t@test.local|Teacher|||")
        login("STUDENT|9312|s1@test.local|S1|1101|1|1")
        val student = userRepository.findByGsmAccountId(9312)!!
        val area = cleaningAreaRepository.save(CleaningArea(zoneCode = "zone_D", name = "Duplicate hall"))
        assignmentRepository.save(Assignment(area = area, student = student))
        assignmentRepository.save(Assignment(area = area, student = student))

        mockMvc.delete("/api/v1/cleaning-areas/${area.id}/assignments/${student.id}") { bearer(teacher) }
            .andExpect { status { isNoContent() } }

        assert(assignmentRepository.findByStudentId(student.id).isEmpty())
    }

    @Test
    fun `teacher reject reason is stored and shown to the student`() {
        val teacher = login("TEACHER|9401|t@test.local|Teacher|||")
        val s1 = login("STUDENT|9402|s1@test.local|S1|1101|1|1")
        val student1 = userRepository.findByGsmAccountId(9402)!!
        val area = cleaningAreaRepository.save(CleaningArea(zoneCode = "zone_R", name = "Reject hall"))
        fun manual(day: Long) = verificationRepository.save(
            Verification(student = student1, area = area, photoUrl = "/files/r$day.jpg", verificationDate = LocalDate.now().minusDays(day))
                .apply { status = VerificationStatus.MANUAL_REVIEW },
        )

        val withReason = manual(1)
        mockMvc.patch("/api/v1/verifications/${withReason.id}/review") {
            bearer(teacher); json("""{"decision":"REJECTED","reason":"  청소 전 사진 같아요  "}""")
        }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/verifications/${withReason.id}/review") { bearer(s1) }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("REJECTED") }
            jsonPath("$.failReasons[0]") { value("청소 전 사진 같아요") }
            jsonPath("$.failReasons.length()") { value(1) }
        }

        val tooLong = manual(2)
        mockMvc.patch("/api/v1/verifications/${tooLong.id}/review") {
            bearer(teacher); json("""{"decision":"REJECTED","reason":"${"가".repeat(201)}"}""")
        }.andExpect { status { isBadRequest() }; jsonPath("$.code") { value("VALIDATION_ERROR") } }

        val noReason = manual(3)
        mockMvc.patch("/api/v1/verifications/${noReason.id}/review") { bearer(teacher); json("""{"decision":"REJECTED"}""") }
            .andExpect { status { isOk() } }
        mockMvc.get("/api/v1/verifications/${noReason.id}/review") { bearer(s1) }
            .andExpect { status { isOk() }; jsonPath("$.status") { value("REJECTED") }; jsonPath("$.failReasons.length()") { value(0) } }
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
            jsonPath("$.zoneCode") { value(area.zoneCode) }
            jsonPath("$.members.length()") { value(2) }
        }

        // 한 학생은 한 구역만: 다른 구역에 배정 시도하면 거절된다
        val other = cleaningAreaRepository.save(CleaningArea(zoneCode = "zone_T2", name = "Other hall", cleanTime = "08:00~08:10"))
        mockMvc.patch("/api/v1/cleaning-areas/${other.id}") { bearer(teacher); json("""{"isActive":true}""") }
            .andExpect { status { isOk() } }
        mockMvc.post("/api/v1/cleaning-areas/${other.id}/assignments") {
            bearer(teacher); json("""{"studentIds":[${student1.id}]}""")
        }.andExpect { status { isConflict() }; jsonPath("$.code") { value("ALREADY_ASSIGNED") } }
        // 같은 구역에 다시 배정하는 것도 거절되고 배정 행이 늘어나지 않는다
        mockMvc.post("/api/v1/cleaning-areas/${area.id}/assignments") {
            bearer(teacher); json("""{"studentIds":[${student1.id}]}""")
        }.andExpect { status { isConflict() }; jsonPath("$.code") { value("ALREADY_ASSIGNED") } }
        assertEquals(1, assignmentRepository.findByStudentId(student1.id).size)

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
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asString()
    }
}
