package team.siru.ecoguard

import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.delete
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import team.siru.ecoguard.notice.NoticeRepository
import team.siru.ecoguard.user.UserRepository

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiDatabaseIntegrationTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val noticeRepository: NoticeRepository,
) {
    @BeforeEach
    fun cleanDatabase() {
        noticeRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `login issues jwt and persists user then protected api enforces role and persists notice crud`() {
        val teacherToken = login("TEACHER|9001|teacher@test.local|Teacher|||")
        val persistedTeacher = userRepository.findByGsmAccountId(9001)
        assertNotNull(persistedTeacher)
        assertEquals("Teacher", persistedTeacher!!.name)

        val studentToken = login("STUDENT|9002|student@test.local|Student|1101|1|1")
        assertEquals(2, userRepository.count())

        // Missing authentication must not reach protected routes.
        mockMvc.get("/api/v1/notices")
            .andExpect { status { isUnauthorized() } }

        // Students may read notices but cannot create them.
        mockMvc.post("/api/v1/notices") {
            header("Authorization", "Bearer $studentToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Denied","content":"Student cannot publish"}"""
        }.andExpect { status { isForbidden() } }
        assertEquals(0, noticeRepository.count())

        val createResult = mockMvc.post("/api/v1/notices") {
            header("Authorization", "Bearer $teacherToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Cleanup","content":"Meet at 8"}"""
        }.andExpect { status { isCreated() } }
            .andReturn()
        val noticeId = objectMapper.readTree(createResult.response.contentAsString).get("noticeId").asLong()
        assertTrue(noticeRepository.existsById(noticeId))

        mockMvc.get("/api/v1/notices/$noticeId") {
            header("Authorization", "Bearer $teacherToken")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.title") { value("Cleanup") }
                jsonPath("$.content") { value("Meet at 8") }
            }

        mockMvc.patch("/api/v1/notices/$noticeId") {
            header("Authorization", "Bearer $teacherToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Updated cleanup"}"""
        }.andExpect { status { isOk() } }
        assertEquals("Updated cleanup", noticeRepository.findById(noticeId).orElseThrow().title)

        mockMvc.delete("/api/v1/notices/$noticeId") {
            header("Authorization", "Bearer $teacherToken")
        }.andExpect { status { isNoContent() } }
        assertFalse(noticeRepository.existsById(noticeId))
    }

    @Test
    fun `invalid mock oauth code returns client error and does not persist user`() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"authCode":"not-a-valid-code"}"""
        }.andExpect { status { isUnauthorized() } }

        assertEquals(0, userRepository.count())
    }

    private fun login(authCode: String): String {
        val result = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("authCode" to authCode))
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
            jsonPath("$.refreshToken") { exists() }
        }.andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }
}
