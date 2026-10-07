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
            .andExpect { status { isUnauthorized() }; content { string("") } }

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

    @Test
    fun `logout revokes earlier tokens but a fresh login works`() {
        fun loginTokens(): Pair<String, String> {
            val body = objectMapper.readTree(
                mockMvc.post("/api/v1/auth/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(mapOf("authCode" to "STUDENT|9004|s4@test.local|Student|1101|1|1"))
                }.andReturn().response.contentAsString,
            )
            return body.get("accessToken").asString() to body.get("refreshToken").asString()
        }
        val (accessToken, refreshToken) = loginTokens()
        mockMvc.get("/api/v1/notices") { header("Authorization", "Bearer $accessToken") }
            .andExpect { status { isOk() } }

        mockMvc.post("/api/v1/auth/logout") { header("Authorization", "Bearer $accessToken") }
            .andExpect { status { isOk() } }

        mockMvc.get("/api/v1/notices") { header("Authorization", "Bearer $accessToken") }
            .andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))
        }.andExpect { status { isUnauthorized() } }

        Thread.sleep(5)
        val (newAccessToken, _) = loginTokens()
        mockMvc.get("/api/v1/notices") { header("Authorization", "Bearer $newAccessToken") }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `refresh token issues a working token pair and rejects invalid tokens`() {
        val loginBody = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(mapOf("authCode" to "TEACHER|9003|t3@test.local|Teacher|||"))
            }.andReturn().response.contentAsString,
        )
        val accessToken = loginBody.get("accessToken").asString()
        val refreshToken = loginBody.get("refreshToken").asString()

        val refreshed = mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
            jsonPath("$.refreshToken") { exists() }
        }.andReturn()
        val newAccessToken = objectMapper.readTree(refreshed.response.contentAsString).get("accessToken").asString()
        mockMvc.get("/api/v1/notices") { header("Authorization", "Bearer $newAccessToken") }
            .andExpect { status { isOk() } }

        // 액세스 토큰은 리프레시에 쓸 수 없고, 리프레시 토큰은 API 호출에 쓸 수 없다.
        for (bad in listOf(accessToken, "garbage")) {
            mockMvc.post("/api/v1/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(mapOf("refreshToken" to bad))
            }.andExpect { status { isUnauthorized() }; content { string("") } }
        }
        mockMvc.get("/api/v1/notices") { header("Authorization", "Bearer $refreshToken") }
            .andExpect { status { isUnauthorized() } }
    }

    // 앱이 응답 바디 유무로 401 종류를 구분하므로 계약으로 고정한다.
    // 로그인 실패(OAUTH_FAILED)만 에러 바디가 있고, 토큰 만료/무효 401은 바디가 없다.
    @Test
    fun `401 body contract - only login failure has error body`() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"authCode":"not-a-valid-code"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("OAUTH_FAILED") }
            jsonPath("$.message") { exists() }
        }

        val loginBody = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(mapOf("authCode" to "STUDENT|9005|s5@test.local|Student|1101|1|1"))
            }.andReturn().response.contentAsString,
        )
        val accessToken = loginBody.get("accessToken").asString()
        val refreshToken = loginBody.get("refreshToken").asString()

        // 토큰 없음 / 잘못된 토큰
        for (header in listOf(null, "Bearer garbage")) {
            mockMvc.get("/api/v1/notices") { if (header != null) header("Authorization", header) }
                .andExpect { status { isUnauthorized() }; content { string("") } }
        }

        // 로그아웃으로 무효화된 액세스 토큰 / 리프레시 토큰
        mockMvc.post("/api/v1/auth/logout") { header("Authorization", "Bearer $accessToken") }
            .andExpect { status { isOk() } }
        mockMvc.get("/api/v1/notices") { header("Authorization", "Bearer $accessToken") }
            .andExpect { status { isUnauthorized() }; content { string("") } }
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))
        }.andExpect { status { isUnauthorized() }; content { string("") } }
    }

    @Test
    fun `withdraw anonymizes the student, revokes tokens, and re-login creates a new account`() {
        val authCode = "STUDENT|9006|s6@test.local|Student|1102|1|1"
        val loginBody = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(mapOf("authCode" to authCode))
            }.andReturn().response.contentAsString,
        )
        val accessToken = loginBody.get("accessToken").asString()
        val refreshToken = loginBody.get("refreshToken").asString()
        val oldUserId = userRepository.findByGsmAccountId(9006)!!.id

        mockMvc.delete("/api/v1/users/me") { header("Authorization", "Bearer $accessToken") }
            .andExpect { status { isOk() } }

        val withdrawn = userRepository.findById(oldUserId).get()
        assertEquals("탈퇴한 사용자", withdrawn.name)
        assertEquals("withdrawn-$oldUserId@withdrawn.invalid", withdrawn.email)
        assertEquals(Long.MIN_VALUE + oldUserId, withdrawn.gsmAccountId)
        assertEquals(null, withdrawn.studentNumber)
        assertEquals(null, withdrawn.grade)
        assertEquals(null, withdrawn.classNo)

        mockMvc.get("/api/v1/users/me") { header("Authorization", "Bearer $accessToken") }
            .andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))
        }.andExpect { status { isUnauthorized() } }

        // 같은 dataGSM 계정으로 다시 로그인하면 새 사용자로 가입된다.
        val newToken = login(authCode)
        val newUser = userRepository.findByGsmAccountId(9006)!!
        assertTrue(newUser.id != oldUserId)
        assertEquals("Student", newUser.name)
        mockMvc.get("/api/v1/users/me") { header("Authorization", "Bearer $newToken") }
            .andExpect { status { isOk() }; jsonPath("$.userId") { value(newUser.id) } }
    }

    @Test
    fun `withdraw is rejected for teachers and unauthenticated requests`() {
        val teacherToken = login("TEACHER|9007|t7@test.local|Teacher|||")

        mockMvc.delete("/api/v1/users/me") { header("Authorization", "Bearer $teacherToken") }
            .andExpect { status { isForbidden() } }
        mockMvc.delete("/api/v1/users/me").andExpect { status { isUnauthorized() } }
        assertEquals("Teacher", userRepository.findByGsmAccountId(9007)!!.name)
    }

    @Test
    fun `oauth callback redirects to app scheme without auth and passes params through`() {
        mockMvc.get("/api/v1/auth/callback?code=abc123&state=xyz")
            .andExpect {
                status { isFound() }
                header { string("Location", "ecoguard://auth/callback?code=abc123&state=xyz") }
            }

        mockMvc.get("/api/v1/auth/callback") {
            param("error", "access_denied")
            param("error_description", "user denied & cancelled")
            param("state", "s+1")
        }.andExpect {
            status { isFound() }
            header {
                string(
                    "Location",
                    "ecoguard://auth/callback?state=s%2B1&error=access_denied&error_description=user%20denied%20%26%20cancelled",
                )
            }
        }
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
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asString()
    }
}
