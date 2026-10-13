package team.siru.ecoguard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import team.siru.ecoguard.auth.GsmUserInfo
import team.siru.ecoguard.auth.TeacherEmailPromoter
import team.siru.ecoguard.auth.TeacherEmailProperties
import team.siru.ecoguard.user.Role
import tools.jackson.databind.ObjectMapper

class TeacherEmailPromoterUnitTests {

    private fun student(email: String) = GsmUserInfo(1L, email, "팀원", Role.STUDENT, "1101", 1, 1)

    @Test
    fun `listed email becomes a teacher without student fields, ignoring case and spaces`() {
        val promoter = TeacherEmailPromoter(TeacherEmailProperties(listOf(" Member@GSM.hs.kr ")))

        val result = promoter.promote(student("member@gsm.hs.kr"))

        assertEquals(Role.TEACHER, result.role)
        assertNull(result.studentNumber)
        assertNull(result.grade)
        assertNull(result.classNo)
    }

    @Test
    fun `unlisted email and empty list keep the original role`() {
        assertEquals(
            Role.STUDENT,
            TeacherEmailPromoter(TeacherEmailProperties(listOf("a@gsm.hs.kr"))).promote(student("b@gsm.hs.kr")).role,
        )
        assertEquals(
            Role.STUDENT,
            TeacherEmailPromoter(TeacherEmailProperties(listOf("", " "))).promote(student("")).role,
        )
    }
}

@SpringBootTest(properties = ["teacher-emails.emails=member@gsm.hs.kr"])
@AutoConfigureMockMvc
class TeacherEmailLoginTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) {

    private fun login(authCode: String): String = objectMapper.readTree(
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("authCode" to authCode))
        }.andReturn().response.contentAsString,
    ).let { it.get("user").get("role").asString() + " " + it.get("accessToken").asString() }

    @Test
    fun `listed student email logs in as a teacher who can use teacher apis`() {
        val (role, token) = login("STUDENT|9001|member@gsm.hs.kr|팀원|1101|1|1").split(" ")

        assertEquals("TEACHER", role)
        mockMvc.get("/api/v1/appeals") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `other student emails stay students`() {
        val (role, token) = login("STUDENT|9002|other@gsm.hs.kr|학생|1102|1|1").split(" ")

        assertEquals("STUDENT", role)
        mockMvc.get("/api/v1/appeals") { header("Authorization", "Bearer $token") }
            .andExpect { status { isForbidden() } }
    }
}
